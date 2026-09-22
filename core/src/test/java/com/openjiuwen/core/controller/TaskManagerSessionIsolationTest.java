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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Verifies that {@link TaskManager#clearStateForSession(String)} and
 * {@link TaskManager#loadStateForSession(String, TaskManagerState)} only
 * affect tasks belonging to the specified session, preserving tasks from
 * other sessions (Issue #164).
 *
 * @since 0.1.16
 */
class TaskManagerSessionIsolationTest {
    private TaskManager taskManager;

    @BeforeEach
    void setUp() {
        ControllerConfig config = new ControllerConfig();
        config.setDefaultTaskPriority(1);
        taskManager = new TaskManager(config);

        // Add tasks from two sessions
        taskManager.addTask(List.of(
                createTask("session-A", "task-a1", "test_task", "Task A1", 1, TaskStatus.WORKING),
                createTask("session-A", "task-a2", "test_task", "Task A2", 1, TaskStatus.SUBMITTED),
                createTask("session-B", "task-b1", "test_task", "Task B1", 1, TaskStatus.WORKING)));
    }

    private Task createTask(String sessionId, String taskId, String taskType, String description, int priority,
            TaskStatus status) {
        Task task = new Task(sessionId, taskId, taskType);
        task.setDescription(description);
        task.setPriority(priority);
        task.setStatus(status);
        return task;
    }

    @Test
    @DisplayName("clearStateForSession should only remove tasks from the specified session")
    void clearStateForSessionShouldOnlyAffectSpecifiedSession() {
        // Verify initial state: 3 tasks total
        assertEquals(3, taskManager.getTask(null).size());

        // Clear only session-A's tasks
        taskManager.clearStateForSession("session-A");

        // session-A tasks should be gone
        List<Task> sessionATasks = taskManager.getTask(TaskFilter.bySessionId("session-A"));
        assertTrue(sessionATasks.isEmpty());

        // session-B tasks should remain intact
        List<Task> sessionBTasks = taskManager.getTask(TaskFilter.bySessionId("session-B"));
        assertEquals(1, sessionBTasks.size());
        assertEquals("task-b1", sessionBTasks.get(0).getTaskId());
    }

    @Test
    @DisplayName("clearStateForSession with null or blank sessionId should be a no-op")
    void clearStateForSessionWithNullSessionIdShouldBeNoOp() {
        taskManager.clearStateForSession(null);
        assertEquals(3, taskManager.getTask(null).size());

        taskManager.clearStateForSession("");
        assertEquals(3, taskManager.getTask(null).size());
    }

    @Test
    @DisplayName("loadStateForSession should only load tasks for the specified session, preserving others")
    void loadStateForSessionShouldPreserveOtherSessions() {
        // Build a state snapshot for session-B with one new task
        Map<String, Task> tasks = new HashMap<>();
        Task b2 = createTask("session-B", "task-b2", "test_task", "Task B2", 1, TaskStatus.SUBMITTED);
        tasks.put("task-b2", b2.copy());
        TaskManagerState state = new TaskManagerState(tasks, new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new HashSet<>(Set.of("task-b2")));

        // Load state for session-B only — should clear session-B's existing tasks
        // and load the new one, but session-A's tasks should remain intact
        taskManager.loadStateForSession("session-B", state);

        // session-A tasks should be preserved
        List<Task> sessionATasks = taskManager.getTask(TaskFilter.bySessionId("session-A"));
        assertEquals(2, sessionATasks.size());

        // session-B should have only the new task (old task-b1 cleared, new task-b2 loaded)
        List<Task> sessionBTasks = taskManager.getTask(TaskFilter.bySessionId("session-B"));
        assertEquals(1, sessionBTasks.size());
        assertEquals("task-b2", sessionBTasks.get(0).getTaskId());
    }

    @Test
    @DisplayName("loadStateForSession with null state should just clear the session's tasks")
    void loadStateForSessionWithNullStateShouldClearSession() {
        taskManager.loadStateForSession("session-A", null);

        List<Task> sessionATasks = taskManager.getTask(TaskFilter.bySessionId("session-A"));
        assertTrue(sessionATasks.isEmpty());

        List<Task> sessionBTasks = taskManager.getTask(TaskFilter.bySessionId("session-B"));
        assertEquals(1, sessionBTasks.size());
    }
}
