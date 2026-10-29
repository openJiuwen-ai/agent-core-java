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
     * DEFAULT_RECEIVE_TIMEOUT_MS. Positive by design: a consumer must never
     * block forever waiting on a producer that will never send another frame.
     *
     * @since 0.1.7
     */
    public static final long DEFAULT_RECEIVE_TIMEOUT_MS = 600_000L;

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

    /**
     * DEFAULT_CRITICAL_SEND_TOTAL_TIMEOUT_MS. Total bound for sending a critical
     * frame: when the queue stays full this long, the oldest data frames are
     * evicted just enough to admit the critical frame (e.g. END_FRAME) so the
     * producer thread returns instead of spinning forever on a dead consumer;
     * the backlog is cleared fully only when concurrent producers refill every
     * freed slot.
     *
     * @since 0.1.14.post2
     */
    public static final long DEFAULT_CRITICAL_SEND_TOTAL_TIMEOUT_MS = 60_000L;

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
     * Send a critical frame (e.g. the END_FRAME sentinel) with backpressure
     * instead of the bounded-retry-and-drop behavior of {@link #send(Object)}:
     * the send keeps retrying while the queue is full, but the total wait is
     * bounded by {@link #DEFAULT_CRITICAL_SEND_TOTAL_TIMEOUT_MS}. When the queue
     * stays full that long, the oldest data frames are evicted just enough to
     * admit the critical frame so the producer thread returns; the backlog is
     * cleared fully only when concurrent producers refill every freed slot. In
     * the rare race where admission still fails after that clearing, the
     * critical frame is dropped and the drop is logged at error level; consumers
     * then end on their receive timeout instead of the dropped frame.
     *
     * @param data the critical frame to send
     * @since 0.1.7
     */
    public void sendCritical(Object data) {
        sendCritical(data, DEFAULT_CRITICAL_SEND_TOTAL_TIMEOUT_MS);
    }

    /**
     * Send a critical frame with an explicit total budget in milliseconds.
     * Package-private so tests can exercise the timeout path quickly.
     *
     * @param data the critical frame to send
     * @param totalTimeoutMs total time budget for the whole send in milliseconds
     * @since 0.1.14.post2
     */
    void sendCritical(Object data, long totalTimeoutMs) {
        if (isClosed.get()) {
            throw new IllegalStateException("StreamQueue is already isClosed");
        }
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(totalTimeoutMs);
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
            if (System.nanoTime() >= deadlineNanos) {
                forceCriticalAfterTimeout(data, totalTimeoutMs);
                return;
            }
        }
        throw new IllegalStateException("StreamQueue is already isClosed");
    }

    /**
     * Admit the critical frame with minimal eviction so the producer thread can
     * return. A non-blocking offer is attempted first (zero loss when a slot is
     * already free); then head data frames are evicted one by one, only as many
     * as needed to admit the frame and never more than the backlog snapshot
     * taken on entry. Only when concurrent producers refill every freed slot is
     * the full backlog cleared as a last resort. Data frames already use
     * bounded-retry-and-drop semantics in {@link #send(Object)}; the critical
     * frame takes the freed capacity.
     *
     * @param data the critical frame to admit
     * @param totalTimeoutMs total wait already spent in milliseconds
     * @since 0.1.14.post2
     */
    private void forceCriticalAfterTimeout(Object data, long totalTimeoutMs) {
        int backlog = streamQueue.size();
        int evictedFrames = 0;
        boolean isAdmitted = streamQueue.offer(data);
        while (!isAdmitted && evictedFrames < backlog) {
            if (streamQueue.poll() != null) {
                evictedFrames++;
            }
            isAdmitted = streamQueue.offer(data);
        }
        if (isAdmitted) {
            Loggers.SESSION.error("Critical frame forced after {}ms total wait; evicted {} head frames",
                    totalTimeoutMs, evictedFrames);
            return;
        }
        forceClear();
        boolean isFallbackAdmitted = false;
        try {
            isFallbackAdmitted = streamQueue.offer(data, DEFAULT_SEND_ATTEMPT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (isFallbackAdmitted) {
            Loggers.SESSION.error("Critical frame forced after {}ms total wait; cleared full backlog of {} frames",
                    totalTimeoutMs, backlog);
        } else {
            Loggers.SESSION.error("Critical frame dropped after {}ms total wait; cleared full backlog of {} frames",
                    totalTimeoutMs, backlog);
        }
    }

    /**
     * Receive data from the queue.
     *
     * @param timeoutMs timeout in milliseconds; non-positive values fall back to
     *                  {@link #DEFAULT_RECEIVE_TIMEOUT_MS} so a consumer never
     *                  blocks forever on a dead producer
     * @return the received data, or null if no data available within timeout
     * @since 0.1.7
     */
    public Object receive(long timeoutMs) {
        if (isClosed.get()) {
            throw new IllegalStateException("StreamQueue is already isClosed");
        }

        long effectiveTimeoutMs = resolveEffectiveReceiveTimeoutMs(timeoutMs);
        try {
            return streamQueue.poll(effectiveTimeoutMs, TimeUnit.MILLISECONDS);
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
     * Resolve the effective receive timeout: non-positive values fall back to
     * {@link #DEFAULT_RECEIVE_TIMEOUT_MS} so a consumer never blocks forever on
     * a dead producer. Package-private static so tests can lock the fallback
     * mapping without waiting for the default timeout itself.
     *
     * @param timeoutMs requested timeout in milliseconds
     * @return the timeout to poll with, always positive
     * @since 0.1.14.post2
     */
    static long resolveEffectiveReceiveTimeoutMs(long timeoutMs) {
        return timeoutMs > 0 ? timeoutMs : DEFAULT_RECEIVE_TIMEOUT_MS;
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
