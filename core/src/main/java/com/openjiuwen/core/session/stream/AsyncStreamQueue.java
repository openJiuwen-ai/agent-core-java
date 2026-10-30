/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.common.constants.TimeoutConstants;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.events.LogEventType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Blocking queue analogue for Python's async stream queue helper.
 * <p>
 * Mirrors Python's {@code AsyncStreamQueue} in
 * {@code openjiuwen/core/session/stream/emitter.py}.
 */
public class AsyncStreamQueue {

    public static final long DEFAULT_SEND_ATTEMPT_TIMEOUT_MS = 200L;
    public static final int DEFAULT_MAX_SEND_RETRIES = 5;
    public static final int DEFAULT_MAX_SIZE = 1024;

    /**
     * Non-positive caller values no longer mean "block forever": {@link #receive(long)}
     * always falls back to this timeout. Defaults to {@link TimeoutConstants#BLOCKING_QUEUE_MS}
     * and can be overridden via {@code -Dopenjiuwen.timeout.blocking-queue-ms=...}.
     */
    public static final long DEFAULT_RECEIVE_TIMEOUT_MS = TimeoutConstants.BLOCKING_QUEUE_MS;
    public static final long DEFAULT_CLOSE_TIMEOUT_MS = 5000L;

    /**
     * Total bound for {@link #sendCritical(Object)}: when the queue stays full this long
     * (no consumer drains it), the oldest data frames are evicted just enough to admit
     * the critical frame (for example END_FRAME) so the producer thread returns instead
     * of spinning forever on a dead consumer. Data frames already use bounded-retry-and-drop
     * semantics in {@link #send(Object)}; the critical frame takes the freed capacity.
     *
     * @since 0.1.17
     */
    public static final long DEFAULT_CRITICAL_SEND_TOTAL_TIMEOUT_MS = 60_000L;

    private static final LoggerProtocol SESSION_LOGGER = Loggers.SESSION;

    private final BlockingQueue<Object> streamQueue;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger unfinishedTasks = new AtomicInteger(0);
    private final Object taskMonitor = new Object();

    public AsyncStreamQueue() {
        this(DEFAULT_MAX_SIZE);
    }

    public AsyncStreamQueue(int maxSize) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("maxsize must be >= 0");
        }
        this.streamQueue = maxSize > 0 ? new LinkedBlockingQueue<>(maxSize) : new LinkedBlockingQueue<>();
    }

    public boolean isClosed() {
        return closed.get();
    }

    public void send(Object data) {
        send(data, DEFAULT_SEND_ATTEMPT_TIMEOUT_MS, DEFAULT_MAX_SEND_RETRIES);
    }

    public void send(Object data, long attemptTimeoutMs, int maxRetries) {
        if (closed.get()) {
            throw new RuntimeException("StreamQueue is already closed");
        }

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                // Count before offering: a concurrent receive() may drain the item the
                // instant it lands, so incrementing after offer() can leave a permanent
                // phantom count that stalls awaitDrain() on close().
                unfinishedTasks.incrementAndGet();
                boolean offered = streamQueue.offer(data, attemptTimeoutMs, TimeUnit.MILLISECONDS);
                if (offered) {
                    SESSION_LOGGER.debug(
                            "Stream data sent successfully, eventType={}, timeoutMs={}, attempt={}",
                            LogEventType.SESSION_STREAM_CHUNK.getValue(),
                            attemptTimeoutMs,
                            attempt + 1
                    );
                    return;
                }
                unfinishedTasks.decrementAndGet();
                SESSION_LOGGER.warn(
                        "Stream data send timeout, eventType={}, timeoutMs={}, attempt={}",
                        LogEventType.SESSION_STREAM_ERROR.getValue(),
                        attemptTimeoutMs,
                        attempt + 1
                );
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                unfinishedTasks.decrementAndGet();
                SESSION_LOGGER.error(
                        "Stream data send interrupted, eventType={}, timeoutMs={}, attempt={}",
                        LogEventType.SESSION_STREAM_ERROR.getValue(),
                        attemptTimeoutMs,
                        attempt + 1
                );
                return;
            }
        }

        SESSION_LOGGER.error(
                "Failed to send stream data after max retries, eventType={}, maxRetries={}, timeoutMs={}",
                LogEventType.SESSION_STREAM_ERROR.getValue(),
                maxRetries,
                attemptTimeoutMs
        );
    }

    /**
     * Offer a critical frame (for example END_FRAME) with backpressure instead of the
     * bounded-retry-and-drop behavior of {@link #send(Object)}: the send keeps retrying
     * while the queue is full, but the total wait is bounded by
     * {@link #DEFAULT_CRITICAL_SEND_TOTAL_TIMEOUT_MS}. When the queue stays full that
     * long, the oldest data frames are evicted just enough to admit the critical frame
     * so the producer thread returns; the backlog is cleared fully only when concurrent
     * producers refill every freed slot. Regular {@link #send} may drop after retries;
     * dropping END_FRAME silently would leave consumers blocked, so the deadline escape
     * is the degradation signal and is logged at error level.
     */
    public void sendCritical(Object data) {
        sendCritical(data, DEFAULT_CRITICAL_SEND_TOTAL_TIMEOUT_MS);
    }

    /**
     * Offer a critical frame with an explicit total budget in milliseconds.
     * Package-private so tests can exercise the timeout path quickly.
     *
     * @param data the critical frame to send
     * @param totalTimeoutMs total time budget for the whole send in milliseconds
     * @since 0.1.17
     */
    void sendCritical(Object data, long totalTimeoutMs) {
        if (closed.get()) {
            throw new IllegalStateException("StreamQueue is already closed");
        }
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(totalTimeoutMs);
        while (!closed.get()) {
            try {
                // Count before offering: a concurrent receive() may drain the frame the
                // instant it lands, so incrementing after offer() can leave a permanent
                // phantom count that stalls awaitDrain() on close().
                unfinishedTasks.incrementAndGet();
                if (streamQueue.offer(data, DEFAULT_SEND_ATTEMPT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    return;
                }
                unfinishedTasks.decrementAndGet();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                unfinishedTasks.decrementAndGet();
                return;
            }
            if (System.nanoTime() >= deadlineNanos) {
                forceCriticalAfterTimeout(data, totalTimeoutMs);
                return;
            }
        }
        throw new IllegalStateException("StreamQueue is already closed");
    }

    /**
     * Admit the critical frame with minimal eviction so the producer thread can return.
     * A non-blocking offer is attempted first (zero loss when a slot is already free);
     * then head data frames are evicted one by one, only as many as needed to admit the
     * frame. Evictions and loop iterations are both capped by the backlog snapshot
     * taken on entry, so the loop terminates even when concurrent producers keep
     * refilling freed slots. Only when every freed slot has been refilled is the
     * full backlog cleared as a last resort, via the same {@link #forceClear()}
     * accounting close() uses on drain timeout. Data frames
     * already use bounded-retry-and-drop semantics in {@link #send(Object)}; the critical
     * frame takes the freed capacity. Evicted frames retire their {@code unfinishedTasks}
     * count here, mirroring {@link #closeAndDrain()}.
     *
     * @param data the critical frame to admit
     * @param totalTimeoutMs total wait already spent in milliseconds
     * @since 0.1.17
     */
    private void forceCriticalAfterTimeout(Object data, long totalTimeoutMs) {
        int backlog = streamQueue.size();
        int attempts = 0;
        int evictedFrames = 0;
        // Speculative count before offering, same invariant as send(): a concurrent
        // receive() may drain the critical frame the instant it lands.
        unfinishedTasks.incrementAndGet();
        boolean isAdmitted = streamQueue.offer(data);
        while (!isAdmitted && attempts < backlog) {
            attempts++;
            if (streamQueue.poll() != null) {
                evictedFrames++;
                taskDone();
            }
            isAdmitted = streamQueue.offer(data);
        }
        if (isAdmitted) {
            SESSION_LOGGER.error(
                    "Critical frame forced after {}ms total wait; evicted {} head frames, eventType={}",
                    totalTimeoutMs,
                    evictedFrames,
                    LogEventType.SESSION_STREAM_ERROR.getValue()
            );
            return;
        }
        unfinishedTasks.decrementAndGet();
        int clearedFrames = forceClear();
        reofferCriticalAfterClear(data, totalTimeoutMs, clearedFrames);
    }

    /**
     * Re-offer the critical frame once after the fallback clear, then log whether it
     * landed or was dropped. The speculative {@code unfinishedTasks} count is retired
     * when the frame does not land, mirroring {@link #sendCritical(Object, long)}.
     *
     * @param data the critical frame to re-offer
     * @param totalTimeoutMs total wait already spent in milliseconds
     * @param clearedFrames number of frames removed by the fallback clear
     */
    private void reofferCriticalAfterClear(Object data, long totalTimeoutMs, int clearedFrames) {
        boolean isFallbackAdmitted = false;
        try {
            unfinishedTasks.incrementAndGet();
            isFallbackAdmitted = streamQueue.offer(data, DEFAULT_SEND_ATTEMPT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!isFallbackAdmitted) {
                unfinishedTasks.decrementAndGet();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            unfinishedTasks.decrementAndGet();
        }
        if (isFallbackAdmitted) {
            SESSION_LOGGER.error(
                    "Critical frame forced after {}ms total wait; cleared full backlog of {} frames, eventType={}",
                    totalTimeoutMs,
                    clearedFrames,
                    LogEventType.SESSION_STREAM_ERROR.getValue()
            );
        } else {
            SESSION_LOGGER.error(
                    "Critical frame dropped after {}ms total wait; cleared full backlog of {} frames, eventType={}",
                    totalTimeoutMs,
                    clearedFrames,
                    LogEventType.SESSION_STREAM_ERROR.getValue()
            );
        }
    }

    public Object receive() {
        return receive(DEFAULT_RECEIVE_TIMEOUT_MS);
    }

    /**
     * Poll one frame without waiting.
     *
     * <p>A non-null frame follows the same completion accounting and logging path as
     * {@link #receive(long)}. An empty queue returns {@code null} without changing accounting.</p>
     *
     * @return the next queued frame, or {@code null} when no frame is immediately available
     */
    public Object tryReceive() {
        if (closed.get()) {
            throw new RuntimeException("StreamQueue is already closed");
        }
        return completeReceive(streamQueue.poll());
    }

    public Object receive(long timeoutMs) {
        if (closed.get()) {
            throw new RuntimeException("StreamQueue is already closed");
        }

        long effectiveTimeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_RECEIVE_TIMEOUT_MS;
        try {
            return completeReceive(streamQueue.poll(effectiveTimeoutMs, TimeUnit.MILLISECONDS));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private Object completeReceive(Object item) {
        if (item != null) {
            taskDone();
            SESSION_LOGGER.debug(
                    "Stream data received successfully, eventType={}, streamItemType={}",
                    LogEventType.SESSION_STREAM_CHUNK.getValue(),
                    item.getClass().getSimpleName()
            );
        }
        return item;
    }

    public void close() {
        close(DEFAULT_CLOSE_TIMEOUT_MS);
    }

    List<Object> closeAndDrain() {
        // Stop new sends before taking the terminal snapshot; no consumer remains to await.
        closed.set(true);
        List<Object> items = new ArrayList<>();
        streamQueue.drainTo(items);
        for (int index = 0; index < items.size(); index++) {
            taskDone();
        }
        return items;
    }

    public void close(long timeoutMs) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        // An empty queue has nothing to drain: any residual count is a phantom left
        // behind by a consumer that stopped iterating mid-stream (cancellation,
        // session reuse). Waiting for it would stall for the full timeout, so
        // reset the accounting and return immediately.
        if (streamQueue.isEmpty()) {
            forceClear();
            return;
        }
        if (!awaitDrain(timeoutMs)) {
            SESSION_LOGGER.error(
                    "StreamQueue close timeout, force clearing queue, eventType={}, timeoutMs={}",
                    LogEventType.SESSION_STREAM_ERROR.getValue(),
                    timeoutMs
            );
            forceClear();
        }
    }

    private boolean awaitDrain(long timeoutMs) {
        long deadline = timeoutMs > 0 ? System.currentTimeMillis() + timeoutMs : Long.MAX_VALUE;
        synchronized (taskMonitor) {
            while (unfinishedTasks.get() > 0) {
                long waitMs = timeoutMs > 0 ? deadline - System.currentTimeMillis() : 0L;
                if (timeoutMs > 0 && waitMs <= 0) {
                    return false;
                }
                try {
                    taskMonitor.wait(timeoutMs > 0 ? waitMs : 0L);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return true;
    }

    private void taskDone() {
        int remaining = unfinishedTasks.updateAndGet(current -> Math.max(0, current - 1));
        if (remaining == 0) {
            synchronized (taskMonitor) {
                taskMonitor.notifyAll();
            }
        }
    }

    private int forceClear() {
        int clearedItems = 0;
        while (!streamQueue.isEmpty()) {
            Object item = streamQueue.poll();
            if (item != null) {
                clearedItems++;
                taskDone();
            }
        }

        while (unfinishedTasks.get() > 0) {
            taskDone();
        }

        SESSION_LOGGER.info(
                "StreamQueue force cleared, eventType={}, clearedItems={}",
                LogEventType.SESSION_STREAM_CHUNK.getValue(),
                clearedItems
        );
        return clearedItems;
    }
}
