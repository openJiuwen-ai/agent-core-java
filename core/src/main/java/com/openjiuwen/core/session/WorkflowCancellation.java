/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Invocation-owned cancellation propagated to workflow nodes through session environments.
 * This control is ephemeral and must not be stored in checkpoint state.
 */
public final class WorkflowCancellation {
    private static final String ENV_KEY = "__workflow_cancellation__";
    private final Map<Thread, Integer> workers = new HashMap<>();
    private final Set<CompletableFuture<?>> pending = new HashSet<>();
    private boolean cancelled;

    public synchronized boolean isCancelled() {
        return cancelled;
    }

    public synchronized void checkCancelled() {
        if (cancelled) {
            throw new CancellationException("Workflow invocation was replaced");
        }
    }

    public synchronized void cancel() {
        cancelled = true;
        for (Thread worker : workers.keySet()) {
            worker.interrupt();
        }
        for (CompletableFuture<?> future : pending) {
            future.cancel(true);
        }
    }

    public synchronized Registration register() {
        checkCancelled();
        Thread worker = Thread.currentThread();
        boolean interrupted = worker.isInterrupted();
        workers.merge(worker, 1, Integer::sum);
        return () -> unregister(worker, interrupted);
    }

    private synchronized void unregister(Thread worker, boolean previouslyInterrupted) {
        Integer depth = workers.get(worker);
        if (depth == null) {
            return;
        }
        if (depth > 1) {
            workers.put(worker, depth - 1);
            return;
        }
        workers.remove(worker);
        // Do not carry this invocation's interrupt into the next pooled task.
        if (cancelled && !previouslyInterrupted && worker == Thread.currentThread()) {
            Thread.interrupted();
        }
        notifyAll();
    }

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
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while draining workflow nodes", interrupted);
            }
        }
    }

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

    public static WorkflowCancellation from(BaseSession session) {
        Object value = session == null || session.config() == null
                ? null : session.config().getEnv(ENV_KEY);
        return value instanceof WorkflowCancellation control ? control : null;
    }

    public static void check(BaseSession session) {
        WorkflowCancellation control = from(session);
        if (control != null) {
            control.checkCancelled();
        }
    }

    public <T> T await(CompletionStage<T> stage) {
        CompletableFuture<T> future = stage.toCompletableFuture();
        synchronized (this) {
            if (cancelled) {
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

    public static <T> T await(CompletionStage<T> stage, BaseSession session) {
        WorkflowCancellation control = from(session);
        return control == null ? stage.toCompletableFuture().join() : control.await(stage);
    }

    public static Registration register(BaseSession session) {
        WorkflowCancellation control = from(session);
        return control == null ? () -> { } : control.register();
    }

    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }
}
