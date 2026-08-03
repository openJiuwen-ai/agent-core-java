/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription;

import com.openjiuwen.core.common.BackgroundTask;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.RunnerTermination;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.ResultType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Collects responses for one distributed-runner request and supports cancellation and timeouts.
 *
 * <p>Mirrors Python's {@code ResponseCollector} in
 * {@code openjiuwen/core/runner/drunner/dmessage_queue/dsubscription/response_collector.py}.</p>
 */
public class ResponseCollector {

    public static final int MAX_QUEUE_SIZE = 10_000;
    private static final double DEFAULT_TTL_SECONDS = 30.0d;
    private static final String TASK_GROUP = "runner.dmq.response_collector";
    private static final LoggerProtocol LOGGER = Loggers.RUNNER;
    private static final ScheduledExecutorService EXPIRER = Executors.newSingleThreadScheduledExecutor(
            new ResponseCollectorThreadFactory());

    private final String messageId;
    private final String receiverId;
    private final String requestId;
    private final double ttl;
    private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean expired = new AtomicBoolean(false);

    private volatile BackgroundTask expireTask;
    private volatile ScheduledFuture<?> expireFuture;
    private volatile CompletableFuture<Void> expireCompletion;

    public ResponseCollector(String messageId, String receiverId) {
        this(messageId, receiverId, null, null);
    }

    public ResponseCollector(String messageId, String receiverId, String requestId, Double ttl) {
        this.messageId = messageId;
        this.receiverId = receiverId;
        this.requestId = requestId;
        this.ttl = ttl == null || ttl == 0.0d ? DEFAULT_TTL_SECONDS : ttl;
    }

    public synchronized CompletableFuture<Void> start() {
        if (expireTask != null) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> completion = new CompletableFuture<>();
        long delayMillis = secondsToMillis(ttl);
        ScheduledFuture<?> scheduled = EXPIRER.schedule(() -> {
            try {
                expireAfterTtl();
            } finally {
                completion.complete(null);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
        this.expireCompletion = completion;
        this.expireFuture = scheduled;
        this.expireTask = BackgroundTask.fromAsyncioTask(completion, TASK_GROUP);
        return CompletableFuture.completedFuture(null);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public boolean isExpired() {
        return expired.get();
    }

    public boolean isActive() {
        return !cancelled.get() && !expired.get();
    }

    public CompletableFuture<Void> putMessage(DmqResponseMessage message) {
        if (!isActive()) {
            LOGGER.warning("[Collector:{}] inactive, discard message", messageId);
            return CompletableFuture.completedFuture(null);
        }

        if (queue.remainingCapacity() == 0 || !queue.offer(message)) {
            LOGGER.warning("[Collector:{}] queue full({}), auto-cancelled", messageId, MAX_QUEUE_SIZE);
            return close(CancelReason.QUEUE_FULL);
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Object> result() {
        return result(null);
    }

    public CompletableFuture<Object> result(Double timeout) {
        return CompletableFuture.supplyAsync(() -> {
            double effectiveTimeout = effectiveTimeout(timeout);
            try {
                ensureCanWait();
                Object message = pollMessage(effectiveTimeout);
                checkMessage(message);
                return ((DmqResponseMessage) message).getBody();
            } catch (TimeoutException error) {
                expired.set(true);
                cleanupQueue();
                LOGGER.warning("[Collector:{}] result timeout ({})", messageId, formatSeconds(effectiveTimeout));
                throw completion(new TimeoutException(
                        "Collector(" + messageId + ") timeout waiting for result"));
            } finally {
                close(CancelReason.FINISH).join();
            }
        });
    }

    public CompletableFuture<List<Object>> stream() {
        return stream(null);
    }

    public CompletableFuture<List<Object>> stream(Double timeout) {
        return CompletableFuture.supplyAsync(() -> {
            double effectiveTimeout = effectiveTimeout(timeout);
            List<Object> chunks = new ArrayList<>();
            try {
                while (true) {
                    Object message = pollMessage(effectiveTimeout);
                    LOGGER.debug("[Collector:{}] stream get message {}", messageId, message);
                    checkMessage(message);
                    DmqResponseMessage response = (DmqResponseMessage) message;
                    if (response.isLastChunk()) {
                        break;
                    }
                    chunks.add(response.getBody());
                }
                return chunks;
            } catch (TimeoutException error) {
                expired.set(true);
                LOGGER.warning("[Collector:{}] stream timeout ({})", messageId, formatSeconds(effectiveTimeout));
                throw completion(new TimeoutException("Collector(" + messageId + ") stream timeout"));
            } finally {
                close(CancelReason.FINISH).join();
            }
        });
    }

    public CompletableFuture<Void> close() {
        return close(CancelReason.RUNNER_STOPPED);
    }

    public CompletableFuture<Void> close(CancelReason reason) {
        if (!cancelled.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }

        ScheduledFuture<?> scheduled = expireFuture;
        expireFuture = null;
        if (scheduled != null) {
            scheduled.cancel(true);
        }
        CompletableFuture<Void> completion = expireCompletion;
        expireCompletion = null;
        if (completion != null) {
            completion.complete(null);
        }

        BackgroundTask task = expireTask;
        expireTask = null;
        CompletableFuture<Void> cancelFuture = task == null
                ? CompletableFuture.completedFuture(null)
                : task.cancel("response_collector_closed", 1.0d).exceptionally(error -> null);
        return cancelFuture.thenRun(() -> {
            cleanupQueue();
            if (reason != CancelReason.FINISH) {
                wakeWaiters(new CancelEvent(reason));
                LOGGER.info("[Collector:{}] cancelled by close({})", messageId, reason);
            }
            LOGGER.info("[Collector:{}] cancelled by finished", messageId);
        });
    }

    public String getMessageId() {
        return messageId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public String getRequestId() {
        return requestId;
    }

    public double getTtl() {
        return ttl;
    }

    public int getQueueSize() {
        return queue.size();
    }

    private void expireAfterTtl() {
        if (cancelled.get()) {
            return;
        }
        expired.set(true);
        cleanupQueue();
        LOGGER.warning("[Collector:{}] expired after {}s", messageId, formatSeconds(ttl));
        wakeWaiters(new CancelEvent(CancelReason.TTL_EXPIRE));
    }

    private void ensureCanWait() {
        if (cancelled.get()) {
            throw new CancellationException(
                    "Collector(" + messageId + ") was cancelled before request send");
        }
        if (expired.get()) {
            throw completion(new TimeoutException("Collector(" + messageId + ") expired"));
        }
    }

    private Object pollMessage(double timeoutSeconds) throws TimeoutException {
        try {
            Object message = queue.poll(secondsToMillis(timeoutSeconds), TimeUnit.MILLISECONDS);
            if (message == null) {
                throw new TimeoutException("Collector(" + messageId + ") timeout");
            }
            return message;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Collector(" + messageId + ") wait interrupted");
        }
    }

    private void checkMessage(Object message) throws TimeoutException {
        if (message instanceof CancelEvent event) {
            LOGGER.info("[Collector:{}] rev CancelEvent stream cancelled by {}", messageId, event.reason());
            if (event.reason() == CancelReason.TTL_EXPIRE) {
                throw new TimeoutException("Collector(" + messageId + ") timeout");
            }
            if (event.reason() == CancelReason.QUEUE_FULL) {
                throw new CancellationException("Collector(" + messageId + ") queue full");
            }
            throw new RunnerTermination(
                    "Collector(" + messageId + ") was cancelled",
                    StatusCode.RUNNER_TERMINATION_ERROR);
        }

        DmqResponseMessage response = (DmqResponseMessage) message;
        if (response.getResultType() == ResultType.ERROR) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("message_id", messageId);
            params.put("process_id", receiverId);
            params.put("error_code", response.getErrorCode());
            params.put("error_msg", response.getErrorMsg());
            throw ErrorHelper.buildError(
                    StatusCode.REMOTE_AGENT_RESPONSE_PROCESS_ERROR,
                    null,
                    null,
                    null,
                    params);
        }
    }

    private void cleanupQueue() {
        queue.clear();
    }

    private void wakeWaiters(CancelEvent cancelEvent) {
        queue.offer(cancelEvent);
    }

    private double effectiveTimeout(Double timeout) {
        return timeout == null || timeout == 0.0d ? ttl : timeout;
    }

    private static long secondsToMillis(double seconds) {
        return Math.max(0L, Math.round(seconds * 1000.0d));
    }

    private static String formatSeconds(double seconds) {
        return String.format("%.1f", seconds);
    }

    private static CompletionException completion(Throwable error) {
        return error instanceof CompletionException completion ? completion : new CompletionException(error);
    }

    private static final class ResponseCollectorThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "response-collector-expirer");
            thread.setDaemon(true);
            return thread;
        }
    }
}
