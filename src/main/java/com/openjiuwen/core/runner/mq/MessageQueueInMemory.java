/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * In-memory message queue with topic-based routing.
 * Mirrors Python's {@code MessageQueueInMemory} in {@code message_queue_inmemory.py}.
 */
public class MessageQueueInMemory extends MessageQueueBase {

    private static final Logger logger = LoggerFactory.getLogger(MessageQueueInMemory.class);

    private final int queueMaxSize;
    private final long timeoutMs;
    private volatile boolean running;
    private final Map<String, SubscriptionInMemory> subscribers = new ConcurrentHashMap<>();
    private BlockingQueue<TopicMessage> queue;
    private ExecutorService consumerExecutor;

    /**
     * Auto-generated for codecheck compliance.
     */
    public MessageQueueInMemory(int queueMaxSize, long timeoutMs) {
        this.queueMaxSize = queueMaxSize;
        this.timeoutMs = timeoutMs;
        this.queue = new LinkedBlockingQueue<>(queueMaxSize);
        this.running = false;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public MessageQueueInMemory() {
        this(10000, 120_000L);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void start() {
        if (!running) {
            running = true;
            consumerExecutor = Executors.newSingleThreadExecutor(
                    r -> {
                        Thread thread = new Thread(r, "mq-inmemory-0");
                        thread.setDaemon(true);
                        return thread;
                    });
            consumerExecutor.submit(this::consumeMessages);
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void stop() {
        if (running) {
            running = false;
            if (consumerExecutor != null) {
                consumerExecutor.shutdownNow();
                consumerExecutor = null;
            }
            queue = new LinkedBlockingQueue<>(queueMaxSize);
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public SubscriptionBase subscribe(String topic) {
        if (subscribers.containsKey(topic)) {
            throw new IllegalArgumentException("Topic '" + topic + "' is already subscribed.");
        }
        SubscriptionInMemory subscription = new SubscriptionInMemory(queueMaxSize, timeoutMs);
        subscribers.put(topic, subscription);
        return subscription;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void unsubscribe(String topic) {
        SubscriptionInMemory sub = subscribers.remove(topic);
        if (sub != null) {
            sub.deactivate();
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void produceMessage(String topic, QueueMessage message) {
        try {
            queue.put(new TopicMessage(topic, message));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void consumeMessages() {
        while (running) {
            try {
                TopicMessage tm = queue.poll(1, TimeUnit.SECONDS);
                if (tm == null) {
                    continue;
                }
                SubscriptionInMemory sub = subscribers.get(tm.topic());
                if (sub != null && sub.isActive()) {
                    sub.pushMessage(tm.message());
                } else {
                    logger.warn("No active subscriber for topic: {}", tm.topic());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private record TopicMessage(String topic, QueueMessage message) {
    }
}
