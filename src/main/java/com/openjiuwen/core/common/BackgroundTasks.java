/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Background task management.
 * <p>
 * Handle for background tasks created through task_manager when possible.
 * <p>
 * Mirrors Python's {@code BackgroundTask} and related functions in
 * {@code openjiuwen.core.common.background_tasks}.
 */
public class BackgroundTasks {

    private static final Logger logger = Logger.getLogger(BackgroundTasks.class.getName());

    private static final ExecutorService defaultExecutor = Executors.newCachedThreadPool();

    /**
     * Handle for background tasks.
     */
    public static class BackgroundTask {
        private final String group;
        private final AtomicReference<CompletableFuture<?>> managerTask;
        private final AtomicReference<CompletableFuture<?>> asyncioTask;
        private final AtomicBoolean ready;

        public BackgroundTask(String group) {
            this.group = group;
            this.managerTask = new AtomicReference<>();
            this.asyncioTask = new AtomicReference<>();
            this.ready = new AtomicBoolean(false);
        }

        public static BackgroundTask fromAsyncioTask(CompletableFuture<?> task, String group) {
            BackgroundTask handle = new BackgroundTask(group);
            handle.asyncioTask.set(task);
            handle.ready.set(true);
            return handle;
        }

        public void setManagerTask(CompletableFuture<?> task) {
            this.managerTask.set(task);
            this.ready.set(true);
        }

        public String getGroup() {
            return group;
        }

        public boolean isDone() {
            CompletableFuture<?> task = managerTask.get();
            if (task != null) {
                return task.isDone();
            }
            task = asyncioTask.get();
            if (task != null) {
                return task.isDone();
            }
            return false;
        }

        public CompletableFuture<?> waitForCompletion() {
            return CompletableFuture.supplyAsync(() -> {
                while (!ready.get()) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
                CompletableFuture<?> task = managerTask.get();
                if (task != null) {
                    return task.join();
                }
                task = asyncioTask.get();
                if (task != null) {
                    return task.join();
                }
                return null;
            });
        }

        public CompletableFuture<Void> cancel(String reason, double timeout) {
            return CompletableFuture.runAsync(() -> {
                while (!ready.get()) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                CompletableFuture<?> task = managerTask.get();
                if (task != null) {
                    task.cancel(true);
                    return;
                }

                task = asyncioTask.get();
                if (task != null && !task.isDone()) {
                    task.cancel(true);
                }
            });
        }
    }

    /**
     * Create a background task via task_manager when a task group is active.
     *
     * @param runnable           The task to run
     * @param name               Task name
     * @param group              Task group
     * @param fallbackToAsyncio  Whether to fallback to asyncio if no task group
     * @return BackgroundTask handle
     */
    public static BackgroundTask createBackgroundTask(
            Runnable runnable,
            String name,
            String group,
            boolean fallbackToAsyncio) {

        // Check if task manager is available
        if (getTaskGroup() != null) {
            CompletableFuture<?> task = createManagerTask(runnable, name, group);
            BackgroundTask handle = new BackgroundTask(group);
            handle.setManagerTask(task);
            return handle;
        }

        if (!fallbackToAsyncio) {
            throw new RuntimeException("task manager root task group is not available");
        }

        return BackgroundTask.fromAsyncioTask(
            CompletableFuture.runAsync(runnable, defaultExecutor),
            group
        );
    }

    /**
     * Start a background task from synchronous lifecycle methods.
     */
    public static BackgroundTask startBackgroundTask(
            Runnable runnable,
            String name,
            String group,
            boolean fallbackToAsyncio) {

        Object tg = getTaskGroup();
        if (tg == null) {
            if (!fallbackToAsyncio) {
                throw new RuntimeException("task manager root task group is not available");
            }
            return BackgroundTask.fromAsyncioTask(
                CompletableFuture.runAsync(runnable, defaultExecutor),
                group
            );
        }

        BackgroundTask handle = new BackgroundTask(group);
        CompletableFuture.runAsync(() -> {
            CompletableFuture<?> task = createManagerTask(runnable, name, group);
            handle.setManagerTask(task);
        }, defaultExecutor);
        return handle;
    }

    // ── Helper methods ───────────────────────────────────────

    private static Object getTaskGroup() {
        // Placeholder: check if task group is active
        return null;
    }

    private static CompletableFuture<?> createManagerTask(Runnable runnable, String name, String group) {
        // Placeholder: create task via task manager
        return CompletableFuture.runAsync(runnable, defaultExecutor);
    }

    /**
     * Shutdown the default executor.
     */
    public static void shutdown() {
        defaultExecutor.shutdown();
    }
}