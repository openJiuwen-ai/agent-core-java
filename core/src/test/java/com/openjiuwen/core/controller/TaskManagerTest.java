/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.controller.modules.TaskFilter;
import com.openjiuwen.core.controller.modules.TaskManager;
import com.openjiuwen.core.controller.modules.TaskManagerState;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Unit tests for TaskManager.
 * Translated from Python tests/unit_tests/core/controller/test_task_manager.py
 */
class TaskManagerTest {
    private TaskManager taskManager;
    private Task sampleTask;
    private List<Task> sampleTasks;

    @BeforeEach
    void setUp() {
        ControllerConfig config = new ControllerConfig();
        config.setDefaultTaskPriority(1);
        taskManager = new TaskManager(config);

        sampleTask = createTask("session1", "task1", "test_task", "Test task", 1, TaskStatus.SUBMITTED);

        sampleTasks = List.of(createTask("session1", "task1", "test_task", "Task 1", 1, TaskStatus.SUBMITTED),
                createTask("session1", "task2", "test_task", "Task 2", 2, TaskStatus.WORKING),
                createTask("session2", "task3", "test_task", "Task 3", 1, TaskStatus.COMPLETED));
    }

    private Task createTask(String sessionId, String taskId, String taskType, String description, int priority,
            TaskStatus status) {
        Task task = new Task(sessionId, taskId, taskType);
        task.setDescription(description);
        task.setPriority(priority);
        task.setStatus(status);
        return task;
    }

    // ==================== Add Task Tests ====================

    @Nested
    @DisplayName("Add Task Tests")
    class AddTaskTests {
        @Test
        @DisplayName("test adding a single task")
        void testAddSingleTask() {
            taskManager.addTask(sampleTask);
            List<Task> result = taskManager.getTask(TaskFilter.byTaskId("task1"));
            assertEquals(1, result.size());
            assertEquals("task1", result.get(0).getTaskId());
        }

        @Test
        @DisplayName("test adding multiple tasks at once")
        void testAddMultipleTasks() {
            taskManager.addTask(sampleTasks);
            List<Task> all = taskManager.getTask(null);
            assertEquals(3, all.size());
        }

        @Test
        @DisplayName("test adding a task with a parent task")
        void testAddTaskWithParent() {
            Task parentTask =
                createTask("session1", "parent_task", "test_task", "Parent task", 1, TaskStatus.SUBMITTED);
            taskManager.addTask(parentTask);

            sampleTask.setParentTaskId("parent_task");
            taskManager.addTask(sampleTask);

            List<Task> children = taskManager.getChildTask("parent_task", false);
            assertEquals(1, children.size());
            assertEquals("task1", children.get(0).getTaskId());
        }

        @Test
        @DisplayName("test adding duplicate task throws exception")
        void testAddDuplicateTaskThrows() {
            taskManager.addTask(sampleTask);
            Task duplicate = createTask("session1", "task1", "test_task", "Duplicate", 1, TaskStatus.SUBMITTED);
            assertThrows(Exception.class, () -> taskManager.addTask(duplicate));
        }
    }

    // ==================== Get Task Tests ====================

    @Nested
    @DisplayName("Get Task Tests")
    class GetTaskTests {
        @Test
        @DisplayName("test getting a task by ID")
        void testGetTaskById() {
            taskManager.addTask(sampleTask);
            List<Task> result = taskManager.getTask(TaskFilter.byTaskId("task1"));
            assertEquals(1, result.size());
            assertEquals("task1", result.get(0).getTaskId());
        }

        @Test
        @DisplayName("test getting tasks by ID list")
        void testGetTaskByIdList() {
            taskManager.addTask(sampleTasks);
            List<Task> result = taskManager.getTask(TaskFilter.byTaskIds(List.of("task1", "task2")));
            assertEquals(2, result.size());
            Set<String> ids = result.stream().map(Task::getTaskId).collect(Collectors.toSet());
            assertEquals(Set.of("task1", "task2"), ids);
        }

        @Test
        @DisplayName("test getting tasks by session ID")
        void testGetTaskBySessionId() {
            taskManager.addTask(sampleTasks);
            List<Task> result = taskManager.getTask(TaskFilter.bySessionId("session1"));
            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(t -> "session1".equals(t.getSessionId())));
        }

        @Test
        @DisplayName("test getting tasks by priority")
        void testGetTaskByPriority() {
            taskManager.addTask(sampleTasks);
            List<Task> result = taskManager.getTask(TaskFilter.builder().priority(1).build());
            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(t -> t.getPriority() == 1));
        }

        @Test
        @DisplayName("test getting tasks by status")
        void testGetTaskByStatus() {
            taskManager.addTask(sampleTasks);
            List<Task> result = taskManager.getTask(TaskFilter.byStatus(TaskStatus.SUBMITTED));
            assertEquals(1, result.size());
            assertEquals(TaskStatus.SUBMITTED, result.get(0).getStatus());
        }

        @Test
        @DisplayName("test getting tasks by user_id in metadata")
        void testGetTaskByUserId() {
            sampleTask.setMetadata(Map.of("user_id", "user1"));
            taskManager.addTask(sampleTask);
            List<Task> result = taskManager.getTask(TaskFilter.builder().userId("user1").build());
            assertEquals(1, result.size());
            assertEquals("task1", result.get(0).getTaskId());
        }

        @Test
        @DisplayName("test getting root tasks")
        void testGetRootTasks() {
            taskManager.addTask(sampleTasks);
            Task childTask = createTask("session1", "child_task", "test_task", "Child task", 1, TaskStatus.SUBMITTED);
            childTask.setParentTaskId("task1");
            taskManager.addTask(childTask);

            List<Task> result = taskManager.getTask(TaskFilter.byRoot());
            assertEquals(3, result.size());
            Set<String> ids = result.stream().map(Task::getTaskId).collect(Collectors.toSet());
            assertFalse(ids.contains("child_task"));
        }

        @Test
        @DisplayName("test getting tasks with children")
        void testGetTaskWithChildren() {
            Task parent = createTask("session1", "parent", "test_task", "Parent", 1, TaskStatus.SUBMITTED);
            Task child1 = createTask("session1", "child1", "test_task", "Child 1", 1, TaskStatus.SUBMITTED);
            child1.setParentTaskId("parent");
            Task child2 = createTask("session1", "child2", "test_task", "Child 2", 1, TaskStatus.SUBMITTED);
            child2.setParentTaskId("parent");

            taskManager.addTask(List.of(parent, child1, child2));

            List<Task> result = taskManager.getTask(TaskFilter.builder().taskId("parent").withChildren(true).build());
            assertEquals(3, result.size());
            Set<String> ids = result.stream().map(Task::getTaskId).collect(Collectors.toSet());
            assertEquals(Set.of("parent", "child1", "child2"), ids);
        }

        @Test
        @DisplayName("test getting tasks with recursive children")
        void testGetTaskWithRecursiveChildren() {
            Task parent = createTask("session1", "parent", "test_task", "Parent", 1, TaskStatus.SUBMITTED);
            Task child = createTask("session1", "child", "test_task", "Child", 1, TaskStatus.SUBMITTED);
            child.setParentTaskId("parent");
            Task grandchild = createTask("session1", "grandchild", "test_task", "Grandchild", 1, TaskStatus.SUBMITTED);
            grandchild.setParentTaskId("child");

            taskManager.addTask(List.of(parent, child, grandchild));

            List<Task> result = taskManager.getTask(TaskFilter.builder().taskId("parent").withChildren(true).build());
            assertEquals(3, result.size());
            Set<String> ids = result.stream().map(Task::getTaskId).collect(Collectors.toSet());
            assertEquals(Set.of("parent", "child", "grandchild"), ids);
        }

        @Test
        @DisplayName("test getting all tasks when no filter is provided")
        void testGetAllTasks() {
            taskManager.addTask(sampleTasks);
            List<Task> result = taskManager.getTask(null);
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("test that get_task raises error for 'highest' priority")
        void testGetTaskHighestPriorityError() {
            taskManager.addTask(sampleTasks);
            assertThrows(Exception.class, () -> taskManager.getTask(TaskFilter.byHighestPriority()));
        }
    }

    // ==================== Pop Task Tests ====================

    @Nested
    @DisplayName("Pop Task Tests")
    class PopTaskTests {
        @Test
        @DisplayName("test popping a task by ID")
        void testPopTaskById() {
            taskManager.addTask(sampleTask);
            List<Task> result = taskManager.popTask(TaskFilter.byTaskId("task1"));
            assertEquals(1, result.size());
            assertEquals("task1", result.get(0).getTaskId());
            // Verify task is removed
            List<Task> remaining = taskManager.getTask(null);
            assertTrue(remaining.isEmpty());
        }

        @Test
        @DisplayName("test popping task with highest priority")
        void testPopTaskHighestPriority() {
            taskManager.addTask(sampleTasks);
            List<Task> result = taskManager.popTask(TaskFilter.byHighestPriority());
            assertEquals(1, result.size());
            assertEquals(2, result.get(0).getPriority());
        }

        @Test
        @DisplayName("test popping from empty task manager")
        void testPopTaskEmpty() {
            List<Task> result = taskManager.popTask(TaskFilter.byHighestPriority());
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("test that pop_task raises error when task_filter is None")
        void testPopTaskNoneFilterError() {
            assertThrows(Exception.class, () -> taskManager.popTask(null));
        }
    }

    // ==================== Update Task Tests ====================

    @Nested
    @DisplayName("Update Task Tests")
    class UpdateTaskTests {
        @Test
        @DisplayName("test updating a task")
        void testUpdateTask() {
            taskManager.addTask(sampleTask);
            sampleTask.setDescription("Updated description");
            sampleTask.setStatus(TaskStatus.WORKING);
            boolean success = taskManager.updateTask(sampleTask);
            assertTrue(success);

            List<Task> result = taskManager.getTask(TaskFilter.byTaskId("task1"));
            assertEquals("Updated description", result.get(0).getDescription());
            assertEquals(TaskStatus.WORKING, result.get(0).getStatus());
        }

        @Test
        @DisplayName("test updating a non-existent task")
        void testUpdateNonexistentTask() {
            boolean success = taskManager.updateTask(sampleTask);
            assertFalse(success);
        }
    }

    // ==================== Remove Task Tests ====================

    @Nested
    @DisplayName("Remove Task Tests")
    class RemoveTaskTests {
        @Test
        @DisplayName("test removing a task by ID")
        void testRemoveTaskById() {
            taskManager.addTask(sampleTask);
            taskManager.removeTask(TaskFilter.byTaskId("task1"));
            List<Task> remaining = taskManager.getTask(null);
            assertTrue(remaining.isEmpty());
        }

        @Test
        @DisplayName("test removing a task with children")
        void testRemoveTaskWithChildren() {
            Task parent = createTask("session1", "parent", "test_task", "Parent", 1, TaskStatus.SUBMITTED);
            Task child = createTask("session1", "child", "test_task", "Child", 1, TaskStatus.SUBMITTED);
            child.setParentTaskId("parent");
            taskManager.addTask(List.of(parent, child));

            taskManager.removeTask(TaskFilter.builder().taskId("parent").withChildren(true).build());
            List<Task> remaining = taskManager.getTask(null);
            assertTrue(remaining.isEmpty());
        }

        @Test
        @DisplayName("test removing tasks by session ID")
        void testRemoveTaskBySessionId() {
            taskManager.addTask(sampleTasks);
            taskManager.removeTask(TaskFilter.bySessionId("session1"));
            List<Task> remaining = taskManager.getTask(null);
            assertEquals(1, remaining.size());
            assertEquals("task3", remaining.get(0).getTaskId());
        }

        @Test
        @DisplayName("test removing tasks by status")
        void testRemoveTaskByStatus() {
            taskManager.addTask(sampleTasks);
            taskManager.removeTask(TaskFilter.byStatus(TaskStatus.COMPLETED));
            List<Task> remaining = taskManager.getTask(null);
            assertEquals(2, remaining.size());
            assertFalse(remaining.stream().anyMatch(t -> "task3".equals(t.getTaskId())));
        }

        @Test
        @DisplayName("test that remove_task raises error when task_filter is None")
        void testRemoveTaskNoneFilterError() {
            assertThrows(Exception.class, () -> taskManager.removeTask(null));
        }

        @Test
        @DisplayName("test that remove_task raises error for 'highest' priority")
        void testRemoveTaskHighestPriorityError() {
            taskManager.addTask(sampleTasks);
            assertThrows(Exception.class, () -> taskManager.removeTask(TaskFilter.byHighestPriority()));
        }
    }

    // ==================== Get Child Task Tests ====================

    @Nested
    @DisplayName("Get Child Task Tests")
    class GetChildTaskTests {
        @Test
        @DisplayName("test getting child tasks")
        void testGetChildTask() {
            Task parent = createTask("session1", "parent", "test_task", "Parent", 1, TaskStatus.SUBMITTED);
            Task child1 = createTask("session1", "child1", "test_task", "Child 1", 1, TaskStatus.SUBMITTED);
            child1.setParentTaskId("parent");
            Task child2 = createTask("session1", "child2", "test_task", "Child 2", 1, TaskStatus.SUBMITTED);
            child2.setParentTaskId("parent");

            taskManager.addTask(List.of(parent, child1, child2));
            List<Task> result = taskManager.getChildTask("parent", false);
            assertEquals(2, result.size());
            Set<String> ids = result.stream().map(Task::getTaskId).collect(Collectors.toSet());
            assertEquals(Set.of("child1", "child2"), ids);
        }

        @Test
        @DisplayName("test getting child tasks recursively")
        void testGetChildTaskRecursive() {
            Task parent = createTask("session1", "parent", "test_task", "Parent", 1, TaskStatus.SUBMITTED);
            Task child = createTask("session1", "child", "test_task", "Child", 1, TaskStatus.SUBMITTED);
            child.setParentTaskId("parent");
            Task grandchild = createTask("session1", "grandchild", "test_task", "Grandchild", 1, TaskStatus.SUBMITTED);
            grandchild.setParentTaskId("child");

            taskManager.addTask(List.of(parent, child, grandchild));
            List<Task> result = taskManager.getChildTask("parent", true);
            assertEquals(2, result.size());
            Set<String> ids = result.stream().map(Task::getTaskId).collect(Collectors.toSet());
            assertEquals(Set.of("child", "grandchild"), ids);
        }
    }

    // ==================== Update Task Status Tests ====================

    @Nested
    @DisplayName("Update Task Status Tests")
    class UpdateTaskStatusTests {
        @Test
        @DisplayName("test updating task status")
        void testUpdateTaskStatus() {
            taskManager.addTask(sampleTask);
            taskManager.updateTaskStatus("task1", TaskStatus.WORKING);
            List<Task> result = taskManager.getTask(TaskFilter.byTaskId("task1"));
            assertEquals(TaskStatus.WORKING, result.get(0).getStatus());
        }

        @Test
        @DisplayName("test updating task status with children")
        void testUpdateTaskStatusWithChildren() {
            Task parent = createTask("session1", "parent", "test_task", "Parent", 1, TaskStatus.SUBMITTED);
            Task child = createTask("session1", "child", "test_task", "Child", 1, TaskStatus.SUBMITTED);
            child.setParentTaskId("parent");

            taskManager.addTask(List.of(parent, child));
            taskManager.updateTaskStatus(List.of("parent"), TaskStatus.WORKING, true, false, null);

            List<Task> pResult = taskManager.getTask(TaskFilter.byTaskId("parent"));
            List<Task> cResult = taskManager.getTask(TaskFilter.byTaskId("child"));
            assertEquals(TaskStatus.WORKING, pResult.get(0).getStatus());
            assertEquals(TaskStatus.WORKING, cResult.get(0).getStatus());
        }

        @Test
        @DisplayName("test updating task status recursively")
        void testUpdateTaskStatusRecursive() {
            Task parent = createTask("session1", "parent", "test_task", "Parent", 1, TaskStatus.SUBMITTED);
            Task child = createTask("session1", "child", "test_task", "Child", 1, TaskStatus.SUBMITTED);
            child.setParentTaskId("parent");
            Task grandchild = createTask("session1", "grandchild", "test_task", "Grandchild", 1, TaskStatus.SUBMITTED);
            grandchild.setParentTaskId("child");

            taskManager.addTask(List.of(parent, child, grandchild));
            taskManager.updateTaskStatus(List.of("parent"), TaskStatus.WORKING, true, true, null);

            for (String id : List.of("parent", "child", "grandchild")) {
                List<Task> r = taskManager.getTask(TaskFilter.byTaskId(id));
                assertEquals(TaskStatus.WORKING, r.get(0).getStatus(), id + " should have WORKING status");
            }
        }

        @Test
        @DisplayName("test updating task status with error message for FAILED")
        void testUpdateTaskStatusWithErrorMessage() {
            taskManager.addTask(sampleTask);
            taskManager.updateTaskStatus("task1", TaskStatus.FAILED, "Something went wrong");
            List<Task> result = taskManager.getTask(TaskFilter.byTaskId("task1"));
            assertEquals(TaskStatus.FAILED, result.get(0).getStatus());
            assertEquals("Something went wrong", result.get(0).getErrorMessage());
        }
    }

    // ==================== Set Priority Tests ====================

    @Nested
    @DisplayName("Set Priority Tests")
    class SetPriorityTests {
        @Test
        @DisplayName("test setting task priority")
        void testSetPriority() {
            taskManager.addTask(sampleTask);
            taskManager.setPriority("task1", 5, false, false);
            List<Task> result = taskManager.getTask(TaskFilter.byTaskId("task1"));
            assertEquals(5, result.get(0).getPriority());
        }

        @Test
        @DisplayName("test setting priority with children")
        void testSetPriorityWithChildren() {
            Task parent = createTask("session1", "parent", "test_task", "Parent", 1, TaskStatus.SUBMITTED);
            Task child = createTask("session1", "child", "test_task", "Child", 1, TaskStatus.SUBMITTED);
            child.setParentTaskId("parent");

            taskManager.addTask(List.of(parent, child));
            taskManager.setPriority("parent", 5, true, false);

            List<Task> pResult = taskManager.getTask(TaskFilter.byTaskId("parent"));
            List<Task> cResult = taskManager.getTask(TaskFilter.byTaskId("child"));
            assertEquals(5, pResult.get(0).getPriority());
            assertEquals(5, cResult.get(0).getPriority());
        }

        @Test
        @DisplayName("test setting priority recursively")
        void testSetPriorityRecursive() {
            Task parent = createTask("session1", "parent", "test_task", "Parent", 1, TaskStatus.SUBMITTED);
            Task child = createTask("session1", "child", "test_task", "Child", 1, TaskStatus.SUBMITTED);
            child.setParentTaskId("parent");
            Task grandchild = createTask("session1", "grandchild", "test_task", "Grandchild", 1, TaskStatus.SUBMITTED);
            grandchild.setParentTaskId("child");

            taskManager.addTask(List.of(parent, child, grandchild));
            taskManager.setPriority("parent", 5, true, true);

            for (String id : List.of("parent", "child", "grandchild")) {
                List<Task> r = taskManager.getTask(TaskFilter.byTaskId(id));
                assertEquals(5, r.get(0).getPriority(), id + " should have priority 5");
            }
        }
    }

    // ==================== State Management Tests ====================

    @Nested
    @DisplayName("State Management Tests")
    class StateManagementTests {
        @Test
        @DisplayName("test getting task manager state")
        void testGetState() {
            taskManager.addTask(sampleTasks);
            TaskManagerState state = taskManager.getState();
            assertNotNull(state);
            assertEquals(3, state.getTasks().size());
            assertFalse(state.getPriorityIndex().isEmpty());
            assertFalse(state.getRootTasks().isEmpty());
        }

        @Test
        @DisplayName("test loading task manager state")
        void testLoadState() {
            taskManager.addTask(sampleTasks);
            TaskManagerState state = taskManager.getState();

            TaskManager newManager = new TaskManager(new ControllerConfig());
            newManager.loadState(state);

            List<Task> all = newManager.getTask(null);
            assertEquals(3, all.size());
            Set<String> ids = all.stream().map(Task::getTaskId).collect(Collectors.toSet());
            assertEquals(Set.of("task1", "task2", "task3"), ids);
        }

        @Test
        @DisplayName("test that state can be saved and restored correctly with hierarchical tasks")
        void testStatePersistence() {
            Task parent = createTask("session1", "parent", "test_task", "Parent", 1, TaskStatus.SUBMITTED);
            Task child = createTask("session1", "child", "test_task", "Child", 2, TaskStatus.WORKING);
            child.setParentTaskId("parent");

            taskManager.addTask(List.of(parent, child));
            TaskManagerState state = taskManager.getState();

            TaskManager newManager = new TaskManager(new ControllerConfig());
            newManager.loadState(state);

            List<Task> all = newManager.getTask(null);
            assertEquals(2, all.size());
            Set<String> ids = all.stream().map(Task::getTaskId).collect(Collectors.toSet());
            assertTrue(ids.contains("parent"));
            assertTrue(ids.contains("child"));
        }

        @Test
        @DisplayName("test clearState removes all data")
        void testClearState() {
            taskManager.addTask(sampleTasks);
            taskManager.clearState();
            List<Task> all = taskManager.getTask(null);
            assertTrue(all.isEmpty());
        }
    }

    // ==================== Parallel/Concurrent Operations Tests ====================

    @Nested
    @DisplayName("Parallel Operations Tests")
    class ParallelOperationsTests {
        @Test
        @DisplayName("test adding tasks concurrently")
        void testParallelAddTasks() throws Exception {
            int numTasks = 50;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < numTasks; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    Task task = createTask("session" + (idx % 5), "task_" + idx, "test_task", "Task " + idx, idx % 10,
                            TaskStatus.SUBMITTED);
                    taskManager.addTask(task);
                }));
            }

            for (Future<?> f : futures) {
                f.get();
            }
            executor.shutdown();

            List<Task> all = taskManager.getTask(null);
            assertEquals(numTasks, all.size());
        }

        @Test
        @DisplayName("test concurrent get and update operations")
        void testParallelGetAndUpdate() throws Exception {
            taskManager.addTask(sampleTasks);

            ExecutorService executor = Executors.newFixedThreadPool(3);
            List<Future<?>> futures = new ArrayList<>();

            futures.add(executor.submit(() -> {
                List<Task> tasks = taskManager.getTask(TaskFilter.byTaskId("task1"));
                if (!tasks.isEmpty()) {
                    tasks.get(0).setStatus(TaskStatus.WORKING);
                    taskManager.updateTask(tasks.get(0));
                }
            }));
            futures.add(executor.submit(() -> {
                List<Task> tasks = taskManager.getTask(TaskFilter.byTaskId("task2"));
                if (!tasks.isEmpty()) {
                    tasks.get(0).setStatus(TaskStatus.COMPLETED);
                    taskManager.updateTask(tasks.get(0));
                }
            }));
            futures.add(executor.submit(() -> {
                List<Task> tasks = taskManager.getTask(TaskFilter.byTaskId("task3"));
                if (!tasks.isEmpty()) {
                    tasks.get(0).setStatus(TaskStatus.FAILED);
                    tasks.get(0).setErrorMessage("test failure");
                    taskManager.updateTask(tasks.get(0));
                }
            }));

            for (Future<?> f : futures) {
                f.get();
            }
            executor.shutdown();

            assertEquals(TaskStatus.WORKING, taskManager.getTask(TaskFilter.byTaskId("task1")).get(0).getStatus());
            assertEquals(TaskStatus.COMPLETED, taskManager.getTask(TaskFilter.byTaskId("task2")).get(0).getStatus());
            assertEquals(TaskStatus.FAILED, taskManager.getTask(TaskFilter.byTaskId("task3")).get(0).getStatus());
        }

        @Test
        @DisplayName("test concurrent status updates on same tasks")
        void testParallelStatusUpdates() throws Exception {
            taskManager.addTask(sampleTasks);

            ExecutorService executor = Executors.newFixedThreadPool(3);
            List<Future<?>> futures = new ArrayList<>();

            for (String id : List.of("task1", "task2", "task3")) {
                futures.add(executor.submit(() -> {
                    for (int i = 0; i < 10; i++) {
                        taskManager.updateTaskStatus(id, TaskStatus.WORKING);
                        taskManager.updateTaskStatus(id, TaskStatus.COMPLETED);
                    }
                }));
            }

            for (Future<?> f : futures) {
                f.get();
            }
            executor.shutdown();

            // All should be COMPLETED after final iteration
            List<Task> all = taskManager.getTask(null);
            for (Task t : all) {
                assertEquals(TaskStatus.COMPLETED, t.getStatus());
            }
        }

        @Test
        @DisplayName("test concurrent add and remove operations")
        void testParallelAddAndRemove() throws Exception {
            int numTasks = 30;
            // First add all tasks
            for (int i = 0; i < numTasks; i++) {
                Task task = createTask("session1", "task_" + i, "test_task", "Task " + i, i % 5, TaskStatus.SUBMITTED);
                taskManager.addTask(task);
            }

            // Remove half concurrently
            ExecutorService executor = Executors.newFixedThreadPool(5);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < numTasks; i += 2) {
                final String taskId = "task_" + i;
                futures.add(executor.submit(() -> taskManager.removeTask(TaskFilter.byTaskId(taskId))));
            }

            for (Future<?> f : futures) {
                f.get();
            }
            executor.shutdown();

            List<Task> remaining = taskManager.getTask(null);
            assertEquals(numTasks / 2, remaining.size());
        }

        @Test
        @DisplayName("test concurrent priority updates")
        void testParallelPriorityUpdates() throws Exception {
            taskManager.addTask(sampleTasks);

            ExecutorService executor = Executors.newFixedThreadPool(3);
            List<Future<?>> futures = new ArrayList<>();

            futures.add(executor.submit(() -> taskManager.setPriority("task1", 10, false, false)));
            futures.add(executor.submit(() -> taskManager.setPriority("task2", 20, false, false)));
            futures.add(executor.submit(() -> taskManager.setPriority("task3", 30, false, false)));

            for (Future<?> f : futures) {
                f.get();
            }
            executor.shutdown();

            assertEquals(10, taskManager.getTask(TaskFilter.byTaskId("task1")).get(0).getPriority());
            assertEquals(20, taskManager.getTask(TaskFilter.byTaskId("task2")).get(0).getPriority());
            assertEquals(30, taskManager.getTask(TaskFilter.byTaskId("task3")).get(0).getPriority());
        }

        @Test
        @DisplayName("test concurrent pop operations")
        void testParallelPopOperations() throws Exception {
            // Add many tasks
            for (int i = 0; i < 20; i++) {
                Task task = createTask("session1", "task_" + i, "test_task", "Task " + i, i % 5, TaskStatus.SUBMITTED);
                taskManager.addTask(task);
            }

            ExecutorService executor = Executors.newFixedThreadPool(5);
            List<Future<List<Task>>> futures = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                futures.add(executor.submit(() -> taskManager.popTask(TaskFilter.byHighestPriority())));
            }

            List<Task> allPopped = new ArrayList<>();
            for (Future<List<Task>> f : futures) {
                allPopped.addAll(f.get());
            }
            executor.shutdown();

            // All popped tasks should have the highest priority
            if (!allPopped.isEmpty()) {
                int maxPriority = allPopped.stream().mapToInt(Task::getPriority).max().orElse(0);
                assertTrue(allPopped.stream().allMatch(t -> t.getPriority() == maxPriority));
            }
        }

        @Test
        @DisplayName("test concurrent get operations are consistent")
        void testParallelGetOperations() throws Exception {
            taskManager.addTask(sampleTasks);

            ExecutorService executor = Executors.newFixedThreadPool(10);
            List<Future<List<Task>>> futures = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                futures.add(executor.submit(() -> taskManager.getTask(null)));
            }

            for (Future<List<Task>> f : futures) {
                List<Task> result = f.get();
                assertEquals(3, result.size());
                Set<String> ids = result.stream().map(Task::getTaskId).collect(Collectors.toSet());
                assertEquals(Set.of("task1", "task2", "task3"), ids);
            }
            executor.shutdown();
        }

        @Test
        @DisplayName("test mixed concurrent operations")
        void testParallelMixedOperations() throws Exception {
            taskManager.addTask(sampleTasks);

            ExecutorService executor = Executors.newFixedThreadPool(5);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < 5; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    // Get tasks
                    taskManager.getTask(TaskFilter.bySessionId("session1"));
                    // Add a new task
                    Task newTask =
                        createTask("session1", "new_task_" + idx, "test_task", "New task", 5, TaskStatus.SUBMITTED);
                    taskManager.addTask(newTask);
                }));
            }

            for (Future<?> f : futures) {
                f.get();
            }
            executor.shutdown();

            List<Task> all = taskManager.getTask(null);
            assertTrue(all.size() >= 3, "Should have at least original 3 tasks");
        }
    }
}
