// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.dmessagequeue;

import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.MessageQueueBase;
import com.openjiuwen.core.runner.SubscriptionBase;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * In-memory message queue for testing.
 * 
 * 对应Python: drunner/dmessage_queue/message_queue_fake.py - FakeMQ
 */
public class FakeMQ extends MessageQueueBase {

    private static final Logger LOG = Logger.getLogger(FakeMQ.class.getName());

    private final Map<String, List<FakeSubscription>> topics = new ConcurrentHashMap<>();
    private volatile boolean isRunning = false;

    @Override
    public void start() {
        isRunning = true;
        LOG.info("[FakeMQ] started");
    }

    @Override
    public CompletableFuture<Void> stop() {
        isRunning = false;

        List<String> topicList = new ArrayList<>(topics.keySet());
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String t : topicList) {
            futures.add(unsubscribe(t));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> LOG.info("[FakeMQ] stopped"));
    }

    @Override
    public FakeSubscription subscribe(String topic) {
        if (!isRunning) {
            throw ErrorBuilder.build(StatusCode.MESSAGE_QUEUE_NOT_RUNNING, "FakeMQ");
        }
        FakeSubscription sub = new FakeSubscription(topic);
        topics.computeIfAbsent(topic, k -> Collections.synchronizedList(new ArrayList<>())).add(sub);
        LOG.info("[FakeMQ] new subscription for topic=" + topic);
        return sub;
    }

    @Override
    public CompletableFuture<Void> unsubscribe(String topic) {
        List<FakeSubscription> subs = topics.remove(topic);
        if (subs != null) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (FakeSubscription sub : subs) {
                futures.add(sub.deactivate());
            }
            LOG.info("[FakeMQ] unsubscribed topic=" + topic);
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> produceMessage(String topic, Object message) {
        byte[] data = MessageSerializer.serializeMessage(message);

        List<FakeSubscription> subs = topics.get(topic);
        if (subs != null) {
            List<FakeSubscription> snapshot;
            synchronized (subs) {
                snapshot = new ArrayList<>(subs);
            }
            for (FakeSubscription sub : snapshot) {
                sub.push(data);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Check if the MQ is running (for testing).
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Get topics map (for testing).
     */
    public Map<String, List<FakeSubscription>> getTopics() {
        return topics;
    }
}

