/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.controller;

import com.openjiuwen.core.controller.ControllerConfig;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for TaskManager.
 *
 * <p>Mirrors Python's tests/unit_tests/core/controller/test_task_manager.py.</p>
 */
@DisplayName("TestTaskManager")
class TestTaskManager {

    private TaskManager taskManager;
    private Task sampleTask;
    private List<Task> sampleTasks;

    @BeforeEach
    void setUp() {
        ControllerConfig config = new ControllerConfig();
        config.setDefaultTaskPriority(1);
        taskManager = new TaskManager(config);

        sampleTask = createTask("session1", "task1", "test_task", "Test task", 1, TaskStatus.SUBMITTED);
        sampleTasks = List.of(
                createTask("session1", "task1", "test_task", "Task 1", 1, TaskStatus.SUBMITTED),
                createTask("session1", "task2", "test_task", "Task 2", 2, TaskStatus.WORKING),
                createTask("session2", "task3", "test_task", "Task 3", 1, TaskStatus.COMPLETED));
    }

    private static Task createTask(String sessionId, String taskId, String taskType,
                                   String description, int priority, TaskStatus status) {
        Task task = new Task(sessionId, taskId, taskType);
        task.setDescription(description);
        task.setPriority(priority);
        task.setStatus(status);
        return task;
    }

    @Nested
    @DisplayName("Add Task Tests")
    class AddTaskTests {

        @Test
        @DisplayName("Test adding a single task")
        void testAddSingleTask() {
            taskManager.addTask(sampleTask);
            List<Task> result = taskManager.getTask(TaskFilter.byTaskId("task1"));
            assertEquals(1, result.size());
            assertEquals("task1", result.get(0).getTaskId());
        }

        @Test
        @DisplayName("Test adding multiple tasks at once")
        void testAddMultipleTasks() {
            taskManager.addTask(sampleTasks);
            assertEquals(3, taskManager.getTask(null).size());
        }

        @Test
        @DisplayName("Test adding a task with a parent task")
        void testAddTaskWithParent() {
            Task parent = createTask("session1", "parent_task", "test_task", "Parent", 1, TaskStatus.SUBMITTED);
            taskManager.addTask(parent);
            sampleTask.setParentTaskId("parent_task");
            taskManager.addTask(sampleTask);

            List<Task> children = taskManager.getChildTask("parent_task", false);
            assertEquals(1, children.size());
            assertEquals("task1", children.get(0).getTaskId());
        }
    }

    @Nested
    @DisplayName("Get Task Tests")
    class GetTaskTests {

        @Test
        @DisplayName("Test getting a task by ID")
        void testGetTaskById() {
            taskManager.addTask(sampleTask);
            List<Task> result = taskManager.getTask(TaskFilter.byTaskId("task1"));
            assertEquals(1, result.size());
            assertEquals("task1", result.get(0).getTaskId());
        }

        @Test
        @DisplayName("Test getting tasks by ID list")
        void testGetTaskByIdList() {
            taskManager.addTask(sampleTasks);
            List<Task> result = taskManager.getTask(TaskFilter.byTaskIds(List.of("task1", "task2")));
            assertEquals(Set.of("task1", "task2"),
                    result.stream().map(Task::getTaskId).collect(Collectors.toSet()));
        }

        @Test
        @DisplayName("Test getting tasks by session ID")
        void testGetTaskBySessionId() {
            taskManager.addTask(sampleTasks);
            List<Task> result = taskManager.getTask(TaskFilter.bySessionId("session1"));
            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(task -> "session1".equals(task.getSessionId())));
        }

        @Test
        @DisplayName("Test getting tasks by priority")
        void testGetTaskByPriority() {
            taskManager.addTask(sampleTasks);
            List<Task> result = taskManager.getTask(TaskFilter.builder().priority(1).build());
            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(task -> task.getPriority() == 1));
        }

        @Test
        @DisplayName("Test getting tasks by status")
        void testGetTaskByStatus() {
            taskManager.addTask(sampleTasks);
            List<Task> result = taskManager.getTask(TaskFilter.byStatus(TaskStatus.SUBMITTED));
            assertEquals(1, result.size());
            assertEquals(TaskStatus.SUBMITTED, result.get(0).getStatus());
        }

        @Test
        @DisplayName("Test getting tasks by user_id in metadata")
        void testGetTaskByUserId() {
            sampleTask.setMetadata(Map.of("user_id", "user1"));
            taskManager.addTask(sampleTask);
            List<Task> result = taskManager.getTask(TaskFilter.builder().userId("user1").build());
            assertEquals(1, result.size());
            assertEquals("task1", result.get(0).getTaskId());
        }

        @Test
        @DisplayName("Test getting tasks with combined filters")
        void testGetTaskWithCombinedFilters() {
            taskManager.addTask(sampleTasks);
            List<Task> result = taskManager.getTask(TaskFilter.builder()
                    .sessionId("session1")
                    .status(TaskStatus.WORKING)
                    .build());
            assertEquals(1, result.size());
            assertEquals("task2", result.get(0).getTaskId());
        }
    }

    @Nested
    @DisplayName("Update Task Tests")
    class UpdateTaskTests {

        @Test
        @DisplayName("Test updating task status")
        void testUpdateTaskStatus() {
            taskManager.addTask(sampleTask);
            taskManager.updateTaskStatus("task1", TaskStatus.WORKING);
            assertEquals(TaskStatus.WORKING, taskManager.getTask(TaskFilter.byTaskId("task1")).get(0).getStatus());
        }

        @Test
        @DisplayName("Test updating task priority")
        void testUpdateTaskPriority() {
            taskManager.addTask(sampleTask);
            taskManager.setPriority("task1", 5, false, false);
            assertEquals(5, taskManager.getTask(TaskFilter.byTaskId("task1")).get(0).getPriority());
        }

        @Test
        @DisplayName("Test updating task metadata")
        void testUpdateTaskMetadata() {
            taskManager.addTask(sampleTask);
            sampleTask.setMetadata(Map.of("updated", true));
            assertTrue(taskManager.updateTask(sampleTask));
            assertEquals(true, taskManager.getTask(TaskFilter.byTaskId("task1")).get(0).getMetadata().get("updated"));
        }
    }

    @Nested
    @DisplayName("Delete Task Tests")
    class DeleteTaskTests {

        @Test
        @DisplayName("Test deleting a task by ID")
        void testDeleteTaskById() {
            taskManager.addTask(sampleTask);
            taskManager.removeTask(TaskFilter.byTaskId("task1"));
            assertTrue(taskManager.getTask(null).isEmpty());
        }

        @Test
        @DisplayName("Test deleting tasks by session ID")
        void testDeleteTaskBySessionId() {
            taskManager.addTask(sampleTasks);
            taskManager.removeTask(TaskFilter.bySessionId("session1"));
            List<Task> remaining = taskManager.getTask(null);
            assertEquals(1, remaining.size());
            assertEquals("task3", remaining.get(0).getTaskId());
        }

        @Test
        @DisplayName("Test deleting all tasks")
        void testDeleteAllTasks() {
            taskManager.addTask(sampleTasks);
            taskManager.clearState();
            assertTrue(taskManager.getTask(null).isEmpty());
        }
    }

    @Nested
    @DisplayName("Hierarchy Tests")
    class HierarchyTests {

        @Test
        @DisplayName("Test getting child tasks")
        void testGetChildTasks() {
            Task parent = createTask("session1", "parent", "test_task", "Parent", 1, TaskStatus.SUBMITTED);
            Task child1 = createTask("session1", "child1", "test_task", "Child 1", 1, TaskStatus.SUBMITTED);
            Task child2 = createTask("session1", "child2", "test_task", "Child 2", 1, TaskStatus.SUBMITTED);
            child1.setParentTaskId("parent");
            child2.setParentTaskId("parent");
            taskManager.addTask(List.of(parent, child1, child2));

            List<Task> result = taskManager.getChildTask("parent", false);
            assertEquals(Set.of("child1", "child2"),
                    result.stream().map(Task::getTaskId).collect(Collectors.toSet()));
        }

        @Test
        @DisplayName("Test getting parent task")
        void testGetParentTask() {
            Task parent = createTask("session1", "parent", "test_task", "Parent", 1, TaskStatus.SUBMITTED);
            Task child = createTask("session1", "child", "test_task", "Child", 1, TaskStatus.SUBMITTED);
            child.setParentTaskId("parent");
            taskManager.addTask(List.of(parent, child));

            Task persistedChild = taskManager.getTask(TaskFilter.byTaskId("child")).get(0);
            assertEquals("parent", persistedChild.getParentTaskId());
        }

        @Test
        @DisplayName("Test getting task hierarchy")
        void testGetTaskHierarchy() {
            Task parent = createTask("session1", "parent", "test_task", "Parent", 1, TaskStatus.SUBMITTED);
            Task child = createTask("session1", "child", "test_task", "Child", 1, TaskStatus.SUBMITTED);
            Task grandchild = createTask("session1", "grandchild", "test_task", "Grandchild", 1, TaskStatus.SUBMITTED);
            child.setParentTaskId("parent");
            grandchild.setParentTaskId("child");
            taskManager.addTask(List.of(parent, child, grandchild));

            List<Task> result = taskManager.getTask(TaskFilter.builder().taskId("parent").withChildren(true).build());
            assertEquals(Set.of("parent", "child", "grandchild"),
                    result.stream().map(Task::getTaskId).collect(Collectors.toSet()));
        }
    }

    @Nested
    @DisplayName("Priority Tests")
    class PriorityTests {

        @Test
        @DisplayName("Test getting highest priority tasks")
        void testGetHighestPriorityTasks() {
            taskManager.addTask(sampleTasks);
            List<Task> result = taskManager.popTask(TaskFilter.byHighestPriority());
            assertEquals(1, result.size());
            assertEquals(2, result.get(0).getPriority());
        }

        @Test
        @DisplayName("Test getting lowest priority tasks")
        void testGetLowestPriorityTasks() {
            taskManager.addTask(sampleTasks);
            List<Task> result = taskManager.getTask(TaskFilter.builder().priority(1).build());
            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(task -> task.getPriority() == 1));
        }

        @Test
        @DisplayName("Test ordering tasks by priority")
        void testOrderTasksByPriority() {
            taskManager.addTask(sampleTasks);
            List<Task> ordered = new ArrayList<>(taskManager.getTask(null));
            ordered.sort((left, right) -> Integer.compare(right.getPriority(), left.getPriority()));

            assertEquals(2, ordered.get(0).getPriority());
            assertEquals(1, ordered.get(ordered.size() - 1).getPriority());
        }
    }

    @Nested
    @DisplayName("Concurrent Operations Tests")
    class ConcurrentOperationsTests {

        @Test
        @DisplayName("Test concurrent task additions")
        void testConcurrentTaskAdditions() throws Exception {
            ExecutorService executor = Executors.newFixedThreadPool(8);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < 40; i++) {
                final int index = i;
                futures.add(executor.submit(() -> taskManager.addTask(
                        createTask("session" + (index % 3), "task_" + index, "test_task",
                                "Task " + index, index % 5, TaskStatus.SUBMITTED))));
            }

            for (Future<?> future : futures) {
                future.get();
            }
            executor.shutdown();

            assertEquals(40, taskManager.getTask(null).size());
        }

        @Test
        @DisplayName("Test concurrent task reads and writes")
        void testConcurrentTaskReadsAndWrites() throws Exception {
            taskManager.addTask(sampleTasks);

            ExecutorService executor = Executors.newFixedThreadPool(3);
            List<Future<?>> futures = List.of(
                    executor.submit(() -> {
                        Task task = taskManager.getTask(TaskFilter.byTaskId("task1")).get(0);
                        task.setStatus(TaskStatus.WORKING);
                        taskManager.updateTask(task);
                    }),
                    executor.submit(() -> {
                        Task task = taskManager.getTask(TaskFilter.byTaskId("task2")).get(0);
                        task.setStatus(TaskStatus.COMPLETED);
                        taskManager.updateTask(task);
                    }),
                    executor.submit(() -> taskManager.getTask(TaskFilter.byTaskId("task3"))));

            for (Future<?> future : futures) {
                future.get();
            }
            executor.shutdown();

            assertEquals(TaskStatus.WORKING, taskManager.getTask(TaskFilter.byTaskId("task1")).get(0).getStatus());
            assertEquals(TaskStatus.COMPLETED, taskManager.getTask(TaskFilter.byTaskId("task2")).get(0).getStatus());
        }

        @Test
        @DisplayName("Test thread-safe task operations")
        void testThreadSafeTaskOperations() throws Exception {
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

            for (Future<?> future : futures) {
                future.get();
            }
            executor.shutdown();

            assertTrue(taskManager.getTask(null).stream().allMatch(task -> task.getStatus() == TaskStatus.COMPLETED));
        }
    }

    @Nested
    @DisplayName("State Management Tests")
    class StateManagementTests {

        @Test
        @DisplayName("State can be saved and restored")
        void testStatePersistence() {
            taskManager.addTask(sampleTasks);
            TaskManagerState state = taskManager.getState();
            TaskManager restored = new TaskManager(new ControllerConfig());
            restored.loadState(state);

            assertEquals(3, restored.getTask(null).size());
        }

        @Test
        @DisplayName("Get state returns a consistent snapshot")
        void testGetState() {
            taskManager.addTask(sampleTasks);
            TaskManagerState state = taskManager.getState();

            assertNotNull(state);
            assertEquals(3, state.getTasks().size());
            assertFalse(state.getPriorityIndex().isEmpty());
        }

        @Test
        @DisplayName("Highest priority filter is rejected by getTask")
        void testGetHighestPriorityFilterRejected() {
            taskManager.addTask(sampleTasks);
            assertThrows(Exception.class, () -> taskManager.getTask(TaskFilter.byHighestPriority()));
        }
    }
}
