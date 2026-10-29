/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.common.concurrent.InterruptSignals;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

/**
 * Invocation-owned cancellation propagated to workflow nodes through session environments.
 * This control is ephemeral and must not be stored in checkpoint state.
 *
 * @since 2026-10-28
 */
public final class WorkflowCancellation {
    private static final String ENV_KEY = "__workflow_cancellation__";

    private final Map<Thread, WorkerRegistration> workers = new HashMap<>();
    private final Set<CompletableFuture<?>> pending = new HashSet<>();
    private boolean isCancelled;

    /**
     * Reports whether cancellation has been requested, independently of node termination.
     *
     * @return whether this invocation has been cancelled
     */
    public synchronized boolean isCancelled() {
        return isCancelled;
    }

    /**
     * Prevents a cancelled invocation from advancing to another node or publishing results.
     *
     * @throws CancellationException if cancellation has been requested
     */
    public synchronized void checkCancelled() {
        if (isCancelled) {
            throw new CancellationException("Workflow invocation was replaced");
        }
    }

    /**
     * Requests cooperative cancellation of registered workers and pending futures.
     * Call {@link #awaitQuiescence(Duration)} before reusing the invocation's checkpoint.
     */
    public synchronized void cancel() {
        isCancelled = true;
        for (WorkerRegistration worker : workers.values()) {
            worker.cancellationSignal.run();
        }
        for (CompletableFuture<?> future : pending) {
            future.cancel(true);
        }
    }

    /**
     * Registers the current thread while it executes an invocation-owned node.
     * Nested registrations are released separately and preserve a preexisting interrupt.
     *
     * @return registration to close on the same thread when the node exits
     * @throws CancellationException if cancellation has already been requested
     */
    public synchronized Registration register() {
        checkCancelled();
        Thread worker = Thread.currentThread();
        boolean isInterrupted = worker.isInterrupted();
        WorkerRegistration registration = workers.computeIfAbsent(worker,
                ignored -> new WorkerRegistration(InterruptSignals.captureCurrentThread()));
        registration.depth++;
        return () -> unregister(worker, isInterrupted);
    }

    private synchronized void unregister(Thread worker, boolean isPreviouslyInterrupted) {
        WorkerRegistration registration = workers.get(worker);
        if (registration == null) {
            return;
        }
        if (registration.depth > 1) {
            registration.depth--;
            return;
        }
        workers.remove(worker);

        // Do not carry this invocation's interrupt into the next pooled task.
        if (isCancelled && !isPreviouslyInterrupted && worker == Thread.currentThread()) {
            Thread.interrupted();
        }
        notifyAll();
    }

    /**
     * Waits for registered node executions to exit; interrupting a worker alone is not sufficient.
     *
     * @param timeout maximum time to wait for workers to unregister
     * @throws IllegalStateException if workers do not exit in time or this wait is interrupted
     */
    public synchronized void awaitQuiescence(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!workers.isEmpty()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new IllegalStateException("Cancelled workflow nodes did not stop within the drain timeout");
            }
            try {
                java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(this, remaining);
            } catch (InterruptedException interrupted) {
                InterruptSignals.restoreCurrentThread();
                throw new IllegalStateException("Interrupted while draining workflow nodes", interrupted);
            }
        }
    }

    /**
     * Adds this control to a session environment while preserving its parent and callbacks.
     *
     * @param original workflow session to wrap
     * @return session carrying this invocation's cancellation control
     */
    public WorkflowSession attach(WorkflowSession original) {
        Map<String, Object> envs = new LinkedHashMap<>();
        if (original.getEnvs() != null) {
            envs.putAll(original.getEnvs());
        }
        envs.put(ENV_KEY, this);
        return new WorkflowSession(original.getParent(), original.getSessionId(), envs) {
            @Override
            public Map<String, Object> getEnvs() {
                return envs;
            }

            @Override
            public com.openjiuwen.core.session.callback.CallbackManager getCallbackManager() {
                return original.getCallbackManager();
            }
        };
    }

    /**
     * Finds the invocation-owned control without creating one for unrelated workflows.
     *
     * @param session workflow or node session; may be {@code null}
     * @return attached control, or {@code null} when the caller does not own a cancellation scope
     */
    @Nullable
    public static WorkflowCancellation from(BaseSession session) {
        Object value = session == null || session.config() == null
                ? null : session.config().getEnv(ENV_KEY);
        return value instanceof WorkflowCancellation control ? control : null;
    }

    /**
     * Checks cancellation when the session belongs to a managed invocation.
     *
     * @param session workflow or node session; may be {@code null}
     * @throws CancellationException if the attached control has been cancelled
     */
    public static void check(BaseSession session) {
        WorkflowCancellation control = from(session);
        if (control != null) {
            control.checkCancelled();
        }
    }

    /**
     * Registers and waits for an asynchronous operation so cancellation can release the waiter.
     *
     * @param stage operation owned by this invocation
     * @param <T> result type
     * @return the operation's result
     * @throws CancellationException if this invocation or the future is cancelled
     * @throws java.util.concurrent.CompletionException if the operation fails
     */
    public <T> T await(CompletionStage<T> stage) {
        CompletableFuture<T> future = stage.toCompletableFuture();
        synchronized (this) {
            if (isCancelled) {
                future.cancel(true);
                checkCancelled();
            }
            pending.add(future);
        }
        try {
            T result = future.join();
            checkCancelled();
            return result;
        } finally {
            synchronized (this) {
                pending.remove(future);
            }
        }
    }

    /**
     * Waits for an operation with cancellation propagation when a control is attached.
     *
     * @param stage asynchronous operation
     * @param session workflow or node session; may be {@code null}
     * @param <T> result type
     * @return the operation's result
     * @throws CancellationException if the invocation or the future is cancelled
     * @throws java.util.concurrent.CompletionException if the operation fails
     */
    public static <T> T await(CompletionStage<T> stage, BaseSession session) {
        WorkflowCancellation control = from(session);
        return control == null ? stage.toCompletableFuture().join() : control.await(stage);
    }

    /**
     * Registers the current node thread when its session has an invocation-owned control.
     *
     * @param session workflow or node session; may be {@code null}
     * @return node registration, or a no-op registration for unmanaged sessions
     * @throws CancellationException if the attached control has already been cancelled
     */
    public static Registration register(BaseSession session) {
        WorkflowCancellation control = from(session);
        return control == null ? () -> { } : control.register();
    }

    private static final class WorkerRegistration {
        private final Runnable cancellationSignal;
        private int depth;

        private WorkerRegistration(Runnable cancellationSignal) {
            this.cancellationSignal = cancellationSignal;
        }
    }

    /**
     * Releases a node's registration on its executing thread when the node exits.
     */
    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        /**
         * Unregisters the node and notifies callers waiting for invocation cleanup.
         */
        @Override
        void close();
    }
}
