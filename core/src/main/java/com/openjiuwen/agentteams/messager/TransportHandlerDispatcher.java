/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.messager;

import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Keeps asynchronous handlers off the socket owner and bounds their lifetime to one transport generation.
 *
 * @since 2026-11-06
 */
final class TransportHandlerDispatcher implements AutoCloseable {
    private static final int MAX_PENDING_HANDLERS = 256;

    private final ExecutorService executor;

    private final Set<HandlerTask> pending = ConcurrentHashMap.newKeySet();

    private boolean isClosed;

    TransportHandlerDispatcher(String threadSuffix) {
        this(OpenJiuwenExecutors.newBoundedModulePool("agent-team-handlers-" + threadSuffix,
                4, MAX_PENDING_HANDLERS, true));
    }

    TransportHandlerDispatcher(ExecutorService executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    synchronized DispatchResult dispatch(MessagerHandler handler, EventMessage message) {
        if (isClosed) {
            return DispatchResult.CLOSED;
        }
        if (pending.size() >= MAX_PENDING_HANDLERS) {
            return DispatchResult.CAPACITY_EXHAUSTED;
        }
        HandlerTask task = new HandlerTask(handler, message);
        pending.add(task);
        try {
            task.submission = executor.submit(task);
            return DispatchResult.ACCEPTED;
        } catch (RejectedExecutionException exception) {
            pending.remove(task);
            return DispatchResult.SUBMISSION_REJECTED;
        }
    }

    boolean hasPending() {
        return !pending.isEmpty();
    }

    @Override
    public void close() {
        close(System.nanoTime());
    }

    void close(long deadlineNanos) {
        synchronized (this) {
            isClosed = true;
            pending.forEach(HandlerTask::cancel);
            pending.clear();
        }

        // The managed executor unregisters itself on termination; do not add a second shutdown budget.
        executor.shutdownNow();
        try {
            long remainingNanos = Math.max(0L, deadlineNanos - System.nanoTime());
            if (!executor.awaitTermination(remainingNanos, TimeUnit.NANOSECONDS)) {
                Loggers.AGENT.warn("PyZmq handler shutdown deadline reached; interrupted workers may still be exiting");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** Outcome of admitting a handler to this transport generation. */
    enum DispatchResult {ACCEPTED, CLOSED, CAPACITY_EXHAUSTED, SUBMISSION_REJECTED}

    private final class HandlerTask implements Runnable {
        private final MessagerHandler handler;

        private final EventMessage message;

        private volatile Future<?> submission;

        private volatile CompletableFuture<Void> response;

        private volatile boolean isCancelled;

        private HandlerTask(MessagerHandler handler, EventMessage message) {
            this.handler = handler;
            this.message = message;
        }

        @Override
        public void run() {
            if (isCancelled) {
                return;
            }
            CompletableFuture.completedFuture(message)
                    .thenApply(event -> Objects.requireNonNull(handler.handle(event),
                            "Handler returned no completion future"))
                    .whenComplete(this::observeResponse);
        }

        private void observeResponse(CompletableFuture<Void> result, Throwable failure) {
            if (failure != null) {
                finish(failure);
                return;
            }
            response = result;
            if (isCancelled) {
                result.cancel(true);
            }
            result.whenComplete((ignored, responseFailure) -> finish(responseFailure));
        }

        private void finish(Throwable failure) {
            pending.remove(this);
            if (failure != null && !isCancelled) {
                Loggers.AGENT.warn("PyZmq handler execution failed", failure);
            }
        }

        private void cancel() {
            isCancelled = true;
            Future<?> currentSubmission = submission;
            CompletableFuture<Void> currentResponse = response;
            if (currentSubmission != null) {
                currentSubmission.cancel(true);
            }
            if (currentResponse != null) {
                currentResponse.cancel(true);
            }
        }
    }
}
