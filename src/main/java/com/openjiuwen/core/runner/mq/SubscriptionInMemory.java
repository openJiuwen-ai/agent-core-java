/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * In-memory subscription using a bounded queue and background consumer.
 *
 * <p>Mirrors Python's {@code SubscriptionInMemory} in
 * {@code openjiuwen/core/runner/message_queue_inmemory.py}.</p>
 */
public class SubscriptionInMemory extends SubscriptionBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionInMemory.class);
    private static final int DEFAULT_QUEUE_MAX_SIZE = 10_000;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120_000);

    private final int queueMaxSize;
    private final Duration timeout;

    private BlockingQueue<QueueMessage> queue;
    private volatile boolean active;
    private AsyncMessageHandler<Object, Object> handler;
    private ExecutorService consumerExecutor;

    public SubscriptionInMemory() {
        this(DEFAULT_QUEUE_MAX_SIZE, DEFAULT_TIMEOUT);
    }

    public SubscriptionInMemory(int maxSize, double timeoutSeconds) {
        this(maxSize, durationFromSeconds(timeoutSeconds));
    }

    public SubscriptionInMemory(int maxSize, Duration timeout) {
        this.queueMaxSize = maxSize;
        this.timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        this.queue = newQueue(maxSize);
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
                    Thread.ofVirtual().name("subscription-inmemory-", 0).factory());
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
            queue = newQueue(queueMaxSize);
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
                QueueMessage message = queue.take();
                dispatchMessage(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException e) {
                LOGGER.warn("Failed to consume in-memory subscription message", e);
            }
        }
    }

    private void dispatchMessage(QueueMessage message) {
        CompletableFuture<Object> responseFuture;
        try {
            responseFuture = handler.handle(message.getPayload());
        } catch (RuntimeException e) {
            failMessage(message, e);
            return;
        }
        if (responseFuture == null) {
            failMessage(message, new NullPointerException("handler returned null future"));
            return;
        }
        responseFuture.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        failMessage(message, throwable);
                        return;
                    }
                    try {
                        handleResponse(message, response);
                    } catch (RuntimeException e) {
                        failMessage(message, e);
                    }
                });
    }

    private void handleResponse(QueueMessage message, Object response) {
        if (message instanceof InvokeQueueMessage invokeQueueMessage) {
            if (isPythonFalsy(response)) {
                throw new IllegalArgumentException("response is empty");
            }
            if (response instanceof Iterator<?>) {
                throw new IllegalArgumentException("InvokeQueueMessage need not Iterator response");
            }
            invokeQueueMessage.getResponse().complete(response);
            return;
        }
        if (message instanceof StreamQueueMessage streamQueueMessage) {
            @SuppressWarnings("unchecked")
            Iterator<Object> iterator = (Iterator<Object>) requireIterator(response);
            streamQueueMessage.getResponse().complete(iterator);
        }
    }

    private Iterator<?> requireIterator(Object response) {
        if (response == null) {
            throw new IllegalArgumentException("response is empty");
        }
        if (!(response instanceof Iterator<?> iterator)) {
            throw new IllegalArgumentException("StreamQueueMessage need Iterator response");
        }
        return iterator;
    }

    private void failMessage(QueueMessage message, Throwable throwable) {
        Throwable effectiveThrowable = unwrapCompletionException(throwable);
        BaseError baseError = findBaseError(effectiveThrowable);
        Throwable responseThrowable = baseError == null ? effectiveThrowable : baseError;
        if (baseError != null) {
            message.setErrorCode(baseError.getCode());
            message.setErrorMsg(baseError.getMessage());
        } else {
            String reason = failureReason(effectiveThrowable);
            message.setErrorCode(StatusCode.MESSAGE_QUEUE_MESSAGE_CONSUME_ERROR.getCode());
            message.setErrorMsg(ErrorHelper.buildError(
                    StatusCode.MESSAGE_QUEUE_MESSAGE_CONSUME_ERROR,
                    "reason",
                    reason).getMessage());
        }
        if (message instanceof InvokeQueueMessage invokeQueueMessage && !invokeQueueMessage.getResponse().isDone()) {
            invokeQueueMessage.getResponse().completeExceptionally(responseThrowable);
        }
        if (message instanceof StreamQueueMessage streamQueueMessage && !streamQueueMessage.getResponse().isDone()) {
            streamQueueMessage.getResponse().completeExceptionally(responseThrowable);
        }
    }

    private BaseError findBaseError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof BaseError baseError) {
                return baseError;
            }
            current = current.getCause();
        }
        return null;
    }

    private Throwable unwrapCompletionException(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private String failureReason(Throwable throwable) {
        if (throwable instanceof TimeoutException) {
            return "timeout";
        }
        String message = throwable.getMessage();
        return message == null ? "" : message;
    }

    private boolean isPythonFalsy(Object response) {
        if (response == null) {
            return true;
        }
        if (response instanceof Boolean bool) {
            return !bool;
        }
        if (response instanceof Number number) {
            return Double.compare(number.doubleValue(), 0.0d) == 0;
        }
        if (response instanceof CharSequence text) {
            return text.isEmpty();
        }
        if (response instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (response instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    private static BlockingQueue<QueueMessage> newQueue(int maxSize) {
        if (maxSize <= 0) {
            return new LinkedBlockingQueue<>();
        }
        return new LinkedBlockingQueue<>(maxSize);
    }

    private static Duration durationFromSeconds(double timeoutSeconds) {
        long timeoutMillis = Math.max(0L, Math.round(timeoutSeconds * 1000.0d));
        return Duration.ofMillis(timeoutMillis);
    }
}
