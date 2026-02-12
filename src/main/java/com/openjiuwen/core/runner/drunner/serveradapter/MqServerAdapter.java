// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.serveradapter;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.MessageQueueBase;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.SubscriptionBase;
import com.openjiuwen.core.runner.drunner.dmessagequeue.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessagequeue.DmqRequestMessage;
import com.openjiuwen.core.runner.drunner.dmessagequeue.DmqResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * MQ服务端适配器
 * 
 * <p>负责订阅指定topic，处理接收到的请求消息（invoke/stream），管理运行中的任务。
 * 
 * 对应Python: drunner/server_adapter/mq_server_adapter.py - MqServerAdapter
 */
public class MqServerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(MqServerAdapter.class);

    private final String adapterId;
    private final String topic;
    private final Function<Map<String, Object>, Object> invokeHandler;
    private final Function<Map<String, Object>, Iterator<Object>> streamHandler;

    // 通过 package-visible 字段供测试设置
    MessageQueueBase mq;
    SubscriptionBase subscription;
    volatile boolean active = false;
    final ConcurrentHashMap<String, MessageTask> runningTasks = new ConcurrentHashMap<>();
    private final ExecutorService taskExecutor;
    private final ScheduledExecutorService scheduler;

    public MqServerAdapter(
            String adapterId,
            String topic,
            Function<Map<String, Object>, Object> invokeHandler,
            Function<Map<String, Object>, Iterator<Object>> streamHandler) {
        this.adapterId = adapterId;
        this.topic = topic;
        this.invokeHandler = invokeHandler;
        this.streamHandler = streamHandler;
        this.mq = Runner.getDistPubsub();
        this.taskExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "MqServerAdapter-" + adapterId);
            t.setDaemon(true);
            return t;
        });
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MqServerAdapter-scheduler-" + adapterId);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动适配器：订阅topic，设置消息处理器
     */
    public void start() {
        if (!active) {
            subscription = mq.subscribe(topic);
            subscription.setMessageHandler(msg -> {
                if (msg instanceof DmqRequestMessage) {
                    handleMessage((DmqRequestMessage) msg);
                }
                return CompletableFuture.completedFuture(null);
            });
            subscription.activate();
            active = true;
            logger.info("[{}] Adapter started on {}", adapterId, topic);
        }
    }

    /**
     * 消息分发处理
     */
    void handleMessage(DmqRequestMessage message) {
        String msgId = message.getMessageId();
        logger.info("[{}] Received message {}, message_type={}", adapterId, msgId, message.getType());

        // Discard expired messages
        if (message.getExpireAt() != null && message.getExpireAt() < System.currentTimeMillis() / 1000.0) {
            logger.warn("[{}] Ignoring expired message {}, expire_at: {}, current_time: {}",
                    adapterId, msgId, message.getExpireAt(), System.currentTimeMillis() / 1000.0);
            return;
        }

        // STOP message -> cancel task
        if (DMessageType.STOP.getValue().equals(message.getType())) {
            cancelTask(msgId, false);
            return;
        }

        // Duplicate message
        if (runningTasks.containsKey(msgId)) {
            logger.warn("[{}] Duplicate msg_id {}, replacing old task", adapterId, msgId);
            cancelTask(msgId, true);
        }

        // Start execution task
        Future<?> future = taskExecutor.submit(() -> processMessage(message));
        runningTasks.put(msgId, new MessageTask(message, future));

        // Scheduled timeout cancellation
        if (message.getExpireAt() != null) {
            double delay = message.getExpireAt() - System.currentTimeMillis() / 1000.0;
            if (delay > 0) {
                scheduler.schedule(
                        () -> timeoutCancel(msgId),
                        (long) (delay * 1000),
                        TimeUnit.MILLISECONDS
                );
            }
        }
        logger.info("[{}] Submitted task message_id={}", adapterId, msgId);
    }

    /**
     * 处理消息：执行invoke或stream handler
     */
    @SuppressWarnings("unchecked")
    void processMessage(DmqRequestMessage message) {
        try {
            if (message.isEnableStream()) {
                int seq = 0;
                Iterator<Object> iterator = streamHandler.apply(
                        (Map<String, Object>) message.getPayload());
                while (iterator.hasNext()) {
                    Object chunk = iterator.next();
                    DmqResponseMessage resp = MqMessageUtils.buildStreamResponse(
                            message, adapterId, chunk, seq, false);
                    mq.produceMessage(message.getReplyTopic(), resp);
                    seq++;
                }

                DmqResponseMessage finalResp = MqMessageUtils.buildFinalResponse(
                        message, adapterId, seq);
                mq.produceMessage(message.getReplyTopic(), finalResp);
            } else {
                Object result = invokeHandler.apply(
                        (Map<String, Object>) message.getPayload());
                DmqResponseMessage resp = MqMessageUtils.buildBatchResponse(
                        message, adapterId, result);
                mq.produceMessage(message.getReplyTopic(), resp);
            }
        } catch (CancellationException e) {
            logger.info("[{}] Task {} cancelled", adapterId, message.getMessageId());
            throw e;
        } catch (JiuWenBaseException e) {
            if (e.getErrorCode() == StatusCode.RUNNER_STOPPED.getCode()
                    || e.getErrorCode() == StatusCode.MESSAGE_QUEUE_NOT_RUNNING.getCode()) {
                logger.info("[{}] Task {} cancelled", adapterId, message.getMessageId());
                throw e;
            }
            logger.warn("[{}] adapter run error msg: {}: {}", adapterId, message.getMessageId(), e.getMessage());
            DmqResponseMessage resp = MqMessageUtils.buildErrorResponse(message, adapterId, e);
            mq.produceMessage(message.getReplyTopic(), resp);
        } catch (Exception e) {
            logger.error("[{}] Unexpected error: {}", adapterId, e.getMessage(), e);
            JiuWenBaseException err = new JiuWenBaseException(StatusCode.ERROR.getCode(), e.getMessage());
            DmqResponseMessage resp = MqMessageUtils.buildErrorResponse(message, adapterId, err);
            mq.produceMessage(message.getReplyTopic(), resp);
        } finally {
            runningTasks.remove(message.getMessageId());
        }
    }

    /**
     * 超时取消任务
     */
    void timeoutCancel(String msgId) {
        MessageTask msgTask = runningTasks.get(msgId);
        if (msgTask == null) {
            return;
        }
        Future<?> task = msgTask.getTask();
        if (!task.isDone()) {
            logger.warn("[{}] Task {} expired and will be cancelled", adapterId, msgId);
            task.cancel(true);
        }
    }

    /**
     * 取消任务
     *
     * @param msgId       消息ID
     * @param innerCancel 是否由内部主动取消（需发送错误响应给客户端）
     */
    void cancelTask(String msgId, boolean innerCancel) {
        MessageTask msgTask = runningTasks.get(msgId);
        if (msgTask == null) {
            logger.info("[{}] No task found for msg_id {} during cancellation", adapterId, msgId);
            return;
        }
        DmqRequestMessage message = msgTask.getMessage();
        Future<?> task = msgTask.getTask();

        logger.info("[{}] Cancelling task {}", adapterId, msgId);
        task.cancel(true);

        runningTasks.remove(msgId);
        logger.info("[{}] Removed task {} from running tasks", adapterId, msgId);

        if (innerCancel) {
            logger.info("[{}] Sending cancellation error response for task {}", adapterId, msgId);
            try {
                JiuWenBaseException err = new JiuWenBaseException(
                        StatusCode.RUNNER_STOPPED.getCode(),
                        "Task cancelled by adapter stop (" + adapterId + ")"
                );
                DmqResponseMessage resp = MqMessageUtils.buildErrorResponse(message, adapterId, err);
                mq.produceMessage(message.getReplyTopic(), resp);
                logger.info("[{}] Sent cancellation error response for task {}", adapterId, msgId);
            } catch (Exception e) {
                logger.warn("[{}] Failed to send cancel error for task {}: {}", adapterId, msgId, e.getMessage());
            }
        }
    }

    /**
     * 停止适配器
     */
    public void stop() {
        logger.info("[{}] Stopping adapter...", adapterId);
        if (!active) {
            return;
        }
        active = false;

        if (subscription != null) {
            subscription.deactivate();
            subscription = null;
        }

        logger.info("[{}] Cancelling all running tasks...", adapterId);
        List<String> taskIds = new ArrayList<>(runningTasks.keySet());
        for (String taskId : taskIds) {
            cancelTask(taskId, true);
        }
        logger.info("[{}] Adapter stopped", adapterId);
    }

    // Getters for test access

    public String getAdapterId() {
        return adapterId;
    }

    public String getTopic() {
        return topic;
    }

    public boolean isActive() {
        return active;
    }

    public Map<String, MessageTask> getRunningTasks() {
        return runningTasks;
    }
}

