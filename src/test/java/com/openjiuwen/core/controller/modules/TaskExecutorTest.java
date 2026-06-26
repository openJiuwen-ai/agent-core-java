/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.single_agent.AbilityManager;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TaskExecutor} in
 * {@code openjiuwen/core/controller/modules/task_executor.py}.
 */
class TaskExecutorTest {

    @Test
    void constructorStoresConcreteDependencies() {
        ControllerConfig config = new ControllerConfig();
        AbilityManager abilityManager = new AbilityManager();
        ContextEngine contextEngine = new ContextEngine();
        TaskManager taskManager = new TaskManager(config);
        EventQueue eventQueue = new EventQueue(config);

        RecordingExecutor executor = new RecordingExecutor(new TaskExecutorDependencies(
                config,
                abilityManager,
                contextEngine,
                taskManager,
                eventQueue
        ));

        assertThat(executor.config()).isSameAs(config);
        assertThat(executor.abilityManager()).isSameAs(abilityManager);
        assertThat(executor.contextEngine()).isSameAs(contextEngine);
        assertThat(executor.taskManager()).isSameAs(taskManager);
        assertThat(executor.eventQueue()).isSameAs(eventQueue);
    }

    @Test
    void pythonExecuteNameDelegatesToSchedulerEntryPoint() {
        RecordingExecutor executor = new RecordingExecutor(new TaskExecutorDependencies(
                new ControllerConfig(),
                new AbilityManager(),
                new ContextEngine(),
                new TaskManager(new ControllerConfig()),
                new EventQueue(new ControllerConfig())
        ));

        Iterator<ControllerOutputChunk> chunks = executor.execute("task-1", null);

        assertThat(chunks).isSameAs(executor.lastIterator());
        assertThat(executor.lastTaskId()).isEqualTo("task-1");
    }

    @Test
    void pauseAndCancelChecksMirrorTupleReturns() {
        RecordingExecutor executor = new RecordingExecutor(new TaskExecutorDependencies(
                new ControllerConfig(),
                null,
                null,
                new TaskManager(new ControllerConfig()),
                new EventQueue(new ControllerConfig())
        ));

        assertThat(executor.canPause("task-1", null))
                .isEqualTo(new TaskExecutor.PauseCheckResult(false, "pause-disabled"));
        assertThat(executor.pause("task-1", null)).isFalse();
        assertThat(executor.canCancel("task-1", null))
                .isEqualTo(new TaskExecutor.CancelCheckResult(true, ""));
        assertThat(executor.cancel("task-1", null)).isTrue();
    }

    /**
     * Test executor implementation for the abstract interface.
     *
     * <p>Mirrors Python's {@code TaskExecutor} subclass contract in
     * {@code openjiuwen/core/controller/modules/task_executor.py}.</p>
     */
    private static final class RecordingExecutor extends TaskExecutor {
        private Iterator<ControllerOutputChunk> lastIterator;
        private String lastTaskId;

        private RecordingExecutor(TaskExecutorDependencies dependencies) {
            super(dependencies);
        }

        private ControllerConfig config() {
            return config;
        }

        private AbilityManager abilityManager() {
            return abilityManager;
        }

        private ContextEngine contextEngine() {
            return contextEngine;
        }

        private TaskManager taskManager() {
            return taskManager;
        }

        private EventQueue eventQueue() {
            return eventQueue;
        }

        private Iterator<ControllerOutputChunk> lastIterator() {
            return lastIterator;
        }

        private String lastTaskId() {
            return lastTaskId;
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session) {
            lastTaskId = taskId;
            lastIterator = List.<ControllerOutputChunk>of().iterator();
            return lastIterator;
        }

        @Override
        public PauseCheckResult canPause(String taskId, AgentSessionApi session) {
            return new PauseCheckResult(false, "pause-disabled");
        }

        @Override
        public boolean pause(String taskId, AgentSessionApi session) {
            return false;
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
}
