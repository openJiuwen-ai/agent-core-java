
package com.openjiuwen.core.common.task_manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class TaskManagerTest {
    @AfterEach
    void tearDown() {
        TaskManager.resetInstance();
    }

    @Test
    void shouldCreateTaskAndWaitForCompletion() throws Exception {
        TaskManager manager = TaskManager.getInstance();

        Task task = manager.createTask(() -> "done", null, "simple", null, null, Map.of(), false);
        List<Object> results = manager.waitAll(false);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.waitFor()).isEqualTo("done");
        assertThat(results).containsExactly("done");
    }

    @Test
    void shouldRejectDuplicateTaskId() {
        TaskManager manager = TaskManager.getInstance();
        manager.createTask(() -> "one", "dup", "first", null, null, Map.of(), false);

        assertThatThrownBy(() -> manager.createTask(() -> "two", "dup", "second", null, null, Map.of(), false))
                .isInstanceOf(DuplicateTaskError.class);
    }

    @Test
    void shouldCancelTaskGroup() throws Exception {
        TaskManager manager = TaskManager.getInstance();

        manager.createTask(() -> {
            Thread.sleep(5_000);
            return "late";
        }, null, "slow1", "workers", null, Map.of(), false);
        manager.createTask(() -> {
            Thread.sleep(5_000);
            return "late2";
        }, null, "slow2", "workers", null, Map.of(), false);

        Thread.sleep(100);
        int cancelled = manager.cancelGroup("workers");
        List<Object> results = manager.waitAll(true);

        assertThat(cancelled).isEqualTo(2);
        assertThat(results).hasSize(2);
        assertThat(manager.getRegistry().getByGroup("workers"))
                .allMatch(task -> task.getStatus() == TaskStatus.CANCELLED);
    }

    @Test
    void shouldInvokeCompletionCallback() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> completedTaskId = new AtomicReference<>();

        manager.on(TaskManagerEvents.TASK_COMPLETED, payload -> {
            completedTaskId.set(String.valueOf(payload.get("task_id")));
            latch.countDown();
            return null;
        });

        Task task = manager.createTask(() -> "ok", null, "callback", null, null, Map.of(), false);
        manager.waitAll(false);

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(completedTaskId.get()).isEqualTo(task.getTaskId());
    }

    @Test
    void shouldTrackParentChildRelationAcrossNestedCreateTask() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        AtomicReference<Task> childRef = new AtomicReference<>();

        Task parent = manager.createTask(() -> {
            Task child = manager.createTask(() -> "child", null, "child", null, null, Map.of(), false);
            childRef.set(child);
            return child.waitFor();
        }, null, "parent", null, null, Map.of(), false);

        manager.waitAll(false);

        assertThat(parent.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(childRef.get()).isNotNull();
        assertThat(childRef.get().getParentTaskId()).isEqualTo(parent.getTaskId());
        assertThat(manager.getTaskTree(parent.getTaskId())).contains("parent").contains("child");
    }

    @Test
    void shouldYieldTasksAsCompleted() {
        TaskManager manager = TaskManager.getInstance();

        Task slow = manager.createTask(() -> {
            Thread.sleep(300);
            return "slow";
        }, null, "slow", null, null, Map.of(), false);
        Task fast = manager.createTask(() -> {
            Thread.sleep(50);
            return "fast";
        }, null, "fast", null, null, Map.of(), false);

        List<Map.Entry<Task, Object>> results = new ArrayList<>();
        for (Map.Entry<Task, Object> entry : manager.asCompleted(List.of(slow, fast), 2.0)) {
            results.add(entry);
        }

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getKey().getName()).isEqualTo("fast");
        assertThat(results.get(0).getValue()).isEqualTo("fast");
    }

    @Test
    void taskGroupShouldWaitForGroupedTasksOnClose() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        try (TaskGroupScope ignored = manager.taskGroup("scoped")) {
            manager.createTask(() -> {
                Thread.sleep(100);
                return "done";
            }, null, "scoped-task", null, null, Map.of(), false);
        }

        assertThat(manager.getRegistry().getByGroup("scoped"))
                .allMatch(task -> task.getStatus() == TaskStatus.COMPLETED);
    }
}
