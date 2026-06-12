/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Background task creation helpers.
 *
 * <p>Mirrors Python's module functions in
 * {@code openjiuwen/core/common/background_tasks.py}.</p>
 */
public final class BackgroundTasks {

    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newCachedThreadPool();
    private static volatile TaskGroupProvider taskGroupProvider;
    private static volatile ManagerTaskCreator managerTaskCreator;

    private BackgroundTasks() {
    }

    public static void setLoadedTaskGroupProvider(TaskGroupProvider provider) {
        taskGroupProvider = provider;
    }

    public static void setLoadedManagerTaskCreator(ManagerTaskCreator creator) {
        managerTaskCreator = creator;
    }

    public static void resetLoadedTaskManager() {
        taskGroupProvider = null;
        managerTaskCreator = null;
    }

    public static TaskGroup getLoadedTaskGroup() {
        TaskGroupProvider provider = taskGroupProvider;
        return provider == null ? null : provider.getTaskGroup();
    }

    public static ManagerTaskCreator getLoadedCreateTask() {
        return managerTaskCreator;
    }

    public static CompletableFuture<BackgroundTask> createBackgroundTask(
            Supplier<? extends CompletionStage<?>> coroutine,
            String name,
            String group) {
        return createBackgroundTask(coroutine, name, group, true);
    }

    public static CompletableFuture<BackgroundTask> createBackgroundTask(
            Supplier<? extends CompletionStage<?>> coroutine,
            String name,
            String group,
            boolean fallbackToAsyncio) {
        Objects.requireNonNull(coroutine, "coroutine must not be null");
        TaskGroup taskGroup = getLoadedTaskGroup();
        if (taskGroup != null) {
            ManagerTaskCreator createTask = getLoadedCreateTask();
            if (createTask != null) {
                BackgroundTask handle = new BackgroundTask(group);
                return createTask.create(coroutine, name, group, true)
                        .thenApply(task -> {
                            handle.setManagerTask(task);
                            return handle;
                        })
                        .toCompletableFuture();
            }
        }
        if (!fallbackToAsyncio) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("task manager root task group is not available"));
        }
        return CompletableFuture.completedFuture(
                BackgroundTask.fromAsyncioTask(scheduleCoroutine(coroutine), group));
    }

    public static BackgroundTask startBackgroundTask(
            Supplier<? extends CompletionStage<?>> coroutine,
            String name,
            String group) {
        return startBackgroundTask(coroutine, name, group, true);
    }

    public static BackgroundTask startBackgroundTask(
            Supplier<? extends CompletionStage<?>> coroutine,
            String name,
            String group,
            boolean fallbackToAsyncio) {
        Objects.requireNonNull(coroutine, "coroutine must not be null");
        TaskGroup taskGroup = getLoadedTaskGroup();
        if (taskGroup == null) {
            if (!fallbackToAsyncio) {
                throw new IllegalStateException("task manager root task group is not available");
            }
            return BackgroundTask.fromAsyncioTask(scheduleCoroutine(coroutine), group);
        }

        ManagerTaskCreator createTask = getLoadedCreateTask();
        if (createTask == null) {
            if (!fallbackToAsyncio) {
                throw new IllegalStateException("task manager root task group is active but manager is not loaded");
            }
            return BackgroundTask.fromAsyncioTask(scheduleCoroutine(coroutine), group);
        }

        BackgroundTask handle = new BackgroundTask(group);
        taskGroup.startSoon(() -> createTask.create(coroutine, name, group, true)
                .whenComplete((task, error) -> {
                    if (error != null) {
                        handle.setAsyncTask(CompletableFuture.failedFuture(error));
                        return;
                    }
                    handle.setManagerTask(task);
                }));
        return handle;
    }

    private static CompletableFuture<Object> scheduleCoroutine(Supplier<? extends CompletionStage<?>> coroutine) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return coroutine.get();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, DEFAULT_EXECUTOR).thenCompose(stage -> {
            if (stage == null) {
                return CompletableFuture.completedFuture(null);
            }
            return stage.toCompletableFuture().thenApply(result -> result);
        });
    }

    /**
     * Task-group boundary used by {@code startBackgroundTask}.
     *
     * <p>Mirrors Python's loaded task group usage in
     * {@code openjiuwen/core/common/background_tasks.py}.</p>
     */
    @FunctionalInterface
    public interface TaskGroup {
        void startSoon(Runnable runnable);
    }

    /**
     * Loaded task-group provider.
     *
     * <p>Mirrors Python's module lookup in
     * {@code openjiuwen/core/common/background_tasks.py}.</p>
     */
    @FunctionalInterface
    public interface TaskGroupProvider {
        TaskGroup getTaskGroup();
    }

    /**
     * Loaded manager create-task callable.
     *
     * <p>Mirrors Python's {@code manager.create_task} usage in
     * {@code openjiuwen/core/common/background_tasks.py}.</p>
     */
    @FunctionalInterface
    public interface ManagerTaskCreator {
        CompletionStage<BackgroundTask.ManagerTask> create(
                Supplier<? extends CompletionStage<?>> coroutine,
                String name,
                String group,
                boolean catchExceptions);
    }
}
