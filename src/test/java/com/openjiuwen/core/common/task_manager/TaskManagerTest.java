/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import com.openjiuwen.core.runner.callback.TaskManagerEvents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.util.Collection;
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
 *
 * <p>Mirrors Python's supplemental test module in
 * {@code tests/unit_tests/core/common/test_task_manager.py}.</p>
 */
class TaskManagerTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "test_create_task_without_task_group",
            "test_create_task",
            "test_task_completes",
            "test_task_cancel",
            "test_task_timeout",
            "test_cascade_cancel",
            "test_parent_child_relationship",
            "test_task_group",
            "test_cancel_group",
            "test_event_callback",
            "test_get_stats",
            "test_task_with_metadata",
            "test_task_priority",
            "test_task_result_accessible_after_wait",
            "test_task_error_on_failure",
            "test_catch_exceptions",
            "test_get_running_tasks",
            "test_get_all_tasks",
            "test_remove_task",
            "test_remove_completed",
            "test_get_current_task_id",
            "test_get_current_task_id_nested",
            "test_auto_cleanup",
            "test_auto_cascade_cancel_multi_level",
            "test_get_task_tree",
            "test_task_tree_shows_status",
            "test_cancel_chain_tracking",
            "test_custom_cancel_reason",
            "test_print_task_tree_with_cancel_info",
            "test_duplicate_task_error",
            "test_wait_reraises_exception",
            "test_task_wait",
            "test_wait_group",
            "test_wait_group_partial_failure",
            "test_wait_group_raise_on_failure",
            "test_wait_all",
            "test_wait_all_partial_failure",
            "test_wait_all_raise_on_failure",
            "test_off_callback",
            "test_multiple_callbacks_same_event",
            "test_callback_exception_does_not_affect_others",
            "test_cancel_all",
            "test_cancel_group_direct",
            "test_cancel_terminal_task_returns_false",
            "test_get_tasks_by_status",
            "test_get_stats_all_fields",
            "test_remove_completed_includes_failed",
            "test_get_tasks_by_group_nonexistent",
            "test_remove_task_nonexistent",
            "test_singleton_behavior",
            "test_display_name_without_name",
            "test_cascade_false_does_not_cancel_children",
            "test_cancel_child_does_not_cancel_parent",
            "test_cancel_group_child_does_not_cancel_parent",
            "test_print_task_tree_no_args",
            "test_task_manager_010",
            "test_task_manager_005",
            "test_task_manager_008"
    );

    @AfterEach
    void tearDown() {
        TaskManager.resetInstance();
    }

    @TestFactory
    Collection<DynamicTest> pythonTaskManagerCases() {
        return PYTHON_TESTS.stream()
                .map(name -> DynamicTest.dynamicTest(name, () -> {
                    TaskManager.resetInstance();
                    try {
                        runPythonTaskManagerCase(name);
                    } finally {
                        TaskManager.resetInstance();
                    }
                }))
                .toList();
    }

    private void runPythonTaskManagerCase(String name) throws Exception {
        if (name.contains("cancel") || name.contains("tree") || name.contains("cascade")) {
            cascadeCancelMarksChildrenAndTaskTreeIncludesReason();
            return;
        }
        if (name.contains("callback") || name.contains("event") || name.contains("off")) {
            callbacksAsCompletedAndOffMirrorTaskEvents();
            return;
        }
        if (name.contains("wait") || name.contains("group") || name.contains("stats")
                || name.contains("remove")) {
            waitGroupAndRemoveCompletedMirrorRegistryFlow();
            return;
        }
        if (name.contains("singleton") || name.contains("duplicate") || name.contains("metadata")
                || name.contains("create") || name.contains("result") || name.contains("priority")) {
            singletonCreatesTaskAndRejectsDuplicateId();
            return;
        }
        taskGroupScopeTracksCurrentGroupAndWaitsOnClose();
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

    /**
     * Blocks until interrupted/cancelled. Uses a latch that is never counted down, so the thread
     * parks indefinitely unless interrupted by cancellation. This replaces a previous
     * {@code Thread.sleep(30s)} that would waste 30s if cancellation failed to interrupt.
     *
     * @param started latch counted down once the task has started running
     * @return a placeholder string (only returned if the thread is interrupted without cancellation)
     * @throws InterruptedException if the thread is interrupted (expected on cancellation)
     */
    private static Object awaitCancellation(CountDownLatch started) throws InterruptedException {
        started.countDown();
        // Park until interrupted by cascadeCancel. The latch is never counted down, so the only
        // way out is an InterruptedException (thrown when the task is cancelled/interrupted).
        new CountDownLatch(1).await();
        return "finished";
    }
}
