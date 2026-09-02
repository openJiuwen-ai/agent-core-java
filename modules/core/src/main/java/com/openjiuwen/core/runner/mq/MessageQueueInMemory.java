/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * In-memory message queue with topic-based routing.
 * Mirrors Python's {@code MessageQueueInMemory} in {@code message_queue_inmemory.py}.
 * 
 * @since 0.1.7
 */
public class MessageQueueInMemory extends MessageQueueBase {
    private static final Logger logger = LoggerFactory.getLogger(MessageQueueInMemory.class);

    private final int queueMaxSize;
    private final long timeoutMs;
    private volatile boolean running;

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, SubscriptionInMemory> subscribers = new ConcurrentHashMap<>();
    private BlockingQueue<TopicMessage> queue;
    private ExecutorService consumerExecutor;

    /**
     * MessageQueueInMemory.
     * 
     * @param queueMaxSize queueMaxSize
     * @param timeoutMs timeoutMs
     * @since 0.1.7
     */
    public MessageQueueInMemory(int queueMaxSize, long timeoutMs) {
        this.queueMaxSize = queueMaxSize;
        this.timeoutMs = timeoutMs;
        this.queue = new LinkedBlockingQueue<>(queueMaxSize);
        this.running = false;
    }

    /**
     * MessageQueueInMemory.
     * 
     * @since 0.1.7
     */
    public MessageQueueInMemory() {
        this(10000, 120_000L);
    }

    /**
     * start.
     * 
     * @since 0.1.7
     */
    @Override
    public void start() {
        if (!running) {
            running = true;
            consumerExecutor = OpenJiuwenExecutors.newSingleThreadExecutor("mq-inmemory", true);
            consumerExecutor.submit(this::consumeMessages);
        }
    }

    /**
     * stop.
     * 
     * @since 0.1.7
     */
    @Override
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

    /**
     * subscribe.
     * 
     * @param topic topic
     * @return the result
     * @since 0.1.7
     */
    @Override
    public SubscriptionBase subscribe(String topic) {
        if (subscribers.containsKey(topic)) {
            throw new IllegalArgumentException("Topic '" + topic + "' is already subscribed.");
        }
        SubscriptionInMemory subscription = new SubscriptionInMemory(queueMaxSize, timeoutMs);
        subscribers.put(topic, subscription);
        return subscription;
    }

    /**
     * unsubscribe.
     * 
     * @param topic topic
     * @since 0.1.7
     */
    @Override
    public void unsubscribe(String topic) {
        SubscriptionInMemory sub = subscribers.remove(topic);
        if (sub != null) {
            sub.deactivate();
        }
    }

    /**
     * produceMessage.
     * 
     * @param topic topic
     * @param message message
     * @since 0.1.7
     */
    @Override
    public void produceMessage(String topic, QueueMessage message) {
        try {
            queue.put(new TopicMessage(topic, message));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * consumeMessages.
     * 
     * @since 0.1.7
     */
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

    /**
     * TopicMessage.
     * 
     * @param topic topic
     * @param message message
     * @since 0.1.7
     */
    private record TopicMessage(String topic, QueueMessage message) {
    }
}
