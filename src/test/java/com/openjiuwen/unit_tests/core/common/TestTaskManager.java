/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.common;

import com.openjiuwen.core.common.task_manager.Task;
import com.openjiuwen.core.common.task_manager.TaskExceptions.DuplicateTaskError;
import com.openjiuwen.core.common.task_manager.TaskGroupContext;
import com.openjiuwen.core.common.task_manager.TaskManager;
import com.openjiuwen.core.common.task_manager.TaskStatus;
import com.openjiuwen.core.runner.callback.TaskManagerEvents;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/core/common/test_task_manager.py}.
 *
 * <p>The Java implementation uses {@link java.util.concurrent.CompletableFuture} and an
 * executor-backed task group in place of Python's anyio task group while preserving task lifecycle,
 * grouping, cancellation, callback, and registry semantics.</p>
 */
class TestTaskManager {

    @AfterEach
    void resetTaskManager() {
        TaskManager.resetInstance();
    }

    @Test
    void testCreateTaskWithoutTaskGroup() throws Exception {
        TaskManager manager = TaskManager.getInstance();

        Task task = manager.createTask(() -> "result", null, "test_task", null, null, null, false);

        assertEquals("result", task.waitForResult());
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
    }

    @Test
    void testCreateTask() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        Task task;

        try (TaskGroupContext ignored = manager.createTaskGroup()) {
            task = manager.createTask(() -> "result", null, "test_task", null, null, null, false);
            assertNotNull(task);
            assertEquals("test_task", task.getName());
            assertNotNull(task.getTaskId());
        }

        assertEquals(TaskStatus.COMPLETED, task.getStatus());
    }

    @Test
    void testTaskCompletes() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        Task task;

        try (TaskGroupContext ignored = manager.createTaskGroup()) {
            task = manager.createTask(() -> {
                sleep(20);
                return "completed";
            }, null, null, null, null, null, false);
        }

        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        assertEquals("completed", task.getResult());
    }

    @Test
    void testTaskCancel() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        Task task;

        try (TaskGroupContext ignored = manager.createTaskGroup()) {
            task = manager.createTask(() -> {
                sleep(150);
                return "done";
            }, null, null, null, null, null, false);
            awaitStatus(task, TaskStatus.RUNNING);
            assertTrue(task.cancel());
        }

        assertEquals(TaskStatus.CANCELLED, task.getStatus());
    }

    @Test
    void testTaskTimeout() throws Exception {
        TaskManager manager = TaskManager.getInstance();

        Task task = manager.createTask(() -> {
            sleep(200);
            return "done";
        }, null, null, null, 0.03, null, true);

        awaitStatus(task, TaskStatus.TIMEOUT);
        assertEquals(TaskStatus.TIMEOUT, task.getStatus());
    }

    @Test
    void testCascadeCancel() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        AtomicReference<Task> childRef = new AtomicReference<>();
        Task parent = parentWithChild(manager, childRef, 150);

        awaitTaskRef(childRef);
        manager.cascadeCancel(parent.getTaskId(), "manual_cancel");

        assertEquals(TaskStatus.CANCELLED, parent.getStatus());
        assertEquals(TaskStatus.CANCELLED, childRef.get().getStatus());
    }

    @Test
    void testParentChildRelationship() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        AtomicReference<String> childId = new AtomicReference<>();

        Task parent = manager.createTask(() -> {
            Task child = manager.createTask(() -> "child", null, "child", null, null, null, false);
            childId.set(child.getTaskId());
            return child.getTaskId();
        }, null, "parent", null, null, null, false);

        parent.waitForResult();
        Task child = manager.getRegistry().get(childId.get());
        assertNotNull(child);
        assertEquals(parent.getTaskId(), child.getParentTaskId());
        assertFalse(manager.getRegistry().getByParent(parent.getTaskId()).isEmpty());
    }

    @Test
    void testTaskGroup() throws Exception {
        TaskManager manager = TaskManager.getInstance();

        try (TaskGroupContext ignored = manager.createTaskGroup()) {
            manager.createTask(() -> 1, null, null, "my_group", null, null, false);
            manager.createTask(() -> 2, null, null, "my_group", null, null, false);
        }

        assertEquals(2, manager.getRegistry().getByGroup("my_group").size());
    }

    @Test
    void testCancelGroup() throws Exception {
        TaskManager manager = TaskManager.getInstance();

        manager.createTask(() -> {
            sleep(150);
            return "done";
        }, null, null, "cancel_me", null, null, false);
        manager.createTask(() -> {
            sleep(150);
            return "done";
        }, null, null, "cancel_me", null, null, false);
        awaitRunningCount(manager, 2);

        assertEquals(2, manager.cancelGroup("cancel_me"));
        assertTrue(manager.getRegistry().getByGroup("cancel_me").stream()
                .allMatch(task -> task.getStatus() == TaskStatus.CANCELLED));
    }

    @Test
    void testEventCallback() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        AtomicInteger calls = new AtomicInteger();
        manager.on(TaskManagerEvents.TASK_COMPLETED, task -> calls.incrementAndGet());

        Task task = manager.createTask(() -> "done", null, null, null, null, null, false);
        task.waitForResult();
        awaitAtomicAtLeast(calls, 1);

        assertTrue(calls.get() >= 1);
    }

    @Test
    void testGetStats() throws Exception {
        TaskManager manager = TaskManager.getInstance();

        try (TaskGroupContext ignored = manager.createTaskGroup()) {
            manager.createTask(() -> 1, null, null, null, null, null, false);
            manager.createTask(() -> 2, null, null, null, null, null, false);
        }

        Map<String, Integer> stats = manager.getStats();
        assertEquals(2, stats.get("total"));
        assertEquals(2, stats.get("completed"));
    }

    @Test
    void testTaskWithMetadata() throws Exception {
        TaskManager manager = TaskManager.getInstance();

        Task task = manager.createTask(() -> "done", null, null, null, null, Map.of("key", "value", "num", 42), false);
        task.waitForResult();

        assertEquals("value", task.getMetadata().get("key"));
        assertEquals(42, task.getMetadata().get("num"));
    }

    @Test
    void testTaskPriority() throws Exception {
        Task task = TaskManager.getInstance()
                .createTask(() -> "done", null, null, null, null, null, false);

        task.waitForResult();

        assertEquals(TaskStatus.COMPLETED, task.getStatus());
    }

    @Test
    void testTaskResultAccessibleAfterWait() throws Exception {
        Task task = TaskManager.getInstance()
                .createTask(() -> "test_result", null, null, null, null, null, false);

        assertEquals("test_result", task.waitForResult());
        assertEquals("test_result", task.getResult());
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
    }

    @Test
    void testTaskErrorOnFailure() throws Exception {
        Task task = TaskManager.getInstance()
                .createTask(() -> {
                    throw new IllegalArgumentException("test error");
                }, null, null, null, null, null, true);

        awaitStatus(task, TaskStatus.FAILED);
        assertEquals(TaskStatus.FAILED, task.getStatus());
        assertNotNull(task.getException());
        assertInstanceOf(IllegalArgumentException.class, task.getException());
        assertTrue(task.getError().contains("test error"));
    }

    @Test
    void testCatchExceptions() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        Task failed = manager.createTask(() -> {
            throw new IllegalArgumentException("test error");
        }, null, "failing", null, null, null, true);
        Task normal = manager.createTask(() -> "done", null, "normal", null, null, null, false);

        normal.waitForResult();
        awaitStatus(failed, TaskStatus.FAILED);

        assertEquals(TaskStatus.FAILED, failed.getStatus());
        assertEquals(TaskStatus.COMPLETED, normal.getStatus());
    }

    @Test
    void testGetRunningTasks() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        Task task = manager.createTask(() -> {
            sleep(150);
            return "done";
        }, null, null, null, null, null, false);

        awaitStatus(task, TaskStatus.RUNNING);

        assertTrue(manager.getRunningTasks().stream().anyMatch(t -> t.getTaskId().equals(task.getTaskId())));
        task.cancel();
    }

    @Test
    void testGetAllTasks() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        Task t1 = manager.createTask(() -> 1, null, null, null, null, null, false);
        Task t2 = manager.createTask(() -> 2, null, null, null, null, null, false);

        t1.waitForResult();
        t2.waitForResult();

        assertTrue(manager.getAllTasks().size() >= 2);
    }

    @Test
    void testRemoveTask() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        Task task = manager.createTask(() -> "done", null, null, null, null, null, false);
        task.waitForResult();

        assertTrue(manager.removeTask(task.getTaskId()));
        assertNull(manager.getRegistry().get(task.getTaskId()));
    }

    @Test
    void testRemoveCompleted() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        manager.createTask(() -> "done", null, null, null, null, null, false).waitForResult();
        manager.createTask(() -> "done", null, null, null, null, null, false).waitForResult();

        assertTrue(manager.removeCompleted() >= 1);
    }

    @Test
    void testGetCurrentTaskId() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        AtomicReference<String> captured = new AtomicReference<>();

        Task task = manager.createTask(() -> {
            captured.set(TaskManager.getCurrentTaskId());
            return null;
        }, null, "test_task", null, null, null, false);

        task.waitForResult();
        assertEquals(task.getTaskId(), captured.get());
        assertNull(TaskManager.getCurrentTaskId());
    }

    @Test
    void testGetCurrentTaskIdNested() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        AtomicReference<String> parentId = new AtomicReference<>();
        AtomicReference<String> childId = new AtomicReference<>();

        Task parent = manager.createTask(() -> {
            parentId.set(TaskManager.getCurrentTaskId());
            Task child = manager.createTask(() -> {
                childId.set(TaskManager.getCurrentTaskId());
                return "child";
            }, null, "child", null, null, null, false);
            child.waitForResult();
            return "parent";
        }, null, "parent", null, null, null, false);

        parent.waitForResult();
        assertEquals(parent.getTaskId(), parentId.get());
        assertNotNull(childId.get());
        assertFalse(parentId.get().equals(childId.get()));
    }

    @Test
    void testAutoCleanup() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        Task task = manager.createTask(() -> "done", null, null, null, null, null, false);
        String taskId = task.getTaskId();
        task.waitForResult();

        assertNotNull(manager.getRegistry().get(taskId));
        assertNotNull(manager.getRegistry().getAll());
    }

    @Test
    void testAutoCascadeCancelMultiLevel() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        AtomicReference<Task> childRef = new AtomicReference<>();
        AtomicReference<Task> grandchildRef = new AtomicReference<>();

        Task parent = parentWithChildAndGrandchild(manager, childRef, grandchildRef);
        awaitTaskRef(grandchildRef);
        manager.cascadeCancel(parent.getTaskId(), "manual_cancel");

        assertEquals(TaskStatus.CANCELLED, parent.getStatus());
        assertEquals(TaskStatus.CANCELLED, childRef.get().getStatus());
        assertEquals(TaskStatus.CANCELLED, grandchildRef.get().getStatus());
    }

    @Test
    void testGetTaskTree() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        AtomicReference<Task> childRef = new AtomicReference<>();
        Task parent = parentWithChild(manager, childRef, 80);
        awaitTaskRef(childRef);
        parent.waitForResult();

        String tree = manager.getTaskTree(parent.getTaskId());

        assertTrue(tree.contains("parent"));
        assertTrue(tree.contains("child"));
        assertTrue(tree.contains("+- "));
    }

    @Test
    void testTaskTreeShowsStatus() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        Task completed = manager.createTask(() -> "done", null, "completed_task", null, null, null, true);
        Task failed = manager.createTask(() -> {
            throw new IllegalStateException("error");
        }, null, "failed_task", null, null, null, true);
        completed.waitForResult();
        awaitStatus(failed, TaskStatus.FAILED);

        assertTrue(manager.getTaskTree(completed.getTaskId()).contains("completed_task [completed]"));
        assertTrue(manager.getTaskTree(failed.getTaskId()).contains("failed_task [failed]"));
    }

    @Test
    void testCancelChainTracking() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        AtomicReference<Task> childRef = new AtomicReference<>();
        AtomicReference<Task> grandchildRef = new AtomicReference<>();
        Task parent = parentWithChildAndGrandchild(manager, childRef, grandchildRef);
        awaitTaskRef(grandchildRef);

        parent.cancel(true);

        assertEquals(parent.getTaskId(), childRef.get().getCancelledBy());
        assertEquals(childRef.get().getTaskId(), grandchildRef.get().getCancelledBy());
        assertTrue(manager.getTaskTree(parent.getTaskId()).contains("cancelled by: parent"));
    }

    @Test
    void testCustomCancelReason() throws Exception {
        Task task = TaskManager.getInstance().createTask(() -> {
            sleep(150);
            return "done";
        }, null, "custom", null, null, null, false);
        awaitStatus(task, TaskStatus.RUNNING);

        assertTrue(task.cancel(false, "user_requested"));

        assertEquals("user_requested", task.getCancelReason());
    }

    @Test
    void testPrintTaskTreeWithCancelInfo() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        Task task = manager.createTask(() -> {
            sleep(150);
            return "done";
        }, null, "printable", null, null, null, false);
        awaitStatus(task, TaskStatus.RUNNING);
        task.cancel(false, "manual_cancel");

        assertDoesNotThrow(() -> manager.printTaskTree(task.getTaskId()));
        assertTrue(manager.getTaskTree(task.getTaskId()).contains("reason: manual_cancel"));
    }

    @Test
    void testDuplicateTaskError() {
        TaskManager manager = TaskManager.getInstance();
        manager.createTask(() -> "first", "dup", "first", null, null, null, false);

        assertThrows(DuplicateTaskError.class,
                () -> manager.createTask(() -> "second", "dup", "second", null, null, null, false));
    }

    @Test
    void testWaitReraisesException() throws Exception {
        Task task = TaskManager.getInstance().createTask(() -> {
            throw new IllegalArgumentException("fail");
        }, null, null, null, null, null, false);

        awaitStatus(task, TaskStatus.FAILED);

        assertThrows(IllegalArgumentException.class, task::waitForResult);
    }

    @Test
    void testTaskWait() throws Exception {
        Task task = TaskManager.getInstance().createTask(() -> "ok", null, null, null, null, null, false);

        assertEquals("ok", task.waitForResult());
    }

    @Test
    void testWaitGroup() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        manager.createTask(() -> "a", null, null, "letters", null, null, false);
        manager.createTask(() -> "b", null, null, "letters", null, null, false);

        List<Object> results = manager.waitGroupResults("letters").get(1, TimeUnit.SECONDS);

        assertEquals(2, results.size());
        assertTrue(results.contains("a"));
        assertTrue(results.contains("b"));
    }

    @Test
    void testWaitGroupPartialFailure() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        manager.createTask(() -> "ok", null, null, "mixed", null, null, false);
        manager.createTask(() -> {
            throw new IllegalArgumentException("fail");
        }, null, null, "mixed", null, null, true);

        List<Object> results = manager.waitGroupResults("mixed", true).get(1, TimeUnit.SECONDS);

        assertTrue(results.contains("ok"));
        assertTrue(results.stream().anyMatch(IllegalArgumentException.class::isInstance));
    }

    @Test
    void testWaitGroupRaiseOnFailure() {
        TaskManager manager = TaskManager.getInstance();
        manager.createTask(() -> "ok", null, null, "mixed", null, null, false);
        manager.createTask(() -> {
            throw new IllegalArgumentException("fail");
        }, null, null, "mixed", null, null, true);

        assertThrows(ExecutionException.class, () -> manager.waitGroupResults("mixed").get(1, TimeUnit.SECONDS));
    }

    @Test
    void testWaitAll() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        manager.createTask(() -> "a", null, null, null, null, null, false);
        manager.createTask(() -> "b", null, null, null, null, null, false);

        assertEquals(2, manager.waitAllResults().get(1, TimeUnit.SECONDS).size());
    }

    @Test
    void testWaitAllPartialFailure() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        manager.createTask(() -> "ok", null, null, null, null, null, false);
        manager.createTask(() -> {
            throw new IllegalArgumentException("fail");
        }, null, null, null, null, null, true);

        List<Object> results = manager.waitAllResults(true).get(1, TimeUnit.SECONDS);

        assertTrue(results.contains("ok"));
        assertTrue(results.stream().anyMatch(IllegalArgumentException.class::isInstance));
    }

    @Test
    void testWaitAllRaiseOnFailure() {
        TaskManager manager = TaskManager.getInstance();
        manager.createTask(() -> "ok", null, null, null, null, null, false);
        manager.createTask(() -> {
            throw new IllegalArgumentException("fail");
        }, null, null, null, null, null, true);

        assertThrows(ExecutionException.class, () -> manager.waitAllResults().get(1, TimeUnit.SECONDS));
    }

    @Test
    void testOffCallback() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        AtomicInteger calls = new AtomicInteger();
        Consumer<Task> callback = task -> calls.incrementAndGet();
        manager.on(TaskManagerEvents.TASK_COMPLETED, callback);
        manager.off(TaskManagerEvents.TASK_COMPLETED, callback);

        manager.createTask(() -> "done", null, null, null, null, null, false).waitForResult();

        assertEquals(0, calls.get());
    }

    @Test
    void testMultipleCallbacksSameEvent() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        AtomicInteger calls = new AtomicInteger();
        manager.on(TaskManagerEvents.TASK_COMPLETED, task -> calls.incrementAndGet());
        manager.on(TaskManagerEvents.TASK_COMPLETED, task -> calls.incrementAndGet());

        manager.createTask(() -> "done", null, null, null, null, null, false).waitForResult();
        awaitAtomicAtLeast(calls, 2);

        assertEquals(2, calls.get());
    }

    @Test
    void testCallbackExceptionDoesNotAffectOthers() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        AtomicInteger calls = new AtomicInteger();
        manager.on(TaskManagerEvents.TASK_COMPLETED, task -> {
            throw new RuntimeException("callback error");
        });
        manager.on(TaskManagerEvents.TASK_COMPLETED, task -> calls.incrementAndGet());

        manager.createTask(() -> "done", null, null, null, null, null, false).waitForResult();
        awaitAtomicAtLeast(calls, 1);

        assertEquals(1, calls.get());
    }

    @Test
    void testCancelAll() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        Task t1 = manager.createTask(() -> {
            sleep(150);
            return null;
        }, null, null, null, null, null, false);
        Task t2 = manager.createTask(() -> {
            sleep(150);
            return null;
        }, null, null, null, null, null, false);
        awaitRunningCount(manager, 2);

        assertEquals(2, manager.cancelAll());
        assertEquals(TaskStatus.CANCELLED, t1.getStatus());
        assertEquals(TaskStatus.CANCELLED, t2.getStatus());
    }

    @Test
    void testCancelGroupDirect() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        manager.createTask(() -> {
            sleep(150);
            return null;
        }, null, null, "g1", null, null, false);
        manager.createTask(() -> {
            sleep(150);
            return null;
        }, null, null, "g1", null, null, false);
        awaitRunningCount(manager, 2);

        assertEquals(2, manager.cancelGroup("g1"));
    }

    @Test
    void testCancelTerminalTaskReturnsFalse() throws Exception {
        Task task = TaskManager.getInstance().createTask(() -> "done", null, null, null, null, null, false);
        task.waitForResult();

        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        assertFalse(task.cancel());
    }

    @Test
    void testGetTasksByStatus() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        manager.createTask(() -> "done", null, null, null, null, null, false).waitForResult();
        manager.createTask(() -> "done", null, null, null, null, null, false).waitForResult();

        List<Task> completed = manager.getTasksByStatus(TaskStatus.COMPLETED);

        assertEquals(2, completed.size());
        assertTrue(completed.stream().allMatch(task -> task.getStatus() == TaskStatus.COMPLETED));
    }

    @Test
    void testGetStatsAllFields() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        manager.createTask(() -> 1, null, null, null, null, null, false).waitForResult();
        Task failed = manager.createTask(() -> {
            throw new IllegalArgumentException("err");
        }, null, null, null, null, null, true);
        awaitStatus(failed, TaskStatus.FAILED);

        Map<String, Integer> stats = manager.getStats();

        assertTrue(stats.containsKey("failed"));
        assertTrue(stats.containsKey("cancelled"));
        assertTrue(stats.containsKey("timeout"));
        assertEquals(1, stats.get("completed"));
        assertEquals(1, stats.get("failed"));
    }

    @Test
    void testRemoveCompletedIncludesFailed() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        Task failed = manager.createTask(() -> {
            throw new IllegalArgumentException("err");
        }, null, null, null, null, null, true);
        awaitStatus(failed, TaskStatus.FAILED);

        assertTrue(manager.removeCompleted() >= 1);
        assertNull(manager.getRegistry().get(failed.getTaskId()));
    }

    @Test
    void testGetTasksByGroupNonexistent() {
        assertEquals(List.of(), TaskManager.getInstance().getRegistry().getByGroup("no-such-group"));
    }

    @Test
    void testRemoveTaskNonexistent() {
        assertFalse(TaskManager.getInstance().removeTask("nonexistent-task-id"));
    }

    @Test
    void testSingletonBehavior() {
        TaskManager m1 = TaskManager.getInstance();
        TaskManager m2 = TaskManager.getInstance();
        assertSame(m1, m2);
    }

    @Test
    void testDisplayNameWithoutName() throws Exception {
        Task task = TaskManager.getInstance().createTask(() -> "done", null, null, null, null, null, false);
        task.waitForResult();

        assertNull(task.getName());
        assertEquals(task.getTaskId().substring(0, 8), task.getDisplayName());
    }

    @Test
    void testCascadeFalseDoesNotCancelChildren() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        AtomicReference<Task> childRef = new AtomicReference<>();
        Task parent = parentWithChild(manager, childRef, 80);
        awaitTaskRef(childRef);
        awaitStatus(childRef.get(), TaskStatus.RUNNING);

        parent.cancel(false);

        assertEquals(TaskStatus.CANCELLED, parent.getStatus());
        assertFalse(childRef.get().getStatus() == TaskStatus.CANCELLED);
    }

    @Test
    void testCancelChildDoesNotCancelParent() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        AtomicReference<Task> childRef = new AtomicReference<>();
        Task parent = parentWithChild(manager, childRef, 60);
        awaitTaskRef(childRef);

        childRef.get().cancel(false);
        parent.waitForResult();

        assertEquals(TaskStatus.CANCELLED, childRef.get().getStatus());
        assertEquals("manual_cancel", childRef.get().getCancelReason());
        assertEquals(TaskStatus.COMPLETED, parent.getStatus());
    }

    @Test
    void testCancelGroupChildDoesNotCancelParent() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        AtomicReference<Task> childRef = new AtomicReference<>();
        Task parent = manager.createTask(() -> {
            childRef.set(manager.createTask(() -> {
                sleep(120);
                return "child";
            }, null, "child", "child_group", null, null, false));
            sleep(40);
            return "parent_done";
        }, null, "parent", "parent_group", null, null, false);

        awaitTaskRef(childRef);
        assertEquals(1, manager.cancelGroup("child_group"));
        parent.waitForResult();

        assertEquals(TaskStatus.CANCELLED, childRef.get().getStatus());
        assertEquals("manual_cancel", childRef.get().getCancelReason());
        assertEquals(TaskStatus.COMPLETED, parent.getStatus());
    }

    @Test
    void testPrintTaskTreeNoArgs() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        manager.createTask(() -> "done", null, null, null, null, null, false).waitForResult();

        assertDoesNotThrow(() -> manager.printTaskTree());
    }

    @Test
    void testTaskManager010() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        Task task1 = manager.createTask(() -> "result", null, "test_task", "group", null, null, false);
        AtomicReference<Task> childRef = new AtomicReference<>();
        AtomicReference<Task> grandchildRef = new AtomicReference<>();
        Task parent = parentWithGroupedChildAndGrandchild(manager, childRef, grandchildRef);

        awaitTaskRef(grandchildRef);
        assertEquals(1, manager.cancelGroup("group1"));
        task1.waitForResult();
        childRef.get().waitForResult();
        parent.waitForResult();

        assertEquals(TaskStatus.COMPLETED, task1.getStatus());
        assertEquals("result", task1.getResult());
        assertEquals(TaskStatus.COMPLETED, childRef.get().getStatus());
        assertNull(childRef.get().getCancelReason());
        assertEquals(TaskStatus.COMPLETED, parent.getStatus());
        assertNull(parent.getCancelledBy());
        assertNull(parent.getCancelReason());
        assertEquals(TaskStatus.CANCELLED, grandchildRef.get().getStatus());
        assertEquals("manual_cancel", grandchildRef.get().getCancelReason());
        assertTrue(manager.getTaskTree(parent.getTaskId()).contains("grandchild [cancelled]"));
    }

    @Test
    void testTaskManager005() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        AtomicReference<Task> childRef = new AtomicReference<>();
        AtomicReference<Task> grandchildRef = new AtomicReference<>();
        Task parent = parentWithChildAndGrandchild(manager, childRef, grandchildRef);

        awaitTaskRef(grandchildRef);
        parent.cancel(true);

        assertEquals(TaskStatus.CANCELLED, parent.getStatus());
        assertEquals("manual_cancel", parent.getCancelReason());
        assertEquals(TaskStatus.CANCELLED, childRef.get().getStatus());
        assertEquals(parent.getTaskId(), childRef.get().getCancelledBy());
        assertEquals(TaskStatus.CANCELLED, grandchildRef.get().getStatus());
        assertEquals(childRef.get().getTaskId(), grandchildRef.get().getCancelledBy());
    }

    @Test
    void testTaskManager008() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        AtomicReference<Task> childRef = new AtomicReference<>();
        AtomicReference<Task> grandchildRef = new AtomicReference<>();
        Task parent = parentWithChildAndGrandchild(manager, childRef, grandchildRef);

        awaitTaskRef(grandchildRef);
        childRef.get().cancel(true);
        parent.waitForResult();

        assertEquals(TaskStatus.COMPLETED, parent.getStatus());
        assertNull(parent.getCancelReason());
        assertEquals(TaskStatus.CANCELLED, childRef.get().getStatus());
        assertEquals("manual_cancel", childRef.get().getCancelReason());
        assertEquals(TaskStatus.CANCELLED, grandchildRef.get().getStatus());
        assertEquals(childRef.get().getTaskId(), grandchildRef.get().getCancelledBy());
        assertEquals("parent_cancelled", grandchildRef.get().getCancelReason());
    }

    private static Task parentWithChild(TaskManager manager, AtomicReference<Task> childRef, long parentSleepMillis) {
        return manager.createTask(() -> {
            childRef.set(manager.createTask(() -> {
                sleep(120);
                return "child";
            }, null, "child", null, null, null, false));
            sleep(parentSleepMillis);
            return "parent";
        }, null, "parent", null, null, null, false);
    }

    private static Task parentWithChildAndGrandchild(TaskManager manager, AtomicReference<Task> childRef,
            AtomicReference<Task> grandchildRef) {
        return manager.createTask(() -> {
            childRef.set(manager.createTask(() -> {
                grandchildRef.set(manager.createTask(() -> {
                    sleep(500);
                    return "grandchild_done";
                }, null, "grandchild", null, null, null, false));
                sleep(300);
                return "child_done";
            }, null, "child", null, null, null, false));
            sleep(300);
            return "parent_done";
        }, null, "parent", null, null, null, false);
    }

    private static Task parentWithGroupedChildAndGrandchild(TaskManager manager, AtomicReference<Task> childRef,
            AtomicReference<Task> grandchildRef) {
        return manager.createTask(() -> {
            childRef.set(manager.createTask(() -> {
                grandchildRef.set(manager.createTask(() -> {
                    sleep(200);
                    return "grandchild_done";
                }, null, "grandchild", "group1", null, null, false));
                sleep(80);
                return "child_done";
            }, null, "child", "group2", null, null, false));
            sleep(150);
            return "parent_done";
        }, null, "parent", "group2", null, null, false);
    }

    private static void awaitStatus(Task task, TaskStatus status) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (task.getStatus() == status) {
                return;
            }
            Thread.sleep(10);
        }
    }

    private static void awaitRunningCount(TaskManager manager, int count) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (manager.getRunningTasks().size() >= count) {
                return;
            }
            Thread.sleep(10);
        }
    }

    private static void awaitTaskRef(AtomicReference<Task> ref) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (ref.get() != null) {
                return;
            }
            Thread.sleep(10);
        }
        assertNotNull(ref.get());
    }

    private static void awaitAtomicAtLeast(AtomicInteger value, int expected) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (value.get() >= expected) {
                return;
            }
            Thread.sleep(10);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
