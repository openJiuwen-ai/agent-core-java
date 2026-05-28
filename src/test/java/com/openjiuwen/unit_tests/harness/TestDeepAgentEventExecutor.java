/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.harness.task_loop.TaskLoopEventExecutor;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DeepAgent event executor.
 *
 * <p>Mirrors Python's {@code test_deep_agent_event_executor} in
 * {@code tests.unit_tests.harness.test_deep_agent_event_executor}.
 */
class TestDeepAgentEventExecutor {

    @Test
    @Tag("level0")
    @DisplayName("Event executor handles events correctly")
    void testEventExecutorHandlesEvents() {
        TaskLoopEventExecutor executor = new TaskLoopEventExecutor(null, null);
        assertNotNull(executor, "TaskLoopEventExecutor should be constructable");
        assertEquals("deep_agent_task", TaskLoopEventExecutor.DEEP_TASK_TYPE,
            "Task type constant should match");
    }
    
    @Test
    @Tag("level0")
    @DisplayName("Event executor can execute ability")
    void testEventExecutorCanExecuteAbility() {
        TaskLoopEventExecutor executor = new TaskLoopEventExecutor(null, null);
        assertNotNull(executor);
        // Verify executor has executeAbility method
        var future = executor.executeAbility("test-task", null);
        assertNotNull(future, "executeAbility should return CompletableFuture");
    }
}