/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.RunnerTermination;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.ResultType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Collects responses for one distributed request.
 * Supports cancellation, expiration, and queue-full detection.
 *
 * <p>Mirrors Python's {@code ResponseCollector} in
 * {@code openjiuwen.core.runner.drunner.dmessage_queue.dsubscription.response_collector}.</p>
 */
public class ResponseCollector {

    private static final Logger logger = LoggerFactory.getLogger(ResponseCollector.class);
    private static final int MAX_QUEUE_SIZE = 10_000;
    private static final ScheduledExecutorService TTL_SCHEDULER;

    static {
        ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1);
        exec.setRemoveOnCancelPolicy(true);
        TTL_SCHEDULER = exec;
    }

    /** Sentinel value placed in the queue to signal cancellation/expiration. */
    private static final DmqResponseMessage CANCEL_SENTINEL = new DmqResponseMessage();

    private final String messageId;
    private final String receiverId;
    private final String requestId;
    private final double ttlSeconds;
    private final BlockingQueue<DmqResponseMessage> queue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);

    private volatile boolean cancelled;
    private volatile boolean expired;
    private volatile CancelReason cancelReason;
    private final ScheduledFuture<?> expireTask;

    public ResponseCollector(String messageId, String receiverId, String requestId, Double ttlSeconds) {
        this.messageId = messageId;
        this.receiverId = receiverId;
        this.requestId = requestId;
        this.ttlSeconds = ttlSeconds != null ? ttlSeconds : 30.0;

        // Schedule TTL expiration
        this.expireTask = TTL_SCHEDULER.schedule(() -> {
            if (!cancelled) {
                expired = true;
                cleanupQueue();
                logger.warn("[Collector:{}] expired after {:.1f}s", messageId, this.ttlSeconds);
                wakeWaiters(CancelReason.TTL_EXPIRE);
            }
        }, (long) (this.ttlSeconds * 1000), TimeUnit.MILLISECONDS);
    }

    /**
     * Whether this collector has been cancelled.
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Whether this collector has expired due to TTL.
     */
    public boolean isExpired() {
        return expired;
    }

    /**
     * Whether this collector is still active (not cancelled and not expired).
     */
    public boolean isActive() {
        return !cancelled && !expired;
    }

    /**
     * Receive a message from the reply topic.
     */
    public void putMessage(DmqResponseMessage message) {
        if (!isActive()) {
            logger.warn("[Collector:{}] inactive, discard message", messageId);
            return;
        }
        if (!queue.offer(message)) {
            logger.warn("[Collector:{}] queue full({}), auto-cancelled", messageId, MAX_QUEUE_SIZE);
            close(CancelReason.QUEUE_FULL);
        }
    }

    /**
     * Wait for a single result.
     */
    public Object result(Double timeoutSeconds) throws Exception {
        double effectiveTimeout = timeoutSeconds != null ? timeoutSeconds : ttlSeconds;

        if (cancelled) {
            throw new CancellationException("Collector(" + messageId + ") was cancelled before request send");
        }
        if (expired) {
            throw new TimeoutException("Collector(" + messageId + ") expired");
        }
        try {
            DmqResponseMessage message = poll(effectiveTimeout);
            checkMessage(message);
            return message.getBody();
        } catch (TimeoutException e) {
            expired = true;
            cleanupQueue();
            logger.warn("[Collector:{}] result timeout ({:.1f}s)", messageId, effectiveTimeout);
            throw new TimeoutException("Collector(" + messageId + ") timeout waiting for result");
        } finally {
            close(CancelReason.FINISH);
        }
    }

    /**
     * Stream results as an iterator.
     */
    public Iterator<Object> stream(Double timeoutSeconds) {
        double effectiveTimeout = timeoutSeconds != null ? timeoutSeconds : ttlSeconds;
        return new Iterator<>() {
            private Object next;
            private boolean done;

            @Override
            public boolean hasNext() {
                if (done) {
                    return false;
                }
                if (next != null) {
                    return true;
                }
                try {
                    DmqResponseMessage message = poll(effectiveTimeout);
                    checkMessage(message);
                    if (message.isLastChunk()) {
                        done = true;
                        close(CancelReason.FINISH);
                        return false;
                    }
                    next = message.getBody();
                    return true;
                } catch (TimeoutException e) {
                    expired = true;
                    logger.warn("[Collector:{}] stream timeout ({:.1f}s)", messageId, effectiveTimeout);
                    close(CancelReason.FINISH);
                    throw new RuntimeException(
                            new TimeoutException("Collector(" + messageId + ") stream timeout"));
                } catch (Exception e) {
                    close(CancelReason.FINISH);
                    throw new RuntimeException("Failed to read distributed stream response", e);
                }
            }

            @Override
            public Object next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                Object current = next;
                next = null;
                return current;
            }
        };
    }

    /**
     * Check a polled message for cancel/error signals.
     *
     * @throws CancellationException if the message is a cancel sentinel
     * @throws TimeoutException      if cancel reason is TTL_EXPIRE
     * @throws IllegalStateException if the remote returned an error
     */
    public void checkMessage(DmqResponseMessage message) throws Exception {
        if (message == CANCEL_SENTINEL) {
            CancelReason reason = cancelReason;
            if (reason == CancelReason.TTL_EXPIRE) {
                throw new TimeoutException("Collector(" + messageId + ") timeout");
            } else if (reason == CancelReason.QUEUE_FULL) {
                throw new CancellationException("Collector(" + messageId + ") queue full");
            } else {
                throw new RunnerTermination(
                        "Collector(" + messageId + ") was cancelled",
                        StatusCode.RUNNER_TERMINATION_ERROR);
            }
        }
        if (message.getResultType() == ResultType.ERROR) {
            throw ErrorHelper.buildError(
                    StatusCode.REMOTE_AGENT_RESPONSE_PROCESS_ERROR,
                    null,
                    null,
                    null,
                    java.util.Map.of(
                            "message_id", messageId,
                            "process_id", receiverId,
                            "error_code", message.getErrorCode(),
                            "error_msg", message.getErrorMsg() != null ? message.getErrorMsg() : ""));
        }
    }

    /**
     * Close with default reason (RUNNER_STOPPED).
     */
    public void close() {
        close(CancelReason.RUNNER_STOPPED);
    }

    /**
     * Active cancellation (including queue full, system shutdown, normal finish).
     */
    public void close(CancelReason reason) {
        if (cancelled) {
            return;
        }
        cancelled = true;
        cancelReason = reason;

        if (expireTask != null && !expireTask.isDone()) {
            expireTask.cancel(false);
        }

        cleanupQueue();
        if (reason != CancelReason.FINISH) {
            wakeWaiters(reason);
            logger.info("[Collector:{}] cancelled by close({})", messageId, reason);
        } else {
            logger.info("[Collector:{}] closed (finished)", messageId);
        }
    }

    private DmqResponseMessage poll(double timeoutSeconds) throws Exception {
        DmqResponseMessage message = queue.poll((long) (timeoutSeconds * 1000), TimeUnit.MILLISECONDS);
        if (message == null) {
            throw new TimeoutException(
                    "Collector(" + messageId + ") timed out waiting for remote response from " + receiverId);
        }
        return message;
    }

    private void cleanupQueue() {
        queue.clear();
    }

    private void wakeWaiters(CancelReason reason) {
        this.cancelReason = reason;
        queue.offer(CANCEL_SENTINEL);
    }
}
