/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.runner.drunner.server_adapter;

import com.openjiuwen.core.runner.drunner.DistributedRunner;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage;
import com.openjiuwen.core.runner.mq.MessageQueueBase;
import com.openjiuwen.core.runner.mq.SubscriptionBase;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * MQ-based server adapter for distributed-runner requests.
 */
public class MqServerAdapter {

    private final String adapterId;
    private final String topic;
    private final Function<Map<String, Object>, Object> invokeHandler;
    private final Function<Map<String, Object>, Iterator<Object>> streamHandler;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();

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
            return null;
        });
        this.subscription.activate();
        this.active = true;
    }

    public void stop() {
        active = false;
        if (subscription != null) {
            subscription.deactivate();
            mq.unsubscribe(topic);
            subscription = null;
        }
        runningTasks.values().forEach(task -> task.cancel(true));
        runningTasks.clear();
        executor.shutdownNow();
    }

    private void handleMessage(DmqRequestMessage message) {
        if (message.getExpireAt() != null && message.getExpireAt() < (System.currentTimeMillis() / 1000.0)) {
            return;
        }
        if (message.getType() == DMessageType.STOP) {
            Future<?> future = runningTasks.remove(message.getMessageId());
            if (future != null) {
                future.cancel(true);
            }
            return;
        }
        Future<?> future = executor.submit(() -> processMessage(message));
        runningTasks.put(message.getMessageId(), future);
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
        } catch (Exception e) {
            mq.produceMessage(message.getReplyTopic(), MqMessageUtils.buildErrorResponse(message, adapterId, e));
        } finally {
            runningTasks.remove(message.getMessageId());
        }
    }
}
