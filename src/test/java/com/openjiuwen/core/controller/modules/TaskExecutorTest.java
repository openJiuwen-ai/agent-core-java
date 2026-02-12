// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.contextengine.ContextEngine;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.AbilityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TaskExecutorDependencies, TaskExecutorRegistry, and TaskExecutor ABC.
 *
 * <p>Covers:
 * <ul>
 *   <li>TaskExecutorDependencies: field access</li>
 *   <li>TaskExecutorRegistry: add/remove/get, error on missing type, overwrite, coexistence</li>
 *   <li>TaskExecutor: init dependency unpacking, executeAbility interface</li>
 * </ul>
 *
 * <p>Python reference: {@code tests/unit_tests/core/controller/modules/test_task_executor.py}
 */
class TaskExecutorTest {

    // ==================== Stub Executor ====================

    /**
     * Concrete implementation of TaskExecutor for testing.
     */
    static class StubSchedulerExecutor extends TaskExecutor {

        StubSchedulerExecutor(TaskExecutorDependencies dependencies) {
            super(dependencies);
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, Session session) {
            return List.of(new ControllerOutputChunk(0)).iterator();
        }

        @Override
        public PauseResult canPause(String taskId, Session session) {
            return new PauseResult(true, "");
        }

        @Override
        public boolean pause(String taskId, Session session) {
            return true;
        }

        @Override
        public CancelResult canCancel(String taskId, Session session) {
            return new CancelResult(true, "");
        }

        @Override
        public boolean cancel(String taskId, Session session) {
            return true;
        }
    }

    /**
     * Alternative executor for overwrite testing.
     */
    static class AlternativeExecutor extends TaskExecutor {

        AlternativeExecutor(TaskExecutorDependencies dependencies) {
            super(dependencies);
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, Session session) {
            return List.of(new ControllerOutputChunk(0)).iterator();
        }

        @Override
        public PauseResult canPause(String taskId, Session session) {
            return new PauseResult(false, "");
        }

        @Override
        public boolean pause(String taskId, Session session) {
            return false;
        }

        @Override
        public CancelResult canCancel(String taskId, Session session) {
            return new CancelResult(false, "");
        }

        @Override
        public boolean cancel(String taskId, Session session) {
            return false;
        }
    }

    // ==================== Helpers ====================

    private TaskExecutorDependencies makeDeps() {
        return new TaskExecutorDependencies(
            new ControllerConfig(),
            mock(AbilityManager.class),
            mock(ContextEngine.class),
            mock(TaskManager.class),
            mock(EventQueue.class)
        );
    }

    // ==================== TaskExecutorDependencies Tests ====================

    @Nested
    @DisplayName("TaskExecutorDependencies Tests")
    class DependenciesTests {

        @Test
        @DisplayName("All dependency fields should be accessible after construction")
        void testAllFieldsAccessible() {
            ControllerConfig config = new ControllerConfig();
            AbilityManager abilityMgr = mock(AbilityManager.class);
            ContextEngine contextEng = mock(ContextEngine.class);
            TaskManager taskMgr = mock(TaskManager.class);
            EventQueue eventQ = mock(EventQueue.class);

            TaskExecutorDependencies deps = new TaskExecutorDependencies(
                config, abilityMgr, contextEng, taskMgr, eventQ
            );

            assertSame(config, deps.getConfig());
            assertSame(abilityMgr, deps.getAbilityManager());
            assertSame(contextEng, deps.getContextEngine());
            assertSame(taskMgr, deps.getTaskManager());
            assertSame(eventQ, deps.getEventQueue());
        }
    }

    // ==================== TaskExecutorRegistry Tests ====================

    @Nested
    @DisplayName("TaskExecutorRegistry Tests")
    class RegistryTests {

        @Test
        @DisplayName("Registered executor should be retrievable by task_type")
        void testAddAndGetExecutor() {
            TaskExecutorRegistry registry = new TaskExecutorRegistry();
            registry.addTaskExecutor("analysis", StubSchedulerExecutor::new);

            TaskExecutor executor = registry.getTaskExecutor("analysis", makeDeps());
            assertInstanceOf(StubSchedulerExecutor.class, executor);
        }

        @Test
        @DisplayName("Getting an unregistered task_type should raise BaseError")
        void testGetUnregisteredTypeRaises() {
            TaskExecutorRegistry registry = new TaskExecutorRegistry();
            BaseError error = assertThrows(BaseError.class,
                () -> registry.getTaskExecutor("unknown", makeDeps()));
            assertTrue(error.getMessage().contains("not found"));
        }

        @Test
        @DisplayName("Removed task_type should no longer be retrievable")
        void testRemoveExecutor() {
            TaskExecutorRegistry registry = new TaskExecutorRegistry();
            registry.addTaskExecutor("analysis", StubSchedulerExecutor::new);
            registry.removeTaskExecutor("analysis");

            assertThrows(BaseError.class,
                () -> registry.getTaskExecutor("analysis", makeDeps()));
        }

        @Test
        @DisplayName("Removing a non-existent task_type should not raise")
        void testRemoveNonexistentTypeNoError() {
            TaskExecutorRegistry registry = new TaskExecutorRegistry();
            assertDoesNotThrow(() -> registry.removeTaskExecutor("nonexistent"));
        }

        @Test
        @DisplayName("Re-registering the same task_type should overwrite")
        void testOverwriteExistingType() {
            TaskExecutorRegistry registry = new TaskExecutorRegistry();
            registry.addTaskExecutor("analysis", StubSchedulerExecutor::new);
            registry.addTaskExecutor("analysis", AlternativeExecutor::new);

            TaskExecutor executor = registry.getTaskExecutor("analysis", makeDeps());
            assertInstanceOf(AlternativeExecutor.class, executor);
        }

        @Test
        @DisplayName("Multiple task types should coexist in the registry")
        void testMultipleTypesCoexist() {
            TaskExecutorRegistry registry = new TaskExecutorRegistry();
            registry.addTaskExecutor("type_a", StubSchedulerExecutor::new);
            registry.addTaskExecutor("type_b", StubSchedulerExecutor::new);

            assertInstanceOf(StubSchedulerExecutor.class,
                registry.getTaskExecutor("type_a", makeDeps()));
            assertInstanceOf(StubSchedulerExecutor.class,
                registry.getTaskExecutor("type_b", makeDeps()));
        }
    }

    // ==================== Scheduler TaskExecutor ABC Tests ====================

    @Nested
    @DisplayName("TaskExecutor ABC Tests")
    class ExecutorAbcTests {

        @Test
        @DisplayName("TaskExecutor should unpack dependencies into instance attributes")
        void testInitUnpacksDependencies() {
            TaskExecutorDependencies deps = makeDeps();
            StubSchedulerExecutor executor = new StubSchedulerExecutor(deps);

            assertSame(deps.getConfig(), executor.config);
            assertSame(deps.getAbilityManager(), executor.abilityManager);
            assertSame(deps.getContextEngine(), executor.contextEngine);
            assertSame(deps.getTaskManager(), executor.taskManager);
            assertSame(deps.getEventQueue(), executor.eventQueue);
        }

        @Test
        @DisplayName("executeAbility should yield ControllerOutputChunk instances")
        void testExecuteAbilityYieldsChunks() {
            TaskExecutorDependencies deps = makeDeps();
            StubSchedulerExecutor executor = new StubSchedulerExecutor(deps);
            Session session = mock(Session.class);

            Iterator<ControllerOutputChunk> chunks = executor.executeAbility("t1", session);

            assertTrue(chunks.hasNext());
            ControllerOutputChunk chunk = chunks.next();
            assertNotNull(chunk);
            assertInstanceOf(ControllerOutputChunk.class, chunk);
            assertFalse(chunks.hasNext());
        }
    }
}

