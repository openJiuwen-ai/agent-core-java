/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
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
 */
public class AsyncStreamQueue {

    /**
     * Default timeout for each send attempt in milliseconds.
     */
    public static final long DEFAULT_SEND_ATTEMPT_TIMEOUT_MS = 200;

    /**
     * Maximum number of retries for sending data.
     */
    public static final int DEFAULT_MAX_SEND_RETRIES = 5;

    /**
     * Default timeout for receiving data in milliseconds, -1 means no timeout.
     */
    public static final long DEFAULT_RECEIVE_TIMEOUT_MS = -1;

    /**
     * Default timeout for closing the queue in milliseconds.
     */
    public static final long DEFAULT_CLOSE_TIMEOUT_MS = 5000;

    private final BlockingQueue<Object> streamQueue;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Create a stream queue with the specified capacity.
     *
     * @param maxSize the max capacity; 0 means unbounded
     */
    public AsyncStreamQueue(int maxSize) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("maxSize must be >= 0");
        }
        this.streamQueue = maxSize > 0
                ? new LinkedBlockingQueue<>(maxSize)
                : new LinkedBlockingQueue<>();
    }

    /**
     * Create an unbounded stream queue.
     */
    public AsyncStreamQueue() {
        this(0);
    }

    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Send data to the queue with retry logic.
     *
     * @param data           the data to send
     * @param attemptTimeout timeout per attempt in milliseconds
     * @param maxRetries     maximum number of retries
     */
    public void send(Object data, long attemptTimeout, int maxRetries) {
        if (closed.get()) {
            throw new IllegalStateException("StreamQueue is already closed");
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
     */
    public void send(Object data) {
        send(data, DEFAULT_SEND_ATTEMPT_TIMEOUT_MS, DEFAULT_MAX_SEND_RETRIES);
    }

    /**
     * Receive data from the queue.
     *
     * @param timeoutMs timeout in milliseconds, -1 for no timeout
     * @return the received data, or null if no data available within timeout
     */
    public Object receive(long timeoutMs) {
        if (closed.get()) {
            throw new IllegalStateException("StreamQueue is already closed");
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
     */
    public Object receive() {
        return receive(DEFAULT_RECEIVE_TIMEOUT_MS);
    }

    /**
     * Close the queue and drain remaining items.
     *
     * @param timeoutMs timeout for close operation in milliseconds
     */
    public void close(long timeoutMs) {
        if (closed.compareAndSet(false, true)) {
            forceClear();
        }
    }

    /**
     * Close with default timeout.
     */
    public void close() {
        close(DEFAULT_CLOSE_TIMEOUT_MS);
    }

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
