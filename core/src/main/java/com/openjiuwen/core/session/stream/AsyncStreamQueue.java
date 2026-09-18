/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.common.constants.TimeoutConstants;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.events.LogEventType;
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
     * Offer a critical frame (for example END_FRAME) until the queue accepts it or closes.
     * Regular {@link #send} may drop after retries; dropping END_FRAME leaves consumers blocked.
     */
    public void sendCritical(Object data) {
        if (closed.get()) {
            throw new IllegalStateException("StreamQueue is already closed");
        }
        while (!closed.get()) {
            try {
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
        }
        throw new IllegalStateException("StreamQueue is already closed");
    }

    public Object receive() {
        return receive(DEFAULT_RECEIVE_TIMEOUT_MS);
    }

    public Object receive(long timeoutMs) {
        if (closed.get()) {
            throw new RuntimeException("StreamQueue is already closed");
        }

        long effectiveTimeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_RECEIVE_TIMEOUT_MS;
        try {
            Object item = streamQueue.poll(effectiveTimeoutMs, TimeUnit.MILLISECONDS);
            if (item != null) {
                taskDone();
                SESSION_LOGGER.debug(
                        "Stream data received successfully, eventType={}, streamItemType={}",
                        LogEventType.SESSION_STREAM_CHUNK.getValue(),
                        item.getClass().getSimpleName()
                );
            }
            return item;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public void close() {
        close(DEFAULT_CLOSE_TIMEOUT_MS);
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

    private void forceClear() {
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
    }
}
