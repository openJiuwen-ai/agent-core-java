/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqMessage;
import com.openjiuwen.core.runner.mq.MessageQueueBase;
import com.openjiuwen.core.runner.mq.QueueMessage;
import com.openjiuwen.core.runner.mq.SubscriptionBase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory message queue for distributed-runner tests and local execution.
 *
 * <p>Mirrors Python's {@code FakeMQ} in
 * {@code openjiuwen/core/runner/drunner/dmessage_queue/message_queue_fake.py}.</p>
 */
public class FakeMessageQueue extends MessageQueueBase {

    private static final LoggerProtocol LOGGER = Loggers.RUNNER;

    private final Map<String, List<FakeSubscription>> topics = new ConcurrentHashMap<>();
    private volatile boolean running;

    @Override
    public void start() {
        running = true;
        LOGGER.info("[FakeMQ] started");
    }

    @Override
    public void stop() {
        running = false;
        for (String topic : new ArrayList<>(topics.keySet())) {
            unsubscribe(topic);
        }
        LOGGER.info("[FakeMQ] stopped");
    }

    @Override
    public SubscriptionBase subscribe(String topic) {
        if (!running) {
            throw buildMqError(StatusCode.MESSAGE_QUEUE_TOPIC_SUBSCRIPTION_ERROR,
                    "message queue is not running");
        }
        FakeSubscription subscription = new FakeSubscription(topic);
        topics.computeIfAbsent(topic, ignored -> new CopyOnWriteArrayList<>()).add(subscription);
        LOGGER.info("[FakeMQ] new subscription for topic={}", topic);
        return subscription;
    }

    @Override
    public void unsubscribe(String topic) {
        List<FakeSubscription> subscriptions = topics.remove(topic);
        if (subscriptions == null) {
            return;
        }
        for (FakeSubscription subscription : subscriptions) {
            subscription.deactivate();
        }
        LOGGER.info("[FakeMQ] unsubscribed topic={}", topic);
    }

    @Override
    public void produceMessage(String topic, QueueMessage message) {
        if (!(message instanceof DmqMessage dmqMessage)) {
            throw buildMqError(StatusCode.MESSAGE_QUEUE_TOPIC_MESSAGE_PRODUCTION_ERROR,
                    "message must be a distributed message");
        }
        byte[] data = MessageSerializer.serializeMessage(dmqMessage);
        List<FakeSubscription> subscriptions = List.copyOf(topics.getOrDefault(topic, List.of()));
        for (FakeSubscription subscription : subscriptions) {
            CompletableFuture.runAsync(() -> subscription.push(data).join());
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int subscriptionCount(String topic) {
        return topics.getOrDefault(topic, List.of()).size();
    }

    private static RuntimeException buildMqError(StatusCode status, String reason) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("reason", reason);
        return ErrorHelper.buildError(status, null, null, null, params);
    }
}
