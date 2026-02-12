// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TaskManager, TaskFilter, and TaskManagerState.
 *
 * <p>Covers CRUD operations, state management, status/priority updates,
 * hierarchical relationships, concurrency safety, filter validation,
 * and state serialization.
 *
 * <p>Python reference: {@code tests/unit_tests/core/controller/modules/test_task_manager.py}
 */
class TaskManagerTest {

    private ControllerConfig config;
    private TaskManager manager;

    @BeforeEach
    void setUp() {
        config = new ControllerConfig();
        manager = new TaskManager(config);
    }

    /**
     * Helper to create a task with defaults.
     */
    private static Task makeTask(String taskId) {
        return makeTask(taskId, "s1", "analysis", 1, TaskStatus.SUBMITTED, null, null);
    }

    private static Task makeTask(String taskId, int priority) {
        return makeTask(taskId, "s1", "analysis", priority, TaskStatus.SUBMITTED, null, null);
    }

    private static Task makeTask(String taskId, TaskStatus status) {
        return makeTask(taskId, "s1", "analysis", 1, status, null, null);
    }

    private static Task makeTask(String taskId, String parentTaskId) {
        return makeTask(taskId, "s1", "analysis", 1, TaskStatus.SUBMITTED, parentTaskId, null);
    }

    private static Task makeTask(String taskId, String sessionId, String taskType,
                                  int priority, TaskStatus status,
                                  String parentTaskId, Map<String, Object> metadata) {
        return Task.builder(sessionId, taskId, taskType)
            .priority(priority)
            .status(status)
            .parentTaskId(parentTaskId)
            .metadata(metadata)
            .build();
    }

    // ==================== CRUD Tests ====================

    @Nested
    @DisplayName("CRUD Tests")
    class CrudTests {

        @Test
        @DisplayName("Adding a single task should store it with correct priority index and root set")
        void testAddSingleTask() {
            Task t = makeTask("t1");
            manager.addTask(t);

            assertTrue(manager.getTasks().containsKey("t1"));
            assertTrue(manager.getPriorityIndex().get(1).contains("t1"));
            assertTrue(manager.getRootTasks().contains("t1"));
        }

        @Test
        @DisplayName("Adding a list of tasks should store all of them")
        void testAddBatchTasks() {
            List<Task> tasks = List.of(makeTask("t1"), makeTask("t2", 3), makeTask("t3", 1));
            manager.addTask(tasks);

            assertEquals(3, manager.getTasks().size());
            assertTrue(manager.getPriorityIndex().get(3).contains("t2"));
            assertEquals(Set.of("t1", "t2", "t3"), manager.getRootTasks());
        }

        @Test
        @DisplayName("Adding a task with an existing task_id should raise")
        void testAddDuplicateTaskRaises() {
            manager.addTask(makeTask("t1"));
            assertThrows(BaseError.class, () -> manager.addTask(makeTask("t1")));
        }

        @Test
        @DisplayName("Adding a child task should update parent-child indices")
        void testAddTaskWithParentChildRelationship() {
            manager.addTask(makeTask("parent"));
            manager.addTask(makeTask("child", "parent"));

            assertTrue(manager.getParentToChildren().get("parent").contains("child"));
            assertEquals("parent", manager.getChildToParent().get("child"));
            assertFalse(manager.getRootTasks().contains("child"));
            assertTrue(manager.getRootTasks().contains("parent"));
        }

        @Test
        @DisplayName("get_task(None) should return all tasks")
        void testGetTaskNoneReturnsAll() {
            manager.addTask(List.of(makeTask("t1"), makeTask("t2")));
            List<Task> result = manager.getTask(null);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("get_task by task_id should return matching task")
        void testGetTaskById() {
            manager.addTask(List.of(makeTask("t1"), makeTask("t2")));
            List<Task> result = manager.getTask(TaskFilter.builder().taskId("t1").build());
            assertEquals(1, result.size());
            assertEquals("t1", result.get(0).getTaskId());
        }

        @Test
        @DisplayName("get_task with list of task_ids should return matching tasks")
        void testGetTaskByIdList() {
            manager.addTask(List.of(makeTask("t1"), makeTask("t2"), makeTask("t3")));
            List<Task> result = manager.getTask(TaskFilter.builder().taskIds(List.of("t1", "t3")).build());
            Set<String> ids = result.stream().map(Task::getTaskId).collect(Collectors.toSet());
            assertEquals(Set.of("t1", "t3"), ids);
        }

        @Test
        @DisplayName("get_task by session_id should return tasks in that session")
        void testGetTaskBySessionId() {
            manager.addTask(List.of(
                makeTask("t1", "s1", "analysis", 1, TaskStatus.SUBMITTED, null, null),
                makeTask("t2", "s2", "analysis", 1, TaskStatus.SUBMITTED, null, null)
            ));
            List<Task> result = manager.getTask(TaskFilter.builder().sessionId("s1").build());
            assertEquals(1, result.size());
            assertEquals("s1", result.get(0).getSessionId());
        }

        @Test
        @DisplayName("get_task by priority (int) should return tasks with that priority")
        void testGetTaskByPriority() {
            manager.addTask(List.of(makeTask("t1", 1), makeTask("t2", 5)));
            List<Task> result = manager.getTask(TaskFilter.builder().priority(5).build());
            assertEquals(1, result.size());
            assertEquals("t2", result.get(0).getTaskId());
        }

        @Test
        @DisplayName("get_task with priority='highest' should raise")
        void testGetTaskHighestPriorityRaises() {
            manager.addTask(makeTask("t1"));
            assertThrows(BaseError.class, () ->
                manager.getTask(TaskFilter.builder().priorityHighest().build())
            );
        }

        @Test
        @DisplayName("get_task by status should filter correctly")
        void testGetTaskByStatus() {
            manager.addTask(List.of(
                makeTask("t1", TaskStatus.SUBMITTED),
                makeTask("t2", TaskStatus.WORKING)
            ));
            List<Task> result = manager.getTask(TaskFilter.builder().status(TaskStatus.WORKING).build());
            assertEquals(1, result.size());
            assertEquals("t2", result.get(0).getTaskId());
        }

        @Test
        @DisplayName("get_task by user_id should match metadata['user_id']")
        void testGetTaskByUserIdInMetadata() {
            manager.addTask(List.of(
                makeTask("t1", "s1", "analysis", 1, TaskStatus.SUBMITTED, null, Map.of("user_id", "u1")),
                makeTask("t2", "s1", "analysis", 1, TaskStatus.SUBMITTED, null, Map.of("user_id", "u2"))
            ));
            List<Task> result = manager.getTask(TaskFilter.builder().userId("u1").build());
            assertEquals(1, result.size());
            assertEquals("t1", result.get(0).getTaskId());
        }

        @Test
        @DisplayName("get_task with is_root=True should return only root tasks")
        void testGetTaskIsRoot() {
            manager.addTask(makeTask("parent"));
            manager.addTask(makeTask("child", "parent"));
            List<Task> result = manager.getTask(TaskFilter.builder().isRoot(true).build());
            Set<String> ids = result.stream().map(Task::getTaskId).collect(Collectors.toSet());
            assertEquals(Set.of("parent"), ids);
        }

        @Test
        @DisplayName("get_task with with_children should include descendant tasks")
        void testGetTaskWithChildrenRecursive() {
            manager.addTask(makeTask("root"));
            manager.addTask(makeTask("child1", "root"));
            manager.addTask(makeTask("grandchild", "child1"));
            List<Task> result = manager.getTask(
                TaskFilter.builder().taskId("root").withChildren(true).build()
            );
            Set<String> ids = result.stream().map(Task::getTaskId).collect(Collectors.toSet());
            assertTrue(ids.contains("root"));
            assertTrue(ids.contains("child1"));
            assertTrue(ids.contains("grandchild"));
        }

        @Test
        @DisplayName("pop_task should return the task and remove it from the manager")
        void testPopTaskRemovesAndReturns() {
            manager.addTask(List.of(makeTask("t1"), makeTask("t2")));
            List<Task> popped = manager.popTask(TaskFilter.builder().taskId("t1").build());
            assertEquals(1, popped.size());
            assertEquals("t1", popped.get(0).getTaskId());
            assertFalse(manager.getTasks().containsKey("t1"));
        }

        @Test
        @DisplayName("pop_task with priority='highest' should pop the highest-priority tasks")
        void testPopTaskHighestPriority() {
            manager.addTask(List.of(makeTask("t1", 1), makeTask("t2", 10)));
            List<Task> popped = manager.popTask(TaskFilter.builder().priorityHighest().build());
            assertEquals(1, popped.size());
            assertEquals("t2", popped.get(0).getTaskId());
            assertFalse(manager.getTasks().containsKey("t2"));
        }

        @Test
        @DisplayName("pop_task with None filter should raise")
        void testPopTaskNoneFilterRaises() {
            assertThrows(BaseError.class, () -> manager.popTask(null));
        }

        @Test
        @DisplayName("pop_task should promote children of removed parent to root tasks")
        void testPopTaskPromotesChildren() {
            manager.addTask(makeTask("parent"));
            manager.addTask(makeTask("child", "parent"));
            manager.popTask(TaskFilter.builder().taskId("parent").build());
            assertTrue(manager.getRootTasks().contains("child"));
            assertNull(manager.getTasks().get("child").getParentTaskId());
        }

        @Test
        @DisplayName("update_task should update the priority index when priority changes")
        void testUpdateTaskChangesPriorityIndex() {
            manager.addTask(makeTask("t1", 1));
            Task updated = makeTask("t1", "s1", "analysis", 5, TaskStatus.SUBMITTED, null, null);
            boolean success = manager.updateTask(updated);
            assertTrue(success);
            assertFalse(manager.getPriorityIndex().getOrDefault(1, List.of()).contains("t1"));
            assertTrue(manager.getPriorityIndex().get(5).contains("t1"));
        }

        @Test
        @DisplayName("update_task should handle parent_task_id change correctly")
        void testUpdateTaskChangesParent() {
            manager.addTask(List.of(makeTask("parent1"), makeTask("parent2"), makeTask("child")));
            // Move child under parent1
            Task childUpdated = makeTask("child", "s1", "analysis", 1, TaskStatus.SUBMITTED, "parent1", null);
            manager.updateTask(childUpdated);
            assertTrue(manager.getParentToChildren().get("parent1").contains("child"));
            assertFalse(manager.getRootTasks().contains("child"));

            // Move child under parent2
            Task childUpdated2 = makeTask("child", "s1", "analysis", 1, TaskStatus.SUBMITTED, "parent2", null);
            manager.updateTask(childUpdated2);
            assertTrue(manager.getParentToChildren().get("parent2").contains("child"));
            assertFalse(manager.getParentToChildren().getOrDefault("parent1", Set.of()).contains("child"));
        }

        @Test
        @DisplayName("update_task for non-existent task_id should return false")
        void testUpdateNonexistentTaskReturnsFalse() {
            boolean success = manager.updateTask(makeTask("nonexistent"));
            assertFalse(success);
        }

        @Test
        @DisplayName("remove_task should delete the specified task")
        void testRemoveTaskById() {
            manager.addTask(List.of(makeTask("t1"), makeTask("t2")));
            manager.removeTask(TaskFilter.builder().taskId("t1").build());
            assertFalse(manager.getTasks().containsKey("t1"));
            assertTrue(manager.getTasks().containsKey("t2"));
        }

        @Test
        @DisplayName("remove_task with with_children should remove descendants")
        void testRemoveTaskWithChildrenRecursive() {
            manager.addTask(makeTask("root"));
            manager.addTask(makeTask("child1", "root"));
            manager.addTask(makeTask("grandchild", "child1"));
            manager.addTask(makeTask("other"));

            manager.removeTask(TaskFilter.builder().taskId("root").withChildren(true).build());
            assertFalse(manager.getTasks().containsKey("root"));
            assertFalse(manager.getTasks().containsKey("child1"));
            assertFalse(manager.getTasks().containsKey("grandchild"));
            assertTrue(manager.getTasks().containsKey("other"));
        }

        @Test
        @DisplayName("remove_task with None filter should raise")
        void testRemoveTaskNoneFilterRaises() {
            assertThrows(BaseError.class, () -> manager.removeTask(null));
        }

        @Test
        @DisplayName("remove_task with priority='highest' should raise")
        void testRemoveTaskHighestPriorityRaises() {
            manager.addTask(makeTask("t1"));
            assertThrows(BaseError.class, () ->
                manager.removeTask(TaskFilter.builder().priorityHighest().build())
            );
        }

        @Test
        @DisplayName("Removing a parent should promote un-removed children to root")
        void testRemoveParentPromotesRemainingChildren() {
            manager.addTask(makeTask("parent"));
            manager.addTask(makeTask("child1", "parent"));
            manager.addTask(makeTask("child2", "parent"));
            manager.removeTask(TaskFilter.builder().taskId("parent").build());

            assertTrue(manager.getRootTasks().contains("child1"));
            assertTrue(manager.getRootTasks().contains("child2"));
            assertNull(manager.getTasks().get("child1").getParentTaskId());
        }
    }

    // ==================== State Management Tests ====================

    @Nested
    @DisplayName("State Management Tests")
    class StateTests {

        @Test
        @DisplayName("get_state → load_state should reproduce exact same state")
        void testStateRoundtrip() {
            manager.addTask(makeTask("parent", 2));
            manager.addTask(Task.builder("s1", "child", "analysis")
                .priority(1).status(TaskStatus.SUBMITTED).parentTaskId("parent").build());

            TaskManagerState state = manager.getState();
            TaskManager newManager = new TaskManager(new ControllerConfig());
            newManager.loadState(state);

            assertEquals(Set.of("parent", "child"), newManager.getTasks().keySet());
            assertTrue(newManager.getRootTasks().contains("parent"));
            assertFalse(newManager.getRootTasks().contains("child"));
            assertEquals("parent", newManager.getChildToParent().get("child"));
        }

        @Test
        @DisplayName("clear_state should empty all internal structures")
        void testClearState() {
            manager.addTask(List.of(makeTask("t1"), makeTask("t2")));
            manager.clearState();

            assertEquals(0, manager.getTasks().size());
            assertEquals(0, manager.getPriorityIndex().size());
            assertEquals(0, manager.getRootTasks().size());
        }

        @Test
        @DisplayName("Modifications to returned state should not affect internal state")
        void testStateModificationIsolation() {
            manager.addTask(makeTask("t1"));
            TaskManagerState state = manager.getState();
            state.getTasks().clear();
            // Internal tasks should still exist
            assertTrue(manager.getTasks().containsKey("t1"));
        }
    }

    // ==================== Status & Priority Management ====================

    @Nested
    @DisplayName("Status and Priority Tests")
    class StatusAndPriorityTests {

        @Test
        @DisplayName("update_task_status should change a single task's status")
        void testUpdateStatusSingleTask() {
            manager.addTask(makeTask("t1"));
            manager.updateTaskStatus("t1", TaskStatus.WORKING);
            assertEquals(TaskStatus.WORKING, manager.getTasks().get("t1").getStatus());
        }

        @Test
        @DisplayName("update_task_status with list should update all specified tasks")
        void testUpdateStatusListOfTasks() {
            manager.addTask(List.of(makeTask("t1"), makeTask("t2")));
            manager.updateTaskStatus(List.of("t1", "t2"), TaskStatus.COMPLETED);
            assertEquals(TaskStatus.COMPLETED, manager.getTasks().get("t1").getStatus());
            assertEquals(TaskStatus.COMPLETED, manager.getTasks().get("t2").getStatus());
        }

        @Test
        @DisplayName("update_task_status with with_children+is_recursive should update descendants")
        void testUpdateStatusWithChildrenRecursive() {
            manager.addTask(makeTask("root"));
            manager.addTask(makeTask("child", "root"));
            manager.addTask(makeTask("grandchild", "child"));

            manager.updateTaskStatus("root", TaskStatus.PAUSED, true, true);
            assertEquals(TaskStatus.PAUSED, manager.getTasks().get("root").getStatus());
            assertEquals(TaskStatus.PAUSED, manager.getTasks().get("child").getStatus());
            assertEquals(TaskStatus.PAUSED, manager.getTasks().get("grandchild").getStatus());
        }

        @Test
        @DisplayName("update_task_status with with_children but not is_recursive updates direct children only")
        void testUpdateStatusWithChildrenNonRecursive() {
            manager.addTask(makeTask("root"));
            manager.addTask(makeTask("child", "root"));
            manager.addTask(makeTask("grandchild", "child"));

            manager.updateTaskStatus("root", TaskStatus.CANCELED, true, false);
            assertEquals(TaskStatus.CANCELED, manager.getTasks().get("root").getStatus());
            assertEquals(TaskStatus.CANCELED, manager.getTasks().get("child").getStatus());
            // grandchild should NOT be updated
            assertEquals(TaskStatus.SUBMITTED, manager.getTasks().get("grandchild").getStatus());
        }

        @Test
        @DisplayName("set_priority should change the task's priority and update index")
        void testSetPrioritySingleTask() {
            manager.addTask(makeTask("t1", 1));
            manager.setPriority("t1", 10);
            assertEquals(10, manager.getTasks().get("t1").getPriority());
            assertTrue(manager.getPriorityIndex().get(10).contains("t1"));
            assertFalse(manager.getPriorityIndex().getOrDefault(1, List.of()).contains("t1"));
        }

        @Test
        @DisplayName("set_priority should accept string priority and convert to int")
        void testSetPriorityStringConversion() {
            manager.addTask(makeTask("t1", 1));
            manager.setPriority("t1", "7");
            assertEquals(7, manager.getTasks().get("t1").getPriority());
        }

        @Test
        @DisplayName("set_priority with with_children+is_recursive should update descendants")
        void testSetPriorityWithChildrenRecursive() {
            manager.addTask(makeTask("root", 1));
            manager.addTask(Task.builder("s1", "child", "analysis")
                .priority(1).status(TaskStatus.SUBMITTED).parentTaskId("root").build());
            manager.addTask(Task.builder("s1", "grandchild", "analysis")
                .priority(1).status(TaskStatus.SUBMITTED).parentTaskId("child").build());

            manager.setPriority("root", 5, true, true);
            assertEquals(5, manager.getTasks().get("root").getPriority());
            assertEquals(5, manager.getTasks().get("child").getPriority());
            assertEquals(5, manager.getTasks().get("grandchild").getPriority());
        }
    }

    // ==================== get_child_task ====================

    @Nested
    @DisplayName("GetChildTask Tests")
    class GetChildTaskTests {

        @Test
        @DisplayName("get_child_task (non-recursive) should return only direct children")
        void testGetDirectChildren() {
            manager.addTask(makeTask("root"));
            manager.addTask(makeTask("child1", "root"));
            manager.addTask(makeTask("child2", "root"));
            manager.addTask(makeTask("grandchild", "child1"));

            List<Task> children = manager.getChildTask("root", false);
            Set<String> ids = children.stream().map(Task::getTaskId).collect(Collectors.toSet());
            assertEquals(Set.of("child1", "child2"), ids);
        }

        @Test
        @DisplayName("get_child_task (recursive) should return all descendants")
        void testGetChildrenRecursive() {
            manager.addTask(makeTask("root"));
            manager.addTask(makeTask("child1", "root"));
            manager.addTask(makeTask("grandchild", "child1"));

            List<Task> children = manager.getChildTask("root", true);
            Set<String> ids = children.stream().map(Task::getTaskId).collect(Collectors.toSet());
            assertEquals(Set.of("child1", "grandchild"), ids);
        }

        @Test
        @DisplayName("get_child_task on a leaf (no children) should return empty list")
        void testGetChildrenOfLeafTask() {
            manager.addTask(makeTask("leaf"));
            List<Task> children = manager.getChildTask("leaf", false);
            assertTrue(children.isEmpty());
        }
    }

    // ==================== Concurrency Safety ====================

    @Nested
    @DisplayName("Concurrency Safety Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Multiple concurrent add_task/get_task calls should not cause partial reads")
        void testConcurrentAddAndGet() throws Exception {
            int numTasks = 50;

            CompletableFuture<Void> addFuture = CompletableFuture.runAsync(() -> {
                for (int i = 0; i < numTasks; i++) {
                    manager.addTask(makeTask("concurrent_" + i, "cs", "analysis",
                        1, TaskStatus.SUBMITTED, null, null));
                }
            });

            CompletableFuture<Void> readFuture = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < 20; j++) {
                    List<Task> tasks = manager.getTask(TaskFilter.builder().sessionId("cs").build());
                    assertTrue(tasks.size() >= 0);
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });

            CompletableFuture.allOf(addFuture, readFuture).join();
            List<Task> finalTasks = manager.getTask(TaskFilter.builder().sessionId("cs").build());
            assertEquals(numTasks, finalTasks.size());
        }

        @Test
        @DisplayName("Concurrent status updates should all succeed without corruption")
        void testConcurrentStatusUpdates() throws Exception {
            for (int i = 0; i < 10; i++) {
                manager.addTask(makeTask("ct_" + i));
            }

            CompletableFuture<Void> batch1 = CompletableFuture.runAsync(() -> {
                for (int i = 0; i < 5; i++) {
                    manager.updateTaskStatus("ct_" + i, TaskStatus.WORKING);
                }
            });

            CompletableFuture<Void> batch2 = CompletableFuture.runAsync(() -> {
                for (int i = 5; i < 10; i++) {
                    manager.updateTaskStatus("ct_" + i, TaskStatus.COMPLETED);
                }
            });

            CompletableFuture.allOf(batch1, batch2).join();
            long working = manager.getTasks().values().stream()
                .filter(t -> t.getStatus() == TaskStatus.WORKING).count();
            long completed = manager.getTasks().values().stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
            assertEquals(5, working);
            assertEquals(5, completed);
        }
    }

    // ==================== TaskFilter Validation ====================

    @Nested
    @DisplayName("TaskFilter Validation Tests")
    class FilterValidationTests {

        @Test
        @DisplayName("TaskFilter with all None fields and is_root=false should raise")
        void testAllNoneFilterRaises() {
            assertThrows(BaseError.class, () -> TaskFilter.builder().build());
        }

        @Test
        @DisplayName("TaskFilter with at least one non-None field should be valid")
        void testSingleFieldFilterValid() {
            TaskFilter f = TaskFilter.builder().taskId("t1").build();
            assertEquals("t1", f.getTaskId());
        }

        @Test
        @DisplayName("TaskFilter with is_root=True (all others None) should be valid")
        void testIsRootOnlyFilterValid() {
            TaskFilter f = TaskFilter.builder().isRoot(true).build();
            assertTrue(f.isRoot());
        }

        @Test
        @DisplayName("TaskFilter with only status set should be valid")
        void testStatusOnlyFilterValid() {
            TaskFilter f = TaskFilter.builder().status(TaskStatus.SUBMITTED).build();
            assertEquals(TaskStatus.SUBMITTED, f.getStatus());
        }

        @Test
        @DisplayName("TaskFilter with multiple fields set should be valid")
        void testCombinedFiltersValid() {
            TaskFilter f = TaskFilter.builder().sessionId("s1").status(TaskStatus.WORKING).priority(5).build();
            assertEquals("s1", f.getSessionId());
            assertEquals(5, f.getPriority());
        }
    }
}

