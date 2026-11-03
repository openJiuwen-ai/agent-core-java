/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskStatus;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.core.controller.test_task_manager} in
 * {@code tests/unit_tests/core/controller/test_task_manager.py}.</p>
 */
class ControllerTaskManagerPythonParityTest {
    private static final List<String> PYTHON_TESTS = List.of(
            "test_add_multiple_tasks",
            "test_add_single_task",
            "test_add_task_with_parent",
            "test_get_all_tasks",
            "test_get_child_task",
            "test_get_child_task_recursive",
            "test_get_root_tasks",
            "test_get_state",
            "test_get_task_by_id",
            "test_get_task_by_id_list",
            "test_get_task_by_priority",
            "test_get_task_by_session_id",
            "test_get_task_by_status",
            "test_get_task_by_user_id",
            "test_get_task_highest_priority_error",
            "test_get_task_with_children",
            "test_get_task_with_recursive_children",
            "test_load_state",
            "test_parallel_add_and_remove",
            "test_parallel_add_tasks",
            "test_parallel_get_and_update",
            "test_parallel_get_operations",
            "test_parallel_mixed_operations",
            "test_parallel_pop_operations",
            "test_parallel_priority_updates",
            "test_parallel_status_updates",
            "test_pop_task_by_id",
            "test_pop_task_empty",
            "test_pop_task_highest_priority",
            "test_pop_task_none_filter_error",
            "test_remove_task_by_id",
            "test_remove_task_by_session_id",
            "test_remove_task_by_status",
            "test_remove_task_highest_priority_error",
            "test_remove_task_no_filter_error",
            "test_remove_task_none_filter_error",
            "test_remove_task_promotes_children_to_root",
            "test_remove_task_with_children",
            "test_set_priority",
            "test_set_priority_recursive",
            "test_set_priority_string",
            "test_set_priority_with_children",
            "test_state_persistence",
            "test_update_nonexistent_task",
            "test_update_task",
            "test_update_task_status",
            "test_update_task_status_recursive",
            "test_update_task_status_with_children"
    );

    @TestFactory
    Collection<DynamicTest> pythonTaskManagerCases() {
        return PYTHON_TESTS.stream()
                .map(name -> DynamicTest.dynamicTest(name, () -> runPythonCase(name)))
                .toList();
    }

    private void runPythonCase(String name) throws Exception {
        switch (name) {
            case "test_add_single_task" -> addSingleTask();
            case "test_add_multiple_tasks" -> addMultipleTasks();
            case "test_add_task_with_parent" -> addTaskWithParent();
            case "test_get_task_by_id" -> getTaskById();
            case "test_get_task_by_id_list" -> getTaskByIdList();
            case "test_get_task_by_session_id" -> getTaskBySessionId();
            case "test_get_task_by_priority" -> getTaskByPriority();
            case "test_get_task_by_status" -> getTaskByStatus();
            case "test_get_task_by_user_id" -> getTaskByUserId();
            case "test_get_root_tasks" -> getRootTasks();
            case "test_get_task_with_children" -> getTaskWithChildren();
            case "test_get_task_with_recursive_children" -> getTaskWithRecursiveChildren();
            case "test_get_all_tasks" -> getAllTasks();
            case "test_get_task_highest_priority_error" -> getTaskHighestPriorityError();
            case "test_pop_task_by_id" -> popTaskById();
            case "test_pop_task_highest_priority" -> popTaskHighestPriority();
            case "test_pop_task_empty" -> popTaskEmpty();
            case "test_update_task" -> updateTask();
            case "test_update_nonexistent_task" -> updateNonexistentTask();
            case "test_remove_task_by_id" -> removeTaskById();
            case "test_remove_task_with_children" -> removeTaskWithChildren();
            case "test_remove_task_promotes_children_to_root" -> removeTaskPromotesChildrenToRoot();
            case "test_remove_task_by_session_id" -> removeTaskBySessionId();
            case "test_remove_task_by_status" -> removeTaskByStatus();
            case "test_remove_task_no_filter_error" -> removeTaskNoFilterError();
            case "test_remove_task_highest_priority_error" -> removeTaskHighestPriorityError();
            case "test_pop_task_none_filter_error" -> popTaskNoneFilterError();
            case "test_remove_task_none_filter_error" -> removeTaskNoneFilterError();
            case "test_get_child_task" -> getChildTask();
            case "test_get_child_task_recursive" -> getChildTaskRecursive();
            case "test_update_task_status" -> updateTaskStatus();
            case "test_update_task_status_with_children" -> updateTaskStatusWithChildren();
            case "test_update_task_status_recursive" -> updateTaskStatusRecursive();
            case "test_set_priority" -> setPriority();
            case "test_set_priority_string" -> setPriorityString();
            case "test_set_priority_with_children" -> setPriorityWithChildren();
            case "test_set_priority_recursive" -> setPriorityRecursive();
            case "test_get_state" -> getState();
            case "test_load_state" -> loadState();
            case "test_state_persistence" -> statePersistence();
            case "test_parallel_add_tasks" -> parallelAddTasks();
            case "test_parallel_get_and_update" -> parallelGetAndUpdate();
            case "test_parallel_status_updates" -> parallelStatusUpdates();
            case "test_parallel_add_and_remove" -> parallelAddAndRemove();
            case "test_parallel_priority_updates" -> parallelPriorityUpdates();
            case "test_parallel_pop_operations" -> parallelPopOperations();
            case "test_parallel_get_operations" -> parallelGetOperations();
            case "test_parallel_mixed_operations" -> parallelMixedOperations();
            default -> throw new IllegalArgumentException("Unhandled Python test: " + name);
        }
    }

    private void addSingleTask() {
        TaskManager manager = manager();
        Task task = sampleTask();

        manager.addTask(task);

        assertEquals(task.getTaskId(), only(manager.getTask(TaskFilter.byTaskId("task1"))).getTaskId());
    }

    private void addMultipleTasks() {
        TaskManager manager = manager();

        manager.addTask(sampleTasks());

        assertEquals(Set.of("task1", "task2", "task3"), ids(manager.getTask(null)));
    }

    private void addTaskWithParent() {
        TaskManager manager = manager();
        Task parent = task("session1", "parent_task", 1, TaskStatus.SUBMITTED, null);
        Task child = sampleTask();
        child.setParentTaskId("parent_task");

        manager.addTask(parent);
        manager.addTask(child);

        assertEquals(Set.of("task1"), ids(manager.getChildTask("parent_task", false)));
    }

    private void getTaskById() {
        TaskManager manager = managerWithSampleTask();

        List<Task> result = manager.getTask(TaskFilter.byTaskId("task1"));

        assertEquals(1, result.size());
        assertEquals("task1", result.get(0).getTaskId());
    }

    private void getTaskByIdList() {
        TaskManager manager = managerWithSampleTasks();

        List<Task> result = manager.getTask(TaskFilter.byTaskIds(List.of("task1", "task2")));

        assertEquals(Set.of("task1", "task2"), ids(result));
    }

    private void getTaskBySessionId() {
        TaskManager manager = managerWithSampleTasks();

        List<Task> result = manager.getTask(TaskFilter.bySessionId("session1"));

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(task -> "session1".equals(task.getSessionId())));
    }

    private void getTaskByPriority() {
        TaskManager manager = managerWithSampleTasks();

        List<Task> result = manager.getTask(TaskFilter.builder().priority(1).build());

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(task -> task.getPriority() == 1));
    }

    private void getTaskByStatus() {
        TaskManager manager = managerWithSampleTasks();

        List<Task> result = manager.getTask(TaskFilter.byStatus(TaskStatus.SUBMITTED));

        assertEquals(1, result.size());
        assertEquals(TaskStatus.SUBMITTED, result.get(0).getStatus());
    }

    private void getTaskByUserId() {
        TaskManager manager = manager();
        Task task = sampleTask();
        task.setMetadata(Map.of("user_id", "user1"));
        manager.addTask(task);

        List<Task> result = manager.getTask(TaskFilter.builder().userId("user1").build());

        assertEquals(1, result.size());
        assertEquals("task1", result.get(0).getTaskId());
    }

    private void getRootTasks() {
        TaskManager manager = managerWithSampleTasks();
        manager.addTask(task("session1", "child_task", 1, TaskStatus.SUBMITTED, "task1"));

        List<Task> result = manager.getTask(TaskFilter.byRoot());

        assertEquals(3, result.size());
        assertFalse(ids(result).contains("child_task"));
    }

    private void getTaskWithChildren() {
        TaskManager manager = managerWithParentChildren(false);

        List<Task> result = manager.getTask(TaskFilter.builder().taskId("parent").withChildren(true).build());

        assertEquals(Set.of("parent", "child1", "child2"), ids(result));
    }

    private void getTaskWithRecursiveChildren() {
        TaskManager manager = managerWithParentChildGrandchild();

        List<Task> result = manager.getTask(TaskFilter.builder().taskId("parent").withChildren(true).build());

        assertEquals(Set.of("parent", "child", "grandchild"), ids(result));
    }

    private void getAllTasks() {
        TaskManager manager = managerWithSampleTasks();

        assertEquals(3, manager.getTask(null).size());
    }

    private void getTaskHighestPriorityError() {
        TaskManager manager = managerWithSampleTasks();

        assertThrows(RuntimeException.class, () -> manager.getTask(TaskFilter.byHighestPriority()));
    }

    private void popTaskById() {
        TaskManager manager = managerWithSampleTask();

        List<Task> result = manager.popTask(TaskFilter.byTaskId("task1"));

        assertEquals(1, result.size());
        assertEquals("task1", result.get(0).getTaskId());
        assertTrue(manager.getTask(null).isEmpty());
    }

    private void popTaskHighestPriority() {
        TaskManager manager = managerWithSampleTasks();

        List<Task> result = manager.popTask(TaskFilter.byHighestPriority());

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getPriority());
        assertFalse(manager.getTask(null).stream().anyMatch(task -> "task2".equals(task.getTaskId())));
    }

    private void popTaskEmpty() {
        TaskManager manager = manager();

        assertEquals(List.of(), manager.popTask(TaskFilter.byHighestPriority()));
    }

    private void updateTask() {
        TaskManager manager = managerWithSampleTask();
        Task updated = sampleTask();
        updated.setDescription("Updated description");
        updated.setStatus(TaskStatus.WORKING);

        assertTrue(manager.updateTask(updated));
        Task actual = only(manager.getTask(TaskFilter.byTaskId("task1")));
        assertEquals("Updated description", actual.getDescription());
        assertEquals(TaskStatus.WORKING, actual.getStatus());
    }

    private void updateNonexistentTask() {
        TaskManager manager = manager();

        assertFalse(manager.updateTask(sampleTask()));
        assertTrue(manager.getTask(null).isEmpty());
    }

    private void removeTaskById() {
        TaskManager manager = managerWithSampleTask();

        manager.removeTask(TaskFilter.byTaskId("task1"));

        assertTrue(manager.getTask(null).isEmpty());
    }

    private void removeTaskWithChildren() {
        TaskManager manager = managerWithParentChildren(true);

        manager.removeTask(TaskFilter.builder().taskId("parent").withChildren(true).build());

        assertTrue(manager.getTask(null).isEmpty());
    }

    private void removeTaskPromotesChildrenToRoot() {
        TaskManager manager = managerWithParentChildren(true);

        manager.removeTask(TaskFilter.byTaskId("parent"));

        Task child = only(manager.getTask(TaskFilter.byTaskId("child")));
        assertNull(child.getParentTaskId());
        assertEquals(Set.of("child"), ids(manager.getTask(TaskFilter.byRoot())));
    }

    private void removeTaskBySessionId() {
        TaskManager manager = managerWithSampleTasks();

        manager.removeTask(TaskFilter.bySessionId("session1"));

        assertEquals(Set.of("task3"), ids(manager.getTask(null)));
    }

    private void removeTaskByStatus() {
        TaskManager manager = managerWithSampleTasks();

        manager.removeTask(TaskFilter.byStatus(TaskStatus.COMPLETED));

        assertEquals(Set.of("task1", "task2"), ids(manager.getTask(null)));
    }

    private void removeTaskNoFilterError() {
        assertThrows(RuntimeException.class, () -> TaskFilter.builder().build());
    }

    private void removeTaskHighestPriorityError() {
        TaskManager manager = managerWithSampleTasks();

        assertThrows(RuntimeException.class, () -> manager.removeTask(TaskFilter.byHighestPriority()));
    }

    private void popTaskNoneFilterError() {
        TaskManager manager = manager();

        assertThrows(RuntimeException.class, () -> manager.popTask(null));
    }

    private void removeTaskNoneFilterError() {
        TaskManager manager = manager();

        assertThrows(RuntimeException.class, () -> manager.removeTask(null));
    }

    private void getChildTask() {
        TaskManager manager = managerWithParentChildren(false);

        List<Task> result = manager.getChildTask("parent", false);

        assertEquals(Set.of("child1", "child2"), ids(result));
    }

    private void getChildTaskRecursive() {
        TaskManager manager = managerWithParentChildGrandchild();

        List<Task> result = manager.getChildTask("parent", true);

        assertEquals(Set.of("child", "grandchild"), ids(result));
    }

    private void updateTaskStatus() {
        TaskManager manager = managerWithSampleTask();

        manager.updateTaskStatus("task1", TaskStatus.WORKING);

        assertEquals(TaskStatus.WORKING, only(manager.getTask(TaskFilter.byTaskId("task1"))).getStatus());
    }

    private void updateTaskStatusWithChildren() {
        TaskManager manager = managerWithParentChildren(true);

        manager.updateTaskStatus(List.of("parent"), TaskStatus.WORKING, true, false, null);

        assertEquals(TaskStatus.WORKING, only(manager.getTask(TaskFilter.byTaskId("parent"))).getStatus());
        assertEquals(TaskStatus.WORKING, only(manager.getTask(TaskFilter.byTaskId("child"))).getStatus());
    }

    private void updateTaskStatusRecursive() {
        TaskManager manager = managerWithParentChildGrandchild();

        manager.updateTaskStatus(List.of("parent"), TaskStatus.WORKING, true, true, null);

        assertEquals(Set.of("parent", "child", "grandchild"),
                manager.getTask(TaskFilter.byStatus(TaskStatus.WORKING)).stream()
                        .map(Task::getTaskId)
                        .collect(Collectors.toSet()));
    }

    private void setPriority() {
        TaskManager manager = managerWithSampleTask();

        manager.setPriority("task1", 5, false, false);

        assertEquals(5, only(manager.getTask(TaskFilter.byTaskId("task1"))).getPriority());
    }

    private void setPriorityString() {
        TaskManager manager = managerWithSampleTask();

        manager.setPriority("task1", Integer.parseInt("3"), false, false);

        assertEquals(3, only(manager.getTask(TaskFilter.byTaskId("task1"))).getPriority());
    }

    private void setPriorityWithChildren() {
        TaskManager manager = managerWithParentChildren(true);

        manager.setPriority("parent", 5, true, false);

        assertEquals(5, only(manager.getTask(TaskFilter.byTaskId("parent"))).getPriority());
        assertEquals(5, only(manager.getTask(TaskFilter.byTaskId("child"))).getPriority());
    }

    private void setPriorityRecursive() {
        TaskManager manager = managerWithParentChildGrandchild();

        manager.setPriority("parent", 5, true, true);

        assertEquals(Set.of("parent", "child", "grandchild"),
                manager.getTask(TaskFilter.builder().priority(5).build()).stream()
                        .map(Task::getTaskId)
                        .collect(Collectors.toSet()));
    }

    private void getState() {
        TaskManager manager = managerWithSampleTasks();

        TaskManagerState state = manager.getState();

        assertInstanceOf(TaskManagerState.class, state);
        assertEquals(3, state.getTasks().size());
        assertFalse(state.getPriorityIndex().isEmpty());
        assertFalse(state.getRootTasks().isEmpty());
    }

    private void loadState() {
        TaskManager manager = managerWithSampleTasks();
        TaskManagerState state = manager.getState();
        TaskManager newManager = manager();

        newManager.loadState(state);

        assertEquals(Set.of("task1", "task2", "task3"), ids(newManager.getTask(null)));
    }

    private void statePersistence() {
        TaskManager manager = managerWithParentChildren(true);
        TaskManagerState state = manager.getState();
        TaskManager newManager = manager();

        newManager.loadState(state);

        assertNotNull(only(newManager.getTask(TaskFilter.byTaskId("parent"))));
        assertNotNull(only(newManager.getTask(TaskFilter.byTaskId("child"))));
    }

    private void parallelAddTasks() throws Exception {
        TaskManager manager = manager();
        List<Callable<Void>> calls = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            final int taskIndex = index;
            calls.add(() -> {
                manager.addTask(task("session" + taskIndex % 5, "task_" + taskIndex,
                        taskIndex % 10, TaskStatus.SUBMITTED, null));
                return null;
            });
        }

        runConcurrent(calls);

        assertEquals(50, manager.getTask(null).size());
    }

    private void parallelGetAndUpdate() throws Exception {
        TaskManager manager = managerWithSampleTasks();

        runConcurrent(List.of(
                () -> updateFirstMatching(manager, "task1", TaskStatus.WORKING),
                () -> updateFirstMatching(manager, "task2", TaskStatus.COMPLETED),
                () -> updateFirstMatching(manager, "task3", TaskStatus.FAILED)
        ));

        assertEquals(TaskStatus.WORKING, only(manager.getTask(TaskFilter.byTaskId("task1"))).getStatus());
        assertEquals(TaskStatus.COMPLETED, only(manager.getTask(TaskFilter.byTaskId("task2"))).getStatus());
        assertEquals(TaskStatus.FAILED, only(manager.getTask(TaskFilter.byTaskId("task3"))).getStatus());
    }

    private Void updateFirstMatching(TaskManager manager, String taskId, TaskStatus status) {
        Task task = only(manager.getTask(TaskFilter.byTaskId(taskId)));
        task.setStatus(status);
        manager.updateTask(task);
        return null;
    }

    private void parallelStatusUpdates() throws Exception {
        TaskManager manager = managerWithSampleTasks();
        List<Callable<Void>> calls = List.of(
                () -> updateStatusRepeatedly(manager, "task1"),
                () -> updateStatusRepeatedly(manager, "task2"),
                () -> updateStatusRepeatedly(manager, "task3")
        );

        runConcurrent(calls);

        assertTrue(manager.getTask(null).stream().allMatch(task -> task.getStatus() == TaskStatus.COMPLETED));
    }

    private Void updateStatusRepeatedly(TaskManager manager, String taskId) throws InterruptedException {
        for (int index = 0; index < 10; index++) {
            manager.updateTaskStatus(taskId, TaskStatus.WORKING);
            Thread.sleep(1);
            manager.updateTaskStatus(taskId, TaskStatus.COMPLETED);
            Thread.sleep(1);
        }
        return null;
    }

    private void parallelAddAndRemove() throws Exception {
        TaskManager manager = manager();
        List<Callable<Void>> addCalls = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            final int taskIndex = index;
            addCalls.add(() -> {
                manager.addTask(task("session1", "task_" + taskIndex, taskIndex % 5,
                        TaskStatus.SUBMITTED, null));
                return null;
            });
        }
        runConcurrent(addCalls);

        List<Callable<Void>> removeCalls = new ArrayList<>();
        for (int index = 0; index < 30; index += 2) {
            final int taskIndex = index;
            removeCalls.add(() -> {
                manager.removeTask(TaskFilter.byTaskId("task_" + taskIndex));
                return null;
            });
        }
        runConcurrent(removeCalls);

        assertEquals(15, manager.getTask(null).size());
    }

    private void parallelPriorityUpdates() throws Exception {
        TaskManager manager = managerWithSampleTasks();

        runConcurrent(List.of(
                () -> setPriority(manager, "task1", 10),
                () -> setPriority(manager, "task2", 20),
                () -> setPriority(manager, "task3", 30)
        ));

        Map<String, Integer> priorities = manager.getTask(null).stream()
                .collect(Collectors.toMap(Task::getTaskId, Task::getPriority));
        assertEquals(10, priorities.get("task1"));
        assertEquals(20, priorities.get("task2"));
        assertEquals(30, priorities.get("task3"));
    }

    private Void setPriority(TaskManager manager, String taskId, int priority) {
        manager.setPriority(taskId, priority, false, false);
        return null;
    }

    private void parallelPopOperations() throws Exception {
        TaskManager manager = manager();
        for (int index = 0; index < 20; index++) {
            manager.addTask(task("session1", "task_" + index, index % 5, TaskStatus.SUBMITTED, null));
        }
        List<Task> poppedTasks = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Callable<Void>> popCalls = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            popCalls.add(() -> {
                poppedTasks.addAll(manager.popTask(TaskFilter.byHighestPriority()));
                return null;
            });
        }

        runConcurrent(popCalls);

        assertFalse(poppedTasks.isEmpty());
        assertEquals(new HashSet<>(ids(poppedTasks)).size(), poppedTasks.size());
        assertTrue(poppedTasks.stream().mapToInt(Task::getPriority).max().orElse(-1) >= 0);
    }

    private void parallelGetOperations() throws Exception {
        TaskManager manager = managerWithSampleTasks();
        List<Callable<Void>> calls = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            calls.add(() -> {
                List<Task> result = manager.getTask(null);
                assertEquals(3, result.size());
                assertEquals(Set.of("task1", "task2", "task3"), ids(result));
                return null;
            });
        }

        runConcurrent(calls);
    }

    private void parallelMixedOperations() throws Exception {
        TaskManager manager = managerWithSampleTasks();
        List<Callable<Void>> calls = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            final int operationId = index;
            calls.add(() -> {
                List<Task> tasks = manager.getTask(TaskFilter.bySessionId("session1"));
                if (!tasks.isEmpty()) {
                    Task task = tasks.get(0);
                    task.setStatus(TaskStatus.WORKING);
                    manager.updateTask(task);
                }
                manager.addTask(task("session1", "new_task_" + operationId, 5, TaskStatus.SUBMITTED, null));
                return null;
            });
        }

        runConcurrent(calls);

        assertTrue(manager.getTask(null).size() >= 3);
    }

    private static void runConcurrent(List<Callable<Void>> calls) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, Math.min(calls.size(), 8)));
        try {
            List<Future<Void>> futures = executor.invokeAll(calls);
            for (Future<Void> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static TaskManager manager() {
        return new TaskManager(new ControllerConfig());
    }

    private static TaskManager managerWithSampleTask() {
        TaskManager manager = manager();
        manager.addTask(sampleTask());
        return manager;
    }

    private static TaskManager managerWithSampleTasks() {
        TaskManager manager = manager();
        manager.addTask(sampleTasks());
        return manager;
    }

    private static TaskManager managerWithParentChildren(boolean oneChild) {
        TaskManager manager = manager();
        manager.addTask(task("session1", "parent", 1, TaskStatus.SUBMITTED, null));
        manager.addTask(task("session1", "child", 1, TaskStatus.SUBMITTED, "parent"));
        if (!oneChild) {
            manager.addTask(task("session1", "child1", 1, TaskStatus.SUBMITTED, "parent"));
            manager.addTask(task("session1", "child2", 1, TaskStatus.SUBMITTED, "parent"));
            manager.removeTask(TaskFilter.byTaskId("child"));
        }
        return manager;
    }

    private static TaskManager managerWithParentChildGrandchild() {
        TaskManager manager = manager();
        manager.addTask(task("session1", "parent", 1, TaskStatus.SUBMITTED, null));
        manager.addTask(task("session1", "child", 1, TaskStatus.SUBMITTED, "parent"));
        manager.addTask(task("session1", "grandchild", 1, TaskStatus.SUBMITTED, "child"));
        return manager;
    }

    private static Task sampleTask() {
        Task task = task("session1", "task1", 1, TaskStatus.SUBMITTED, null);
        task.setDescription("Test task");
        return task;
    }

    private static List<Task> sampleTasks() {
        Task task1 = task("session1", "task1", 1, TaskStatus.SUBMITTED, null);
        task1.setDescription("Task 1");
        Task task2 = task("session1", "task2", 2, TaskStatus.WORKING, null);
        task2.setDescription("Task 2");
        Task task3 = task("session2", "task3", 1, TaskStatus.COMPLETED, null);
        task3.setDescription("Task 3");
        return List.of(task1, task2, task3);
    }

    private static Task task(String sessionId, String taskId, int priority, TaskStatus status, String parentTaskId) {
        Task task = new Task(sessionId, taskId, "test_task");
        task.setDescription(taskId);
        task.setPriority(priority);
        task.setStatus(status);
        task.setParentTaskId(parentTaskId);
        return task;
    }

    private static Task only(List<Task> tasks) {
        assertEquals(1, tasks.size());
        return tasks.get(0);
    }

    private static Set<String> ids(List<Task> tasks) {
        return tasks.stream().map(Task::getTaskId).collect(Collectors.toSet());
    }
}
