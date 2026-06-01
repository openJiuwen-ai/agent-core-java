/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Task group context for structured concurrency.
 * <p>
 * Mirrors Python's anyio.abc.TaskGroup for managing concurrent tasks
 * within a scoped context.
 * <p>
 * In Java, this is implemented using CompletableFuture and ExecutorService.
 */
public class TaskGroupContext implements AutoCloseable {

    private final ExecutorService executor;
    private final List<CompletableFuture<?>> tasks = new ArrayList<>();
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final String groupId;

    /**
     * Create a new task group context.
     *
     * @param executor The executor service to use for task execution
     */
    public TaskGroupContext(ExecutorService executor) {
        this.executor = executor;
        this.groupId = java.util.UUID.randomUUID().toString();
    }

    /**
     * Get the group ID.
     *
     * @return Unique identifier for this task group
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * Check if the group is still active.
     *
     * @return true if tasks can still be added
     */
    public boolean isActive() {
        return active.get();
    }

    /**
     * Start a new task in this group.
     *
     * @param task The task to execute
     * @param <T> The result type
     * @return CompletableFuture for the task
     */
    public <T> CompletableFuture<T> startTask(Supplier<CompletableFuture<T>> task) {
        if (!active.get()) {
            throw new IllegalStateException("TaskGroup is not active");
        }
        CompletableFuture<T> future = task.get();
        track(future);
        return future;
    }

    /**
     * Start a runnable task.
     *
     * @param runnable The runnable to execute
     * @return CompletableFuture for the task
     */
    public CompletableFuture<Void> startTask(Runnable runnable) {
        if (!active.get()) {
            throw new IllegalStateException("TaskGroup is not active");
        }
        CompletableFuture<Void> future = CompletableFuture.runAsync(runnable, executor);
        track(future);
        return future;
    }

    public void track(CompletableFuture<?> future) {
        if (!active.get()) {
            throw new IllegalStateException("TaskGroup is not active");
        }
        tasks.add(future);
    }

    /**
     * Wait for all tasks to complete.
     *
     * @return CompletableFuture that completes when all tasks are done
     */
    public CompletableFuture<Void> waitAll() {
        @SuppressWarnings("unchecked")
        CompletableFuture<?>[] futures = tasks.toArray(new CompletableFuture[0]);
        return CompletableFuture.allOf(futures);
    }

    /**
     * Get the number of tasks in this group.
     *
     * @return Task count
     */
    public int getTaskCount() {
        return tasks.size();
    }

    /**
     * Close the task group and wait for all tasks to complete.
     */
    @Override
    public void close() {
        active.set(false);
        for (CompletableFuture<?> task : tasks) {
            try {
                task.join();
            } catch (java.util.concurrent.CancellationException | java.util.concurrent.CompletionException ignored) {
                // Python's task-group tests assert final task state; Java test cleanup should not mask it.
            }
        }
        TaskManager.resetTaskGroup();
    }
}
