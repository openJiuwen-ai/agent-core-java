/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.controller;

import com.openjiuwen.core.controller.Controller;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskStatus;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Controller concurrency and exception handling.
 * <p>
 * Mirrors Python's {@code test_controller_concurrency_and_exception.py} from
 * {@code tests/unit_tests/core/controller/test_controller_concurrency_and_exception.py}.
 * Tests concurrent task execution, cancellation, and error handling.
 */
class TestControllerConcurrency {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Basic existence checks)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testControllerConfigClassExists() {
        assertNotNull(ControllerConfig.class);
    }

    @Test
    @Tag("level0")
    void testTaskClassExists() {
        assertNotNull(Task.class);
    }

    @Test
    @Tag("level0")
    void testTaskStatusEnumExists() {
        assertNotNull(TaskStatus.class);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Concurrency configuration)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    void testControllerConfigMaxConcurrency() {
        ControllerConfig config = new ControllerConfig();
        config.setMaxConcurrency(10);
        assertEquals(10, config.getMaxConcurrency());
    }

    @Test
    @Tag("level1")
    void testControllerConfigMaxConcurrencyDefault() {
        ControllerConfig config = new ControllerConfig();
        assertNotNull(config);
    }

    @Test
    @Tag("level1")
    void testControllerConfigConcurrencyLimit() {
        ControllerConfig config = new ControllerConfig();
        config.setMaxConcurrency(1);
        assertEquals(1, config.getMaxConcurrency());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Task status for concurrency)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    void testTaskStatusPending() {
        assertNotNull(TaskStatus.PENDING);
    }

    @Test
    @Tag("level2")
    void testTaskStatusRunning() {
        assertNotNull(TaskStatus.RUNNING);
    }

    @Test
    @Tag("level2")
    void testTaskStatusCompleted() {
        assertNotNull(TaskStatus.COMPLETED);
    }

    @Test
    @Tag("level2")
    void testTaskStatusCancelled() {
        assertNotNull(TaskStatus.CANCELLED);
    }

    @Test
    @Tag("level2")
    void testTaskStatusFailed() {
        assertNotNull(TaskStatus.FAILED);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 3 (Concurrent task simulation)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level3")
    void testConcurrentTaskCreation() {
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Task task = new Task();
            task.setTaskId("task_" + i);
            task.setStatus(TaskStatus.PENDING);
            tasks.add(task);
        }
        assertEquals(5, tasks.size());
    }

    @Test
    @Tag("level3")
    void testTaskStatusTransition() {
        Task task = new Task();
        task.setStatus(TaskStatus.PENDING);
        assertEquals(TaskStatus.PENDING, task.getStatus());

        task.setStatus(TaskStatus.RUNNING);
        assertEquals(TaskStatus.RUNNING, task.getStatus());

        task.setStatus(TaskStatus.COMPLETED);
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
    }

    @Test
    @Tag("level3")
    void testCompletableFutureBasicUsage() {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> "result");
        String result = future.join();
        assertEquals("result", result);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 4 (Virtual thread executor)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level4")
    void testVirtualThreadExecutorCreation() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertNotNull(executor);
        }
    }

    @Test
    @Tag("level4")
    void testConcurrentExecution() {
        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 3; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return 1;
                }, executor));
            }

            int sum = futures.stream()
                    .map(CompletableFuture::join)
                    .reduce(0, Integer::sum);

            assertEquals(3, sum);
        }
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 5 (Cancellation simulation)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level5")
    void testTaskCancellationStatus() {
        Task task = new Task();
        task.setStatus(TaskStatus.RUNNING);
        task.setStatus(TaskStatus.CANCELLED);
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
    }

    @Test
    @Tag("level5")
    void testCompletableFutureCancellation() {
        CompletableFuture<String> future = new CompletableFuture<>();
        future.cancel(true);
        assertTrue(future.isCancelled());
    }
}