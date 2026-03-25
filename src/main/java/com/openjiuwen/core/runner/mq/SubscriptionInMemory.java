/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.runner.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * In-memory subscription using a blocking queue and Virtual Thread consumer.
 * Mirrors Python's {@code SubscriptionInMemory} in {@code message_queue_inmemory.py}.
 */
public class SubscriptionInMemory extends SubscriptionBase {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionInMemory.class);

    private final int queueMaxSize;
    private final long timeoutMs;
    private BlockingQueue<QueueMessage> queue;
    private volatile boolean active;
    private AsyncMessageHandler<Object, Object> handler;
    private ExecutorService consumerExecutor;

    public SubscriptionInMemory(int maxSize, long timeoutMs) {
        this.queueMaxSize = maxSize;
        this.timeoutMs = timeoutMs;
        this.queue = new LinkedBlockingQueue<>(maxSize);
        this.active = false;
    }

    public SubscriptionInMemory() {
        this(10000, 120_000L);
    }

    @Override
    public void setMessageHandler(AsyncMessageHandler<Object, Object> handler) {
        this.handler = handler;
    }

    @Override
    public void activate() {
        if (!active) {
            active = true;
            consumerExecutor = Executors.newSingleThreadExecutor(
                    Thread.ofVirtual().name("sub-inmemory-", 0).factory());
            consumerExecutor.submit(this::consumeMessages);
        }
    }

    @Override
    public void deactivate() {
        if (active) {
            active = false;
            if (consumerExecutor != null) {
                consumerExecutor.shutdownNow();
                consumerExecutor = null;
            }
            queue = new LinkedBlockingQueue<>(queueMaxSize);
        }
    }

    @Override
    public boolean isActive() {
        return active;
    }

    public void pushMessage(QueueMessage message) {
        if (message.getMessageId() == null || message.getMessageId().isEmpty()) {
            message.setMessageId(UUID.randomUUID().toString());
        }
        try {
            queue.put(message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void consumeMessages() {
        while (active && handler != null) {
            try {
                QueueMessage message = queue.poll(1, TimeUnit.SECONDS);
                if (message == null) {
                    continue;
                }
                try {
                    CompletableFuture<Object> future = handler.handle(message.getPayload());
                    future.whenComplete((response, throwable) -> {
                        if (throwable != null) {
                            message.setErrorCode(-1);
                            message.setErrorMsg(throwable.getMessage());
                            if (message instanceof InvokeQueueMessage iqm && !iqm.getResponse().isDone()) {
                                iqm.getResponse().completeExceptionally(throwable);
                            }
                            if (message instanceof StreamQueueMessage sqm && !sqm.getResponse().isDone()) {
                                sqm.getResponse().completeExceptionally(throwable);
                            }
                        } else {
                            handleResponse(message, response);
                        }
                    });
                } catch (Exception e) {
                    message.setErrorCode(-1);
                    message.setErrorMsg(e.getMessage());
                    if (message instanceof InvokeQueueMessage iqm && !iqm.getResponse().isDone()) {
                        iqm.getResponse().completeExceptionally(e);
                    }
                    if (message instanceof StreamQueueMessage sqm && !sqm.getResponse().isDone()) {
                        sqm.getResponse().completeExceptionally(e);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void handleResponse(QueueMessage message, Object response) {
        if (message instanceof InvokeQueueMessage iqm) {
            iqm.getResponse().complete(response);
        } else if (message instanceof StreamQueueMessage sqm) {
            @SuppressWarnings("unchecked")
            var iter = (java.util.Iterator<Object>) response;
            sqm.getResponse().complete(iter);
        }
    }
}
