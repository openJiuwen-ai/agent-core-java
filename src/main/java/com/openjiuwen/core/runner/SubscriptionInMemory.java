// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * In-memory implementation of subscription.
 */
public class SubscriptionInMemory extends SubscriptionBase {
    
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionInMemory.class);
    
    private final int queueMaxSize;
    private final long timeoutMs;
    private BlockingQueue<QueueMessage> queue;
    private CompletableFuture<Void> consumeTask;
    private AsyncMessageHandler handler;
    private volatile boolean isActive = false;
    private final ExecutorService executor;
    
    /**
     * Creates an in-memory subscription with default settings.
     */
    public SubscriptionInMemory() {
        this(10000, 120000);
    }
    
    /**
     * Creates an in-memory subscription.
     *
     * @param maxSize maximum queue size
     * @param timeoutMs timeout in milliseconds
     */
    public SubscriptionInMemory(int maxSize, long timeoutMs) {
        this.queueMaxSize = maxSize;
        this.timeoutMs = timeoutMs;
        this.queue = new LinkedBlockingQueue<>(maxSize);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Subscription-consumer");
            t.setDaemon(true);
            return t;
        });
    }
    
    @Override
    public void setMessageHandler(AsyncMessageHandler handler) {
        this.handler = handler;
    }
    
    @Override
    public void activate() {
        if (!isActive) {
            isActive = true;
            consumeTask = CompletableFuture.runAsync(this::consumeMessages, executor);
        }
    }
    
    @Override
    public CompletableFuture<Void> deactivate() {
        if (isActive) {
            isActive = false;
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
    public boolean isActive() {
        return isActive;
    }
    
    /**
     * Pushes a message to the subscription queue.
     *
     * @param message the message to push
     * @return a future that completes when the message is queued
     */
    public CompletableFuture<Void> pushMessage(QueueMessage message) {
        return CompletableFuture.runAsync(() -> {
            if (message.getMessageId() == null || message.getMessageId().isEmpty()) {
                message.setMessageId(UUID.randomUUID().toString());
            }
            try {
                queue.put(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while pushing message", e);
            }
        });
    }
    
    private void consumeMessages() {
        while (isActive && handler != null) {
            try {
                QueueMessage message = queue.poll(100, TimeUnit.MILLISECONDS);
                if (message == null) {
                    continue;
                }
                
                try {
                    CompletableFuture<Object> responseFuture = handler.apply(message.getPayload());
                    Object response = responseFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
                    handleResponse(message, response);
                } catch (JiuWenBaseException e) {
                    message.setErrorCode(e.getErrorCode());
                    message.setErrorMsg(e.getMessage());
                    setMessageException(message, e);
                } catch (Exception e) {
                    message.setErrorCode(StatusCode.ERROR.getCode());
                    message.setErrorMsg(e.getMessage());
                    setMessageException(message, e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private void handleResponse(QueueMessage message, Object response) {
        if (message instanceof InvokeQueueMessage) {
            if (response == null) {
                throw new JiuWenBaseException(StatusCode.ERROR.getCode(), "Response is empty");
            }
            if (response instanceof Iterator) {
                throw new JiuWenBaseException(StatusCode.ERROR.getCode(), 
                    "InvokeQueueMessage need not AsyncIterator response");
            }
            ((InvokeQueueMessage<Object>) message).getResponse().complete(response);
        }
        
        if (message instanceof StreamQueueMessage) {
            if (response == null) {
                throw new JiuWenBaseException(StatusCode.ERROR.getCode(), "Response is empty");
            }
            if (!(response instanceof Iterator)) {
                throw new JiuWenBaseException(StatusCode.ERROR.getCode(), 
                    "StreamQueueMessage need AsyncIterator response");
            }
            ((StreamQueueMessage<Object>) message).getResponse().complete((Iterator<Object>) response);
        }
    }
    
    @SuppressWarnings("unchecked")
    private void setMessageException(QueueMessage message, Exception e) {
        if (message instanceof InvokeQueueMessage) {
            CompletableFuture<Object> future = ((InvokeQueueMessage<Object>) message).getResponse();
            if (!future.isDone()) {
                future.completeExceptionally(e);
            }
        } else if (message instanceof StreamQueueMessage) {
            CompletableFuture<Iterator<Object>> future = ((StreamQueueMessage<Object>) message).getResponse();
            if (!future.isDone()) {
                future.completeExceptionally(e);
            }
        }
    }
}

