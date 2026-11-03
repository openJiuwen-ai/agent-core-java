/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Handle for background tasks created through the task manager when possible.
 *
 * <p>Mirrors Python's {@code BackgroundTask} class in
 * {@code openjiuwen/core/common/background_tasks.py}.</p>
 */
public class BackgroundTask {

    private final String group;
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private volatile ManagerTask managerTask;
    private volatile CompletableFuture<?> asyncTask;

    public BackgroundTask(String group) {
        this.group = group;
    }

    public static BackgroundTask fromAsyncioTask(CompletionStage<?> task, String group) {
        BackgroundTask handle = new BackgroundTask(group);
        handle.asyncTask = toCompletableFuture(task);
        handle.ready.complete(null);
        return handle;
    }

    public void setManagerTask(ManagerTask task) {
        this.managerTask = Objects.requireNonNull(task, "task must not be null");
        this.ready.complete(null);
    }

    void setAsyncTask(CompletionStage<?> task) {
        this.asyncTask = toCompletableFuture(task);
        this.ready.complete(null);
    }

    public String getGroup() {
        return group;
    }

    public boolean done() {
        if (managerTask != null) {
            return managerTask.isTerminal();
        }
        if (asyncTask != null) {
            return asyncTask.isDone();
        }
        return false;
    }

    public boolean isDone() {
        return done();
    }

    public CompletableFuture<Object> waitForCompletion() {
        return awaitResult();
    }

    public CompletableFuture<Object> awaitResult() {
        return ready.thenCompose(unused -> {
            if (managerTask != null) {
                return managerTask.waitForCompletion();
            }
            if (asyncTask != null) {
                return asyncTask.thenApply(result -> result);
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> cancel() {
        return cancel("background_task_cancelled", Duration.ofSeconds(1));
    }

    public CompletableFuture<Void> cancel(String reason, double timeoutSeconds) {
        long timeoutMillis = Math.max(0L, Math.round(timeoutSeconds * 1000.0d));
        return cancel(reason, Duration.ofMillis(timeoutMillis));
    }

    public CompletableFuture<Void> cancel(String reason, Duration timeout) {
        Duration effectiveTimeout = timeout == null ? Duration.ofSeconds(1) : timeout;
        return ready.thenCompose(unused -> {
            if (managerTask != null) {
                return managerTask.cancel(reason)
                        .exceptionally(error -> null)
                        .thenCompose(ignored -> managerTask.waitForCompletion()
                                .completeOnTimeout(null, effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS)
                                .handle((result, error) -> null));
            }
            if (asyncTask == null) {
                return CompletableFuture.completedFuture(null);
            }
            if (!asyncTask.isDone()) {
                asyncTask.cancel(true);
            }
            return asyncTask.completeOnTimeout(null, effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .handle((result, error) -> null);
        });
    }

    private static CompletableFuture<?> toCompletableFuture(CompletionStage<?> task) {
        if (task == null) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            return task.toCompletableFuture();
        } catch (RuntimeException e) {
            CompletableFuture<Object> bridged = new CompletableFuture<>();
            task.whenComplete((result, error) -> {
                if (error != null) {
                    bridged.completeExceptionally(error);
                    return;
                }
                bridged.complete(result);
            });
            return bridged;
        }
    }

    /**
     * Task-manager task surface used by {@code BackgroundTask}.
     *
     * <p>Mirrors Python's task object usage in
     * {@code openjiuwen/core/common/background_tasks.py}.</p>
     */
    public interface ManagerTask {
        boolean isTerminal();

        CompletableFuture<Object> waitForCompletion();

        CompletionStage<Void> cancel(String reason);
    }
}
