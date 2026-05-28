/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.server_adapter;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.drunner.DistributedRunner;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage;
import com.openjiuwen.core.runner.mq.MessageQueueBase;
import com.openjiuwen.core.runner.mq.SubscriptionBase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * MQ-based server adapter for distributed-runner requests.
 * Mirrors Python's MqServerAdapter in mq_server_adapter.py.
 */
public class MqServerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(MqServerAdapter.class);

    private final String adapterId;
    private final String topic;
    private Function<Map<String, Object>, Object> invokeHandler;
    private Function<Map<String, Object>, Iterator<Object>> streamHandler;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, MessageTask> runningTasks = new ConcurrentHashMap<>();

    private MessageQueueBase mq;
    private SubscriptionBase subscription;
    private boolean active;

    public MqServerAdapter(String adapterId,
                           String topic,
                           Function<Map<String, Object>, Object> invokeHandler,
                           Function<Map<String, Object>, Iterator<Object>> streamHandler) {
        this.adapterId = adapterId;
        this.topic = topic;
        this.invokeHandler = invokeHandler;
        this.streamHandler = streamHandler;
    }

    public void setInvokeHandler(Function<Map<String, Object>, Object> handler) {
        this.invokeHandler = handler;
    }

    public void setStreamHandler(Function<Map<String, Object>, Iterator<Object>> handler) {
        this.streamHandler = handler;
    }

    public void start() {
        if (active) {
            return;
        }
        DistributedRunner.ensureStarted();
        this.mq = DistributedRunner.messageQueue();
        this.subscription = mq.subscribe(topic);
        this.subscription.setMessageHandler(message -> {
            if (message instanceof DmqRequestMessage request) {
                handleMessage(request);
            }
            return CompletableFuture.completedFuture(null);
        });
        this.subscription.activate();
        this.active = true;
        logger.info("[{}] Adapter started on {}", adapterId, topic);
    }

    public void stop() {
        logger.info("[{}] Stopping adapter...", adapterId);
        if (!active) {
            return;
        }
        active = false;

        if (subscription != null) {
            subscription.deactivate();
            mq.unsubscribe(topic);
            subscription = null;
        }

        // Cancel all running tasks with inner cancel writeback
        for (Map.Entry<String, MessageTask> entry : runningTasks.entrySet()) {
            cancelTask(entry.getKey(), true);
        }
        runningTasks.clear();
        executor.shutdownNow();
        scheduler.shutdownNow();
        logger.info("[{}] Adapter stopped", adapterId);
    }

    private void handleMessage(DmqRequestMessage message) {
        String msgId = message.getMessageId();
        logger.info("[{}] Received message {}, type={}", adapterId, msgId, message.getType());

        // Discard expired messages
        if (message.getExpireAt() != null && message.getExpireAt() < (System.currentTimeMillis() / 1000.0)) {
            logger.warn("[{}] Ignoring expired message {}", adapterId, msgId);
            return;
        }

        // Passive cancellation via STOP message
        if (message.getType() == DMessageType.STOP) {
            cancelTask(msgId, false);
            return;
        }

        // Duplicate message - cancel old, replace
        if (runningTasks.containsKey(msgId)) {
            logger.warn("[{}] Duplicate msg_id {}, replacing old task", adapterId, msgId);
            cancelTask(msgId, true);
        }

        // Start execution task
        Future<?> future = executor.submit(() -> processMessage(message));
        runningTasks.put(msgId, new MessageTask(message, future));

        // Schedule timeout cancellation
        if (message.getExpireAt() != null) {
            double delay = message.getExpireAt() - (System.currentTimeMillis() / 1000.0);
            if (delay > 0) {
                scheduler.schedule(() -> timeoutCancel(msgId),
                        (long) (delay * 1000), TimeUnit.MILLISECONDS);
            }
        }
        logger.info("[{}] Submitted task message_id={}", adapterId, msgId);
    }

    @SuppressWarnings("unchecked")
    private void processMessage(DmqRequestMessage message) {
        try {
            Map<String, Object> payload = message.getBody() instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : java.util.Map.of();
            if (message.isEnableStream()) {
                int seq = 0;
                Iterator<Object> iterator = streamHandler.apply(payload);
                while (iterator.hasNext()) {
                    mq.produceMessage(message.getReplyTopic(),
                            MqMessageUtils.buildStreamResponse(message, adapterId, iterator.next(), seq++, false));
                }
                mq.produceMessage(message.getReplyTopic(),
                        MqMessageUtils.buildFinalResponse(message, adapterId, seq));
            } else {
                Object result = invokeHandler.apply(payload);
                mq.produceMessage(message.getReplyTopic(),
                        MqMessageUtils.buildBatchResponse(message, adapterId, result));
            }
        } catch (CancellationException e) {
            logger.info("[{}] Task {} cancelled", adapterId, message.getMessageId());
        } catch (Exception e) {
            logger.error("[{}] Task {} error: {}", adapterId, message.getMessageId(), e.getMessage(), e);
            try {
                mq.produceMessage(message.getReplyTopic(),
                        MqMessageUtils.buildErrorResponse(message, adapterId, e));
            } catch (Exception ex) {
                logger.error("[{}] Failed to send error response for {}: {}",
                        adapterId, message.getMessageId(), ex.getMessage());
            }
        } finally {
            runningTasks.remove(message.getMessageId());
        }
    }

    private void timeoutCancel(String msgId) {
        MessageTask msgTask = runningTasks.get(msgId);
        if (msgTask == null) {
            return;
        }
        if (!msgTask.getTask().isDone()) {
            logger.warn("[{}] Task {} expired and will be cancelled", adapterId, msgId);
            msgTask.getTask().cancel(true);
        }
    }

    /**
     * Cancel a running task. If innerCancel is true, sends an error response
     * back to the client indicating the task was cancelled by the adapter.
     */
    private void cancelTask(String msgId, boolean innerCancel) {
        MessageTask msgTask = runningTasks.remove(msgId);
        if (msgTask == null) {
            logger.info("[{}] No task found for msg_id {} during cancellation", adapterId, msgId);
            return;
        }
        logger.info("[{}] Cancelling task {}", adapterId, msgId);
        msgTask.getTask().cancel(true);

        if (innerCancel) {
            logger.info("[{}] Sending cancellation error response for task {}", adapterId, msgId);
            try {
                Exception err = ErrorHelper.buildError(
                        StatusCode.MESSAGE_QUEUE_MESSAGE_PROCESS_EXECUTION_ERROR,
                        "reason", "Task cancelled by adapter stop (" + adapterId + ")");
                mq.produceMessage(msgTask.getMessage().getReplyTopic(),
                        MqMessageUtils.buildErrorResponse(msgTask.getMessage(), adapterId, err));
                logger.info("[{}] Sent cancellation error response for task {}", adapterId, msgId);
            } catch (Exception e) {
                logger.warn("[{}] Failed to send cancel error for task {}: {}",
                        adapterId, msgId, e.getMessage());
            }
        }
    }
}
