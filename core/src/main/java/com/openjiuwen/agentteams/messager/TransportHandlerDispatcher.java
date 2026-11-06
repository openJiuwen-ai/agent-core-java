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
        executor = OpenJiuwenExecutors.newBoundedModulePool("agent-team-handlers-" + threadSuffix,
                4, MAX_PENDING_HANDLERS, true);
    }

    synchronized boolean dispatch(MessagerHandler handler, EventMessage message) {
        if (isClosed || pending.size() >= MAX_PENDING_HANDLERS) {
            return false;
        }
        HandlerTask task = new HandlerTask(handler, message);
        pending.add(task);
        try {
            task.submission = executor.submit(task);
            return true;
        } catch (RejectedExecutionException exception) {
            pending.remove(task);
            return false;
        }
    }

    boolean hasPending() {
        return !pending.isEmpty();
    }

    @Override
    public void close() {
        synchronized (this) {
            isClosed = true;
            pending.forEach(HandlerTask::cancel);
            pending.clear();
        }
        OpenJiuwenExecutors.shutdownNow(executor);
    }

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
            try {
                response = Objects.requireNonNull(handler.handle(message), "Handler returned no completion future");
                if (isCancelled) {
                    response.cancel(true);
                }
                response.whenComplete((ignored, failure) -> finish(failure));
            } catch (RuntimeException exception) {
                finish(exception);
            }
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
