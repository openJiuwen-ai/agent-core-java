/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.controller;

import com.openjiuwen.core.controller.modules.TaskExecutor;
import com.openjiuwen.core.controller.modules.TaskExecutorDependencies;
import com.openjiuwen.core.controller.modules.TaskExecutorRegistry;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.session.AgentSessionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for TaskExecutorRegistry.
 * Translated from Python tests/unit_tests/core/controller/test_task_executor.py
 * (TaskExecutorRegistration tests section)
 */
class TaskExecutorRegistryTest {

    private TaskExecutorRegistry registry;
    private TaskExecutorDependencies mockDependencies;

    @BeforeEach
    void setUp() {
        registry = new TaskExecutorRegistry();
        mockDependencies = mock(TaskExecutorDependencies.class);
    }

    // Simple test executor
    static class SimpleTaskExecutor extends TaskExecutor {
        public SimpleTaskExecutor(TaskExecutorDependencies dependencies) {
            super(dependencies);
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session) {
            return java.util.Collections.emptyIterator();
        }

        @Override
        public PauseCheckResult canPause(String taskId, AgentSessionApi session) {
            return new PauseCheckResult(true, "");
        }

        @Override
        public boolean pause(String taskId, AgentSessionApi session) {
            return true;
        }

        @Override
        public CancelCheckResult canCancel(String taskId, AgentSessionApi session) {
            return new CancelCheckResult(true, "");
        }

        @Override
        public boolean cancel(String taskId, AgentSessionApi session) {
            return true;
        }
    }

    // Another test executor to distinguish from SimpleTaskExecutor
    static class TrackableTaskExecutor extends TaskExecutor {
        public TrackableTaskExecutor(TaskExecutorDependencies dependencies) {
            super(dependencies);
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session) {
            return java.util.Collections.emptyIterator();
        }

        @Override
        public PauseCheckResult canPause(String taskId, AgentSessionApi session) {
            return new PauseCheckResult(true, "");
        }

        @Override
        public boolean pause(String taskId, AgentSessionApi session) {
            return true;
        }

        @Override
        public CancelCheckResult canCancel(String taskId, AgentSessionApi session) {
            return new CancelCheckResult(true, "");
        }

        @Override
        public boolean cancel(String taskId, AgentSessionApi session) {
            return true;
        }
    }

    private static final Function<TaskExecutorDependencies, TaskExecutor> SIMPLE_BUILDER =
            SimpleTaskExecutor::new;

    private static final Function<TaskExecutorDependencies, TaskExecutor> TRACKABLE_BUILDER =
            TrackableTaskExecutor::new;

    @Test
    @DisplayName("test adding task executor")
    void testAddTaskExecutor() {
        registry.addTaskExecutor("test_type", SIMPLE_BUILDER);
        TaskExecutor executor = registry.getTaskExecutor("test_type", mockDependencies);
        assertNotNull(executor);
        assertInstanceOf(SimpleTaskExecutor.class, executor);
    }

    @Test
    @DisplayName("test removing task executor")
    void testRemoveTaskExecutor() {
        registry.addTaskExecutor("test_type", SIMPLE_BUILDER);
        TaskExecutor executor = registry.getTaskExecutor("test_type", mockDependencies);
        assertNotNull(executor);

        registry.removeTaskExecutor("test_type");
        assertThrows(Exception.class,
                () -> registry.getTaskExecutor("test_type", mockDependencies));
    }

    @Test
    @DisplayName("test getting task executor returns correct builder")
    void testGetTaskExecutor() {
        registry.addTaskExecutor("test_type", SIMPLE_BUILDER);
        TaskExecutor executor = registry.getTaskExecutor("test_type", mockDependencies);
        assertNotNull(executor);
        assertInstanceOf(SimpleTaskExecutor.class, executor);
    }

    @Test
    @DisplayName("test getting unregistered task executor raises error")
    void testGetUnregisteredTaskExecutor() {
        Exception exception = assertThrows(Exception.class,
                () -> registry.getTaskExecutor("unregistered_type", mockDependencies));
        assertTrue(exception.getMessage().toLowerCase().contains("task executor not found"),
                "Should raise error for unregistered task type, got: " + exception.getMessage());
    }

    @Test
    @DisplayName("test registering multiple executors")
    void testMultipleExecutorRegistration() {
        registry.addTaskExecutor("type1", SIMPLE_BUILDER);
        registry.addTaskExecutor("type2", TRACKABLE_BUILDER);

        TaskExecutor executor1 = registry.getTaskExecutor("type1", mockDependencies);
        TaskExecutor executor2 = registry.getTaskExecutor("type2", mockDependencies);

        assertInstanceOf(SimpleTaskExecutor.class, executor1);
        assertInstanceOf(TrackableTaskExecutor.class, executor2);
    }

    @Test
    @DisplayName("test each call creates a new executor instance")
    void testExecutorInstancePerCall() {
        registry.addTaskExecutor("test_type", SIMPLE_BUILDER);

        TaskExecutor executor1 = registry.getTaskExecutor("test_type", mockDependencies);
        TaskExecutor executor2 = registry.getTaskExecutor("test_type", mockDependencies);
        TaskExecutor executor3 = registry.getTaskExecutor("test_type", mockDependencies);

        // Each call should create a new instance
        assertNotSame(executor1, executor2);
        assertNotSame(executor2, executor3);
        assertNotSame(executor1, executor3);
    }

    @Test
    @DisplayName("test overwriting executor registration")
    void testOverwriteExecutorRegistration() {
        registry.addTaskExecutor("test_type", SIMPLE_BUILDER);
        TaskExecutor executor1 = registry.getTaskExecutor("test_type", mockDependencies);
        assertInstanceOf(SimpleTaskExecutor.class, executor1);

        // Overwrite with different type
        registry.addTaskExecutor("test_type", TRACKABLE_BUILDER);
        TaskExecutor executor2 = registry.getTaskExecutor("test_type", mockDependencies);
        assertInstanceOf(TrackableTaskExecutor.class, executor2);
    }

    @Test
    @DisplayName("test removing non-existent executor does not throw")
    void testRemoveNonExistentExecutor() {
        // Should not throw
        assertDoesNotThrow(() -> registry.removeTaskExecutor("non_existent"));
    }
}
