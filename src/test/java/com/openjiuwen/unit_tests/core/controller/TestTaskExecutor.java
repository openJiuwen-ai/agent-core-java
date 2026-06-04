/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.controller;

import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.modules.EventQueue;
import com.openjiuwen.core.controller.modules.TaskExecutor;
import com.openjiuwen.core.controller.modules.TaskExecutorDependencies;
import com.openjiuwen.core.controller.modules.TaskExecutorRegistry;
import com.openjiuwen.core.controller.modules.TaskManager;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.ControllerOutputPayload;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.session.AgentSessionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task Executor lifecycle tests.
 *
 * <p>Mirrors Python's tests/unit_tests/core/controller/test_task_executor.py.</p>
 */
@DisplayName("TestTaskExecutor")
class TestTaskExecutor {

    private TaskExecutorRegistry registry;
    private TaskExecutorDependencies dependencies;

    @BeforeEach
    void setUp() {
        ControllerConfig config = new ControllerConfig();
        TaskManager taskManager = new TaskManager(config);
        EventQueue eventQueue = new EventQueue(config);
        registry = new TaskExecutorRegistry();
        dependencies = new TaskExecutorDependencies(config, null, null, taskManager, eventQueue);
        TrackableTaskExecutorStats.resetTracking();
    }

    static class TrackableTaskExecutorStats {
        static final AtomicInteger instancesCreated = new AtomicInteger();
        static final AtomicInteger instancesCleaned = new AtomicInteger();
        static final List<Integer> activeInstances = new ArrayList<>();

        static void resetTracking() {
            instancesCreated.set(0);
            instancesCleaned.set(0);
            activeInstances.clear();
        }
    }

    static class TrackableTaskExecutor extends TaskExecutor {
        private final int instanceId;

        TrackableTaskExecutor(TaskExecutorDependencies dependencies) {
            super(dependencies);
            instanceId = TrackableTaskExecutorStats.instancesCreated.incrementAndGet();
            TrackableTaskExecutorStats.activeInstances.add(instanceId);
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session) {
            List<ControllerOutputChunk> chunks = List.of(
                    new ControllerOutputChunk(
                            0,
                            new ControllerOutputPayload(
                                    ControllerOutputPayload.TASK_PROCESSING,
                                    List.of(new DataFrame.TextDataFrame("Task " + taskId + " executed by instance " + instanceId))),
                            false),
                    new ControllerOutputChunk(
                            1,
                            new ControllerOutputPayload(
                                    EventType.TASK_COMPLETION,
                                    List.of(new DataFrame.TextDataFrame("Task " + taskId + " completed"))),
                            true));
            return new Iterator<>() {
                private int index;

                @Override
                public boolean hasNext() {
                    return index < chunks.size();
                }

                @Override
                public ControllerOutputChunk next() {
                    ControllerOutputChunk chunk = chunks.get(index++);
                    if (chunk.isLastChunk()) {
                        TrackableTaskExecutorStats.instancesCleaned.incrementAndGet();
                        TrackableTaskExecutorStats.activeInstances.remove(Integer.valueOf(instanceId));
                    }
                    return chunk;
                }
            };
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

    private TrackableTaskExecutor newExecutor() {
        return (TrackableTaskExecutor) registry.getTaskExecutor("trackable", dependencies);
    }

    @Test
    @DisplayName("Test register and retrieve executor")
    void testRegisterAndRetrieveExecutor() {
        registry.addTaskExecutor("trackable", TrackableTaskExecutor::new);

        TaskExecutor executor = registry.getTaskExecutor("trackable", dependencies);

        assertInstanceOf(TrackableTaskExecutor.class, executor);
    }

    @Test
    @DisplayName("Test remove executor")
    void testRemoveExecutor() {
        registry.addTaskExecutor("trackable", TrackableTaskExecutor::new);
        registry.removeTaskExecutor("trackable");

        assertThrows(Exception.class, () -> registry.getTaskExecutor("trackable", dependencies));
    }

    @Test
    @DisplayName("Test handle unregistered task types")
    void testHandleUnregisteredTaskTypes() {
        Exception exception = assertThrows(Exception.class,
                () -> registry.getTaskExecutor("unregistered", dependencies));
        assertTrue(exception.getMessage().contains("task executor not found"));
    }

    @Test
    @DisplayName("Test executor creation - one instance per task")
    void testExecutorCreationOneInstancePerTask() {
        registry.addTaskExecutor("trackable", TrackableTaskExecutor::new);

        newExecutor();
        newExecutor();
        newExecutor();

        assertEquals(3, TrackableTaskExecutorStats.instancesCreated.get());
        assertEquals(3, TrackableTaskExecutorStats.activeInstances.size());
    }

    @Test
    @DisplayName("Test independent executor instances for different tasks")
    void testIndependentExecutorInstances() {
        registry.addTaskExecutor("trackable", TrackableTaskExecutor::new);

        TaskExecutor executor1 = registry.getTaskExecutor("trackable", dependencies);
        TaskExecutor executor2 = registry.getTaskExecutor("trackable", dependencies);

        assertNotSame(executor1, executor2);
    }

    @Test
    @DisplayName("Test executor cleanup after task completion")
    void testExecutorCleanupAfterTaskCompletion() {
        registry.addTaskExecutor("trackable", TrackableTaskExecutor::new);
        TrackableTaskExecutor executor = newExecutor();

        List<ControllerOutputChunk> chunks = new ArrayList<>();
        executor.executeAbility("task-1", new AgentSessionApi("session-1")).forEachRemaining(chunks::add);

        assertEquals(2, chunks.size());
        assertEquals(1, TrackableTaskExecutorStats.instancesCleaned.get());
        assertTrue(TrackableTaskExecutorStats.activeInstances.isEmpty());
    }

    @Test
    @DisplayName("Test executor cleanup tracking")
    void testExecutorCleanupTracking() {
        assertEquals(0, TrackableTaskExecutorStats.instancesCreated.get());
        assertEquals(0, TrackableTaskExecutorStats.instancesCleaned.get());
        assertTrue(TrackableTaskExecutorStats.activeInstances.isEmpty());
    }

    @Test
    @DisplayName("Test can_pause returns true")
    void testCanPauseReturnsTrue() {
        registry.addTaskExecutor("trackable", TrackableTaskExecutor::new);
        TrackableTaskExecutor executor = newExecutor();

        assertTrue(executor.canPause("task-1", new AgentSessionApi("session-1")).canPause());
    }

    @Test
    @DisplayName("Test pause returns true")
    void testPauseReturnsTrue() {
        registry.addTaskExecutor("trackable", TrackableTaskExecutor::new);
        TrackableTaskExecutor executor = newExecutor();

        assertTrue(executor.pause("task-1", new AgentSessionApi("session-1")));
    }

    @Test
    @DisplayName("Test can_cancel returns true")
    void testCanCancelReturnsTrue() {
        registry.addTaskExecutor("trackable", TrackableTaskExecutor::new);
        TrackableTaskExecutor executor = newExecutor();

        assertTrue(executor.canCancel("task-1", new AgentSessionApi("session-1")).canCancel());
    }

    @Test
    @DisplayName("Test cancel returns true")
    void testCancelReturnsTrue() {
        registry.addTaskExecutor("trackable", TrackableTaskExecutor::new);
        TrackableTaskExecutor executor = newExecutor();

        assertTrue(executor.cancel("task-1", new AgentSessionApi("session-1")));
    }

    @Test
    @DisplayName("Test execute_ability produces output chunks")
    void testExecuteAbilityProducesOutputChunks() {
        registry.addTaskExecutor("trackable", TrackableTaskExecutor::new);
        TrackableTaskExecutor executor = newExecutor();

        List<ControllerOutputChunk> chunks = new ArrayList<>();
        executor.executeAbility("task-1", new AgentSessionApi("session-1")).forEachRemaining(chunks::add);

        assertEquals(2, chunks.size());
        assertEquals(ControllerOutputPayload.TASK_PROCESSING, chunks.get(0).getControllerPayload().getType());
    }

    @Test
    @DisplayName("Test execute_ability produces completion chunk")
    void testExecuteAbilityProducesCompletionChunk() {
        registry.addTaskExecutor("trackable", TrackableTaskExecutor::new);
        TrackableTaskExecutor executor = newExecutor();

        List<ControllerOutputChunk> chunks = new ArrayList<>();
        executor.executeAbility("task-1", new AgentSessionApi("session-1")).forEachRemaining(chunks::add);

        ControllerOutputChunk lastChunk = chunks.get(chunks.size() - 1);
        assertTrue(lastChunk.isLastChunk());
        assertEquals(EventType.TASK_COMPLETION.getValue(), lastChunk.getControllerPayload().getType());
    }
}
