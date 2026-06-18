/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import com.openjiuwen.core.runner.callback.TaskManagerEvents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused parity tests for {@link TaskManager}.
 *
 * <p>Mirrors Python's {@code TaskManager} in
 * {@code openjiuwen/core/common/task_manager/manager.py}.</p>
 */
class TaskManagerTest {

    @AfterEach
    void tearDown() {
        TaskManager.resetInstance();
    }

    @Test
    void singletonCreatesTaskAndRejectsDuplicateId() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        Task sameManagerTask = TaskManager.getTaskManager().createTask(
                () -> "ok", "task-a", "first", "workers", null, Map.of("priority", "high"), false);

        assertThat(TaskManager.getInstance()).isSameAs(manager);
        assertThat(sameManagerTask.waitForResult()).isEqualTo("ok");
        assertThat(sameManagerTask.getMetadata()).containsEntry("priority", "high");
        assertThat(manager.getRegistry().contains("task-a")).isTrue();
        assertThatThrownBy(() -> manager.createTask(() -> "dup", "task-a", null, null, null, Map.of(), false))
                .isInstanceOf(DuplicateTaskError.class)
                .hasMessageContaining("Task task-a already exists");
    }

    @Test
    void waitGroupAndRemoveCompletedMirrorRegistryFlow() {
        TaskManager manager = TaskManager.getInstance();
        Task first = manager.createTask(() -> "alpha", "task-a", "alpha", "workers", null, Map.of(), false);
        Task second = manager.createTask(() -> "beta", "task-b", "beta", "workers", null, Map.of(), false);

        List<Object> results = manager.waitGroup("workers", Duration.ofSeconds(2), false).join();

        assertThat(List.of(first, second)).hasSize(2);
        assertThat(results).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(manager.getStats()).containsEntry("completed", 2);
        assertThat(manager.removeCompleted()).isEqualTo(2);
        assertThat(manager.getRegistry().getByGroup("workers")).isEmpty();
    }

    @Test
    void cascadeCancelMarksChildrenAndTaskTreeIncludesReason() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        CountDownLatch started = new CountDownLatch(2);
        Task parent = manager.createTask(() -> awaitCancellation(started), "parent-task", "parent", null,
                null, Map.of(), false);
        TaskContext.ContextToken<String> token = TaskContext.setCurrentTaskId(parent.getTaskId());
        Task child;
        try {
            child = manager.createTask(() -> awaitCancellation(started), "child-task", "child", null,
                    null, Map.of(), false);
        } finally {
            TaskContext.resetCurrentTaskId(token);
        }
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

        manager.cascadeCancel(parent.getTaskId(), "user_requested");

        assertThat(parent.getStatus()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(child.getStatus()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(child.getCancelledBy()).isEqualTo(parent.getTaskId());
        assertThat(manager.getTaskTree(parent.getTaskId())).contains("parent [cancelled]")
                .contains("+- child [cancelled] (cancelled by: parent, reason: user_requested)");
    }

    @Test
    void callbacksAsCompletedAndOffMirrorTaskEvents() {
        TaskManager manager = TaskManager.getInstance();
        List<String> events = new CopyOnWriteArrayList<>();
        Consumer<Task> completed = task -> events.add("completed:" + task.getTaskId());
        manager.on(TaskManagerEvents.TASK_COMPLETED, completed);

        Task task = manager.createTask(() -> "done", "task-event", "event", null, null, Map.of(), false);
        List<TaskManager.TaskResult> results = manager.asCompleted(List.of(task), Duration.ofSeconds(2));
        manager.off(TaskManagerEvents.TASK_COMPLETED, completed);
        manager.createTask(() -> "after-off", "task-after-off", "after", null, null, Map.of(), false)
                .waitResult()
                .join();

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.task()).isSameAs(task);
            assertThat(result.result()).isEqualTo("done");
        });
        assertThat(events).containsExactly("completed:task-event");
    }

    @Test
    void taskGroupScopeTracksCurrentGroupAndWaitsOnClose() {
        TaskManager manager = TaskManager.getInstance();
        try (TaskManager.TaskGroupScope ignored = manager.taskGroup()) {
            assertThat(TaskContext.getTaskGroup()).isNotNull();
            manager.createTask(() -> "grouped", "task-grouped", "grouped", null, null, Map.of(), false);
        }

        Task grouped = manager.getTask("task-grouped");
        assertThat(grouped.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(TaskContext.getTaskGroup()).isNull();
    }

    private static Object awaitCancellation(CountDownLatch started) throws InterruptedException {
        started.countDown();
        Thread.sleep(TimeUnit.SECONDS.toMillis(30));
        return "finished";
    }
}
