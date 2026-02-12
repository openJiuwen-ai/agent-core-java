// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.openjiuwen.core.runner.resourcesmanager.ThreadSafeDict;

import java.util.concurrent.*;

/**
 * In-memory implementation of message queue.
 * 
 * <p>Provides publish/subscribe messaging within a single process.
 */
public class MessageQueueInMemory extends MessageQueueBase {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageQueueInMemory.class);
    
    private volatile boolean isRunning = false;
    private final ThreadSafeDict<String, SubscriptionInMemory> subscribers;
    private final int queueMaxSize;
    private BlockingQueue<TopicMessage> queue;
    private CompletableFuture<Void> consumeTask;
    private final long timeoutMs;
    private ExecutorService executor;
    
    /**
     * Creates an in-memory message queue with default settings.
     */
    public MessageQueueInMemory() {
        this(10000, 120000);
    }
    
    /**
     * Creates an in-memory message queue.
     *
     * @param queueMaxSize maximum queue size
     * @param timeoutMs timeout in milliseconds
     */
    public MessageQueueInMemory(int queueMaxSize, long timeoutMs) {
        this.queueMaxSize = queueMaxSize;
        this.timeoutMs = timeoutMs;
        this.subscribers = new ThreadSafeDict<>();
        this.queue = new LinkedBlockingQueue<>(queueMaxSize);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "MessageQueue-consumer");
            t.setDaemon(true);
            return t;
        });
    }
    
    @Override
    public void start() {
        if (!isRunning) {
            isRunning = true;
            if (executor == null || executor.isShutdown()) {
                executor = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "MessageQueue-consumer");
                    t.setDaemon(true);
                    return t;
                });
            }
            consumeTask = CompletableFuture.runAsync(this::consumeMessages, executor);
        }
    }
    
    @Override
    public CompletableFuture<Void> stop() {
        if (isRunning) {
            isRunning = false;
            if (consumeTask != null) {
                consumeTask.cancel(true);
                consumeTask = null;
            }
            queue = new LinkedBlockingQueue<>(queueMaxSize);
            executor.shutdownNow();
        }
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public SubscriptionBase subscribe(String topic) {
        if (subscribers.containsKey(topic)) {
            throw new IllegalArgumentException("Topic '" + topic + "' is already subscribed.");
        }
        SubscriptionInMemory subscription = new SubscriptionInMemory(queueMaxSize, timeoutMs);
        subscribers.put(topic, subscription);
        return subscription;
    }
    
    @Override
    public CompletableFuture<Void> unsubscribe(String topic) {
        SubscriptionInMemory subscription = subscribers.get(topic);
        if (subscription != null) {
            return subscription.deactivate().thenRun(() -> subscribers.remove(topic));
        }
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> produceMessage(String topic, Object message) {
        return CompletableFuture.runAsync(() -> {
            QueueMessage queueMessage;
            if (message instanceof QueueMessage) {
                queueMessage = (QueueMessage) message;
            } else {
                queueMessage = new QueueMessage("", message);
            }
            
            try {
                queue.put(new TopicMessage(topic, queueMessage));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while producing message", e);
            }
        });
    }
    
    private void consumeMessages() {
        while (isRunning) {
            try {
                TopicMessage topicMessage = queue.poll(100, TimeUnit.MILLISECONDS);
                if (topicMessage == null) {
                    continue;
                }
                
                String topic = topicMessage.topic();
                QueueMessage message = topicMessage.message();
                
                SubscriptionInMemory subscription = subscribers.get(topic);
                if (subscription != null && subscription.isActive()) {
                    subscription.pushMessage(message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * Internal record for topic-message pair.
     */
    private record TopicMessage(String topic, QueueMessage message) {}
}

