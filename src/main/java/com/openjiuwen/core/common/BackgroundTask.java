/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common;

import com.openjiuwen.core.common.task_manager.Task;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Handle for background tasks created through task manager when possible.
 * <p>
 * Mirrors Python's {@code BackgroundTask} class from
 * <code>common/background_tasks.py</code>.
 *
 * <p>Provides a unified handle for background tasks that may be managed
 * either by the task manager (if available) or by standard Java executor.
 */
public class BackgroundTask {

    private final String group;
    private Task managerTask;
    private Future<?> executorTask;
    private final CompletableFuture<Void> readyFuture = new CompletableFuture<>();

    private BackgroundTask(String group) {
        this.group = group;
    }

    /**
     * Create a BackgroundTask from a standard Java Future.
     *
     * @param task the executor task
     * @param group the task group name
     * @return the BackgroundTask handle
     */
    public static BackgroundTask fromExecutorTask(Future<?> task, String group) {
        BackgroundTask handle = new BackgroundTask(group);
        handle.executorTask = task;
        handle.readyFuture.complete(null);
        return handle;
    }

    /**
     * Set the manager task for this background task.
     *
     * @param task the Task from task manager
     */
    public void setManagerTask(Task task) {
        this.managerTask = task;
        readyFuture.complete(null);
    }

    public String getGroup() {
        return group;
    }

    public Task getManagerTask() {
        return managerTask;
    }

    public Future<?> getExecutorTask() {
        return executorTask;
    }

    /**
     * Check if the task is done.
     *
     * @return true if the task has completed
     */
    public boolean isDone() {
        if (managerTask != null) {
            return managerTask.isTerminal();
        }
        if (executorTask != null) {
            return executorTask.isDone();
        }
        return false;
    }

    /**
     * Wait for the task to complete and get its result.
     *
     * @return a CompletableFuture containing the task result
     */
    public CompletableFuture<Object> waitForCompletion() {
        return readyFuture.thenCompose(v -> {
            if (managerTask != null) {
                return managerTask.getDoneFuture();
            }
            if (executorTask != null) {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        return executorTask.get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * Cancel the background task with a timeout.
     *
     * @param reason the cancellation reason
     * @param timeoutSeconds the timeout in seconds
     * @return a CompletableFuture that completes when cancel is done
     */
    public CompletableFuture<Void> cancel(String reason, double timeoutSeconds) {
        return readyFuture.thenCompose(v -> {
            if (managerTask != null) {
                managerTask.cancel("background_task", reason);
                return managerTask.getDoneFuture()
                    .completeOnTimeout(null, (long) timeoutSeconds, TimeUnit.SECONDS)
                    .thenApply(r -> null);
            }

            if (executorTask == null) {
                return CompletableFuture.completedFuture(null);
            }

            if (!executorTask.isDone()) {
                executorTask.cancel(true);
            }

            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * Default cancel with standard reason and timeout.
     *
     * @return a CompletableFuture that completes when cancel is done
     */
    public CompletableFuture<Void> cancel() {
        return cancel("background_task_cancelled", 1.0);
    }
}