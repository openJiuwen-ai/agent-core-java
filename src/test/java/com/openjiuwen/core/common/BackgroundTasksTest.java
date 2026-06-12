/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code background_tasks.py} behavior in
 * {@code openjiuwen/core/common/background_tasks.py}.
 */
class BackgroundTasksTest {

    @AfterEach
    void tearDown() {
        BackgroundTasks.resetLoadedTaskManager();
    }

    @Test
    void createBackgroundTaskFallsBackToAsyncTaskWhenNoTaskGroupIsLoaded() {
        BackgroundTask handle = BackgroundTasks.createBackgroundTask(
                completed("ok"), "sync", "workers", true).join();

        assertThat(handle.getGroup()).isEqualTo("workers");
        assertThat(handle.awaitResult().join()).isEqualTo("ok");
        assertThat(handle.done()).isTrue();
    }

    @Test
    void createBackgroundTaskFailsWhenFallbackDisabledAndNoTaskGroupExists() {
        CompletableFuture<BackgroundTask> handle = BackgroundTasks.createBackgroundTask(
                completed("unused"), "sync", "workers", false);

        assertThatThrownBy(handle::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("task manager root task group is not available");
    }

    @Test
    void createBackgroundTaskUsesLoadedManagerCreatorWhenTaskGroupIsActive() {
        TestManagerTask managerTask = new TestManagerTask("managed");
        BackgroundTasks.setLoadedTaskGroupProvider(() -> Runnable::run);
        BackgroundTasks.setLoadedManagerTaskCreator((coroutine, name, group, catchExceptions) -> {
            assertThat(name).isEqualTo("job");
            assertThat(group).isEqualTo("managed-group");
            assertThat(catchExceptions).isTrue();
            return CompletableFuture.completedFuture(managerTask);
        });

        BackgroundTask handle = BackgroundTasks.createBackgroundTask(
                completed("ignored"), "job", "managed-group", true).join();

        assertThat(handle.getGroup()).isEqualTo("managed-group");
        assertThat(handle.done()).isFalse();
        managerTask.complete();
        assertThat(handle.awaitResult().join()).isEqualTo("managed");
        assertThat(handle.done()).isTrue();
    }

    @Test
    void startBackgroundTaskFallsBackWhenManagerModuleIsNotLoaded() {
        BackgroundTasks.setLoadedTaskGroupProvider(() -> Runnable::run);

        BackgroundTask handle = BackgroundTasks.startBackgroundTask(
                completed("fallback"), "job", "group", true);

        assertThat(handle.awaitResult().join()).isEqualTo("fallback");
        assertThat(handle.done()).isTrue();
    }

    @Test
    void startBackgroundTaskFailsWhenTaskGroupActiveButManagerMissingAndFallbackDisabled() {
        BackgroundTasks.setLoadedTaskGroupProvider(() -> Runnable::run);

        assertThatThrownBy(() -> BackgroundTasks.startBackgroundTask(
                completed("unused"), "job", "group", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("task manager root task group is active but manager is not loaded");
    }

    @Test
    void startBackgroundTaskSchedulesManagerCreationOnTaskGroup() {
        TestTaskGroup taskGroup = new TestTaskGroup();
        TestManagerTask managerTask = new TestManagerTask("created");
        BackgroundTasks.setLoadedTaskGroupProvider(() -> taskGroup);
        BackgroundTasks.setLoadedManagerTaskCreator((coroutine, name, group, catchExceptions) ->
                CompletableFuture.completedFuture(managerTask));

        BackgroundTask handle = BackgroundTasks.startBackgroundTask(
                completed("unused"), "job", "group", true);

        assertThat(taskGroup.scheduled.get()).isNotNull();
        taskGroup.scheduled.get().run();
        managerTask.complete();
        assertThat(handle.awaitResult().join()).isEqualTo("created");
    }

    @Test
    void cancelDelegatesToManagerTaskAndWaitsWithinTimeout() {
        TestManagerTask managerTask = new TestManagerTask("done");
        BackgroundTask handle = new BackgroundTask("group");
        handle.setManagerTask(managerTask);

        handle.cancel("background_task_cancelled", Duration.ofMillis(50)).join();

        assertThat(managerTask.cancelled.get()).isTrue();
        assertThat(managerTask.cancelReason.get()).isEqualTo("background_task_cancelled");
    }

    private static Supplier<CompletableFuture<Object>> completed(Object value) {
        return () -> CompletableFuture.completedFuture(value);
    }

    /**
     * Mirrors Python's task manager task surface used by
     * {@code openjiuwen/core/common/background_tasks.py}.
     */
    private static final class TestManagerTask implements BackgroundTask.ManagerTask {
        private final CompletableFuture<Object> done = new CompletableFuture<>();
        private final Object result;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicReference<String> cancelReason = new AtomicReference<>();

        private TestManagerTask(Object result) {
            this.result = result;
        }

        private void complete() {
            done.complete(result);
        }

        @Override
        public boolean isTerminal() {
            return done.isDone();
        }

        @Override
        public CompletableFuture<Object> waitForCompletion() {
            return done;
        }

        @Override
        public CompletableFuture<Void> cancel(String reason) {
            cancelled.set(true);
            cancelReason.set(reason);
            done.complete(result);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Mirrors Python's loaded task-group scheduling surface in
     * {@code openjiuwen/core/common/background_tasks.py}.
     */
    private static final class TestTaskGroup implements BackgroundTasks.TaskGroup {
        private final AtomicReference<Runnable> scheduled = new AtomicReference<>();

        @Override
        public void startSoon(Runnable runnable) {
            scheduled.set(runnable);
        }
    }
}
