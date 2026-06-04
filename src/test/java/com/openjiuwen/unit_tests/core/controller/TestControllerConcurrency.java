/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.controller;

import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.modules.TaskFilter;
import com.openjiuwen.core.controller.modules.TaskManager;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskStatus;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for Controller concurrency and exception handling.
 * <p>
 * Mirrors Python's {@code test_controller_concurrency_and_exception.py} from
 * {@code tests/unit_tests/core/controller/test_controller_concurrency_and_exception.py}.
 */
class TestControllerConcurrency {

    private TaskManager taskManager;

    @BeforeEach
    void setUp() {
        ControllerConfig config = new ControllerConfig();
        config.setDefaultTaskPriority(1);
        taskManager = new TaskManager(config);
    }

    @AfterEach
    void tearDown() {
        taskManager.clearState();
    }

    private static Task task(String sessionId, String taskId, TaskStatus status) {
        Task task = new Task(sessionId, taskId, "normal");
        task.setDescription("task for " + sessionId);
        task.setPriority(1);
        task.setStatus(status);
        if (status == TaskStatus.FAILED) {
            task.setErrorMessage("Task " + taskId + " failed intentionally");
        }
        task.setMetadata(Map.of("user_id", sessionId));
        return task;
    }

    @Test
    @DisplayName("Concurrent sessions stay isolated")
    void testConcurrentSessionsIsolation() {
        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            List<CompletableFuture<Void>> futures = IntStream.rangeClosed(1, 3)
                    .mapToObj(index -> CompletableFuture.runAsync(() -> {
                        String sessionId = "session_" + index;
                        Task task = task(sessionId, "task_" + sessionId, TaskStatus.COMPLETED);
                        taskManager.addTask(task);
                    }, executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }

        for (int index = 1; index <= 3; index++) {
            String sessionId = "session_" + index;
            List<Task> sessionTasks = taskManager.getTask(TaskFilter.bySessionId(sessionId));
            assertEquals(1, sessionTasks.size());
            assertEquals(sessionId, sessionTasks.get(0).getSessionId());
            assertEquals(TaskStatus.COMPLETED, sessionTasks.get(0).getStatus());
        }

        assertEquals(3, taskManager.getTask(null).size());
    }

    @Test
    @DisplayName("Failure in one session does not affect another")
    void testSessionTaskFailureIsolation() {
        taskManager.addTask(List.of(
                task("normal_session", "normal_task", TaskStatus.COMPLETED),
                task("failing_session", "failing_task", TaskStatus.FAILED),
                task("failing_session", "normal_task_2", TaskStatus.COMPLETED)
        ));

        List<Task> normalFailed = taskManager.getTask(TaskFilter.builder()
                .sessionId("normal_session")
                .status(TaskStatus.FAILED)
                .build());
        List<Task> failingFailed = taskManager.getTask(TaskFilter.builder()
                .sessionId("failing_session")
                .status(TaskStatus.FAILED)
                .build());
        List<Task> failingCompleted = taskManager.getTask(TaskFilter.builder()
                .sessionId("failing_session")
                .status(TaskStatus.COMPLETED)
                .build());

        assertTrue(normalFailed.isEmpty());
        assertEquals(1, failingFailed.size());
        assertTrue(failingFailed.get(0).getErrorMessage().contains("failed intentionally"));
        assertEquals(1, failingCompleted.size());
        assertEquals("normal_task_2", failingCompleted.get(0).getTaskId());
    }

    @Test
    @DisplayName("Concurrent status updates remain session scoped")
    void testConcurrentStatusUpdatesRemainSessionScoped() {
        taskManager.addTask(List.of(
                task("session_alpha", "task_alpha", TaskStatus.SUBMITTED),
                task("session_beta", "task_beta", TaskStatus.SUBMITTED),
                task("session_gamma", "task_gamma", TaskStatus.SUBMITTED)
        ));

        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            CompletableFuture<Void> alpha = CompletableFuture.runAsync(() -> {
                Task updated = task("session_alpha", "task_alpha", TaskStatus.COMPLETED);
                taskManager.updateTask(updated);
            }, executor);
            CompletableFuture<Void> beta = CompletableFuture.runAsync(() -> {
                Task updated = task("session_beta", "task_beta", TaskStatus.FAILED);
                taskManager.updateTask(updated);
            }, executor);
            CompletableFuture<Void> gamma = CompletableFuture.runAsync(() -> {
                Task updated = task("session_gamma", "task_gamma", TaskStatus.WORKING);
                taskManager.updateTask(updated);
            }, executor);
            CompletableFuture.allOf(alpha, beta, gamma).join();
        }

        assertEquals(TaskStatus.COMPLETED,
                taskManager.getTask(TaskFilter.bySessionId("session_alpha")).getFirst().getStatus());
        Task betaTask = taskManager.getTask(TaskFilter.bySessionId("session_beta")).getFirst();
        assertEquals(TaskStatus.FAILED, betaTask.getStatus());
        assertTrue(betaTask.getErrorMessage().contains("failed intentionally"));
        assertEquals(TaskStatus.WORKING,
                taskManager.getTask(TaskFilter.bySessionId("session_gamma")).getFirst().getStatus());
    }
}
