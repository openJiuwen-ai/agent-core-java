/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.runner.resourcemanager.ThreadSafeDict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory message queue with topic-based routing.
 *
 * <p>Mirrors Python's {@code MessageQueueInMemory} in
 * {@code openjiuwen/core/runner/message_queue_inmemory.py}.</p>
 */
public class MessageQueueInMemory extends MessageQueueBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageQueueInMemory.class);
    private static final int DEFAULT_QUEUE_MAX_SIZE = 10_000;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120_000);

    private final int queueMaxSize;
    private final Duration timeout;
    private final ThreadSafeDict<String, SubscriptionInMemory> subscribers = new ThreadSafeDict<>();

    private volatile boolean running;
    private BlockingQueue<TopicMessage> queue;
    private ExecutorService consumerExecutor;

    public MessageQueueInMemory() {
        this(DEFAULT_QUEUE_MAX_SIZE, DEFAULT_TIMEOUT);
    }

    public MessageQueueInMemory(int queueMaxSize, double timeoutSeconds) {
        this(queueMaxSize, durationFromSeconds(timeoutSeconds));
    }

    public MessageQueueInMemory(int queueMaxSize, Duration timeout) {
        this.queueMaxSize = queueMaxSize;
        this.timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        this.queue = newQueue(queueMaxSize);
    }

    @Override
    public void start() {
        if (!running) {
            running = true;
            consumerExecutor = OpenJiuwenExecutors.newSingleThreadExecutor("mq-inmemory", true);
            consumerExecutor.submit(this::consumeMessages);
        }
    }

    @Override
    public void stop() {
        if (running) {
            running = false;
            if (consumerExecutor != null) {
                consumerExecutor.shutdownNow();
                consumerExecutor = null;
            }
            queue = newQueue(queueMaxSize);
        }
    }

    @Override
    public SubscriptionInMemory subscribe(String topic) {
        if (subscribers.containsKey(topic)) {
            throw new IllegalArgumentException("Topic '" + topic + "' is already subscribed.");
        }
        SubscriptionInMemory subscription = new SubscriptionInMemory(queueMaxSize, timeout);
        subscribers.put(topic, subscription);
        return subscription;
    }

    @Override
    public void unsubscribe(String topic) {
        SubscriptionInMemory subscription = subscribers.pop(topic, null);
        if (subscription != null) {
            subscription.deactivate();
        }
    }

    @Override
    public void produceMessage(String topic, QueueMessage message) {
        try {
            queue.put(new TopicMessage(topic, message));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isRunning() {
        return running;
    }

    private void consumeMessages() {
        while (running) {
            try {
                TopicMessage topicMessage = queue.take();
                SubscriptionInMemory subscription = subscribers.get(topicMessage.topic());
                if (subscription != null && subscription.isActive()) {
                    subscription.pushMessage(topicMessage.message());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException e) {
                LOGGER.warn("Failed to route in-memory message", e);
            }
        }
    }

    private static BlockingQueue<TopicMessage> newQueue(int queueMaxSize) {
        if (queueMaxSize <= 0) {
            return new LinkedBlockingQueue<>();
        }
        return new LinkedBlockingQueue<>(queueMaxSize);
    }

    private static Duration durationFromSeconds(double timeoutSeconds) {
        long timeoutMillis = Math.max(0L, Math.round(timeoutSeconds * 1000.0d));
        return Duration.ofMillis(timeoutMillis);
    }

    private record TopicMessage(String topic, QueueMessage message) {
    }
}
