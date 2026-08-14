/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.common.logging.Loggers;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe blocking stream queue for producer-consumer pattern.
 * <p>
 * Java equivalent of Python's {@code AsyncStreamQueue} using {@link BlockingQueue}.
 * 
 * @since 0.1.7
 */
public class AsyncStreamQueue {
    /**
     * DEFAULT_SEND_ATTEMPT_TIMEOUT_MS.
     * 
     * @since 0.1.7
     */
    public static final long DEFAULT_SEND_ATTEMPT_TIMEOUT_MS = 200;

    /**
     * DEFAULT_MAX_SEND_RETRIES.
     * 
     * @since 0.1.7
     */
    public static final int DEFAULT_MAX_SEND_RETRIES = 5;

    /**
     * DEFAULT_RECEIVE_TIMEOUT_MS.
     * 
     * @since 0.1.7
     */
    public static final long DEFAULT_RECEIVE_TIMEOUT_MS = -1;

    /**
     * DEFAULT_CLOSE_TIMEOUT_MS.
     * 
     * @since 0.1.7
     */
    public static final long DEFAULT_CLOSE_TIMEOUT_MS = 5000;

    /**
     * Default bounded capacity used when no explicit size is provided.
     * Prevents unbounded memory growth when a consumer disconnects or slows down.
     * 
     * @since 0.1.7
     */
    public static final int DEFAULT_MAX_SIZE = 1024;

    private final BlockingQueue<Object> streamQueue;

    /**
     * AtomicBoolean.
     * 
     * @since 0.1.7
     */
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    /**
     * Create a stream queue with the specified capacity.
     * 
     * @param maxSize the max capacity; 0 means unbounded
     * @since 0.1.7
     */
    public AsyncStreamQueue(int maxSize) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("maxSize must be >= 0");
        }
        this.streamQueue = maxSize > 0 ? new LinkedBlockingQueue<>(maxSize) : new LinkedBlockingQueue<>();
    }

    /**
     * Create a bounded stream queue with the default capacity.
     * 
     * @since 0.1.7
     */
    public AsyncStreamQueue() {
        this(DEFAULT_MAX_SIZE);
    }

    /**
     * isClosed.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isClosed() {
        return isClosed.get();
    }

    /**
     * Send data to the queue with retry logic.
     * 
     * @param data the data to send
     * @param attemptTimeout timeout per attempt in milliseconds
     * @param maxRetries maximum number of retries
     * @since 0.1.7
     */
    public void send(Object data, long attemptTimeout, int maxRetries) {
        if (isClosed.get()) {
            throw new IllegalStateException("StreamQueue is already isClosed");
        }

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                boolean offered = streamQueue.offer(data, attemptTimeout, TimeUnit.MILLISECONDS);
                if (offered) {
                    Loggers.SESSION.debug("Stream data sent successfully, attempt={}", attempt);
                    return;
                }
                Loggers.SESSION.warning("Stream data send timeout, attempt={}", attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Loggers.SESSION.error("Stream data send interrupted, attempt={}", attempt);
                return;
            }
        }

        Loggers.SESSION.error("Failed to send stream data after {} retries", maxRetries);
    }

    /**
     * Send data with default timeout and retries.
     * 
     * @param data the data to send
     * @since 0.1.7
     */
    public void send(Object data) {
        send(data, DEFAULT_SEND_ATTEMPT_TIMEOUT_MS, DEFAULT_MAX_SEND_RETRIES);
    }

    /**
     * Send a critical frame that must never be silently dropped (e.g. the
     * END_FRAME sentinel). Blocks until the frame is accepted or the queue is
     * closed. A silently dropped END_FRAME would leave consumers blocked
     * forever on {@link #receive()}, so critical frames get unbounded wait
     * (backpressure) instead of the bounded-retry-and-drop behavior of
     * {@link #send(Object)}.
     * 
     * @param data the critical frame to send
     * @since 0.1.7
     */
    public void sendCritical(Object data) {
        if (isClosed.get()) {
            throw new IllegalStateException("StreamQueue is already isClosed");
        }
        while (!isClosed.get()) {
            try {
                if (streamQueue.offer(data, DEFAULT_SEND_ATTEMPT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    Loggers.SESSION.debug("Critical stream data sent");
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Loggers.SESSION.error("Critical stream data send interrupted");
                return;
            }
        }
        throw new IllegalStateException("StreamQueue is already isClosed");
    }

    /**
     * Receive data from the queue.
     * 
     * @param timeoutMs timeout in milliseconds, -1 for no timeout
     * @return the received data, or null if no data available within timeout
     * @since 0.1.7
     */
    public Object receive(long timeoutMs) {
        if (isClosed.get()) {
            throw new IllegalStateException("StreamQueue is already isClosed");
        }

        try {
            if (timeoutMs <= 0) {
                // No timeout - block until data available
                return streamQueue.take();
            } else {
                return streamQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Loggers.SESSION.error("Stream data receive interrupted");
            return null;
        }
    }

    /**
     * Receive data with default timeout.
     * 
     * @return the received data
     * @since 0.1.7
     */
    public Object receive() {
        return receive(DEFAULT_RECEIVE_TIMEOUT_MS);
    }

    /**
     * Close the queue and drain remaining items.
     * 
     * @param timeoutMs timeout for close operation in milliseconds
     * @since 0.1.7
     */
    public void close(long timeoutMs) {
        if (isClosed.compareAndSet(false, true)) {
            forceClear();
        }
    }

    /**
     * Close with default timeout.
     * 
     * @since 0.1.7
     */
    public void close() {
        close(DEFAULT_CLOSE_TIMEOUT_MS);
    }

    /**
     * forceClear.
     * 
     * @since 0.1.7
     */
    private void forceClear() {
        int clearedItems = 0;
        while (!streamQueue.isEmpty()) {
            Object item = streamQueue.poll();
            if (item != null) {
                clearedItems++;
            }
        }
        if (clearedItems > 0) {
            Loggers.SESSION.info("StreamQueue force cleared, clearedItems={}", clearedItems);
        }
    }
}
