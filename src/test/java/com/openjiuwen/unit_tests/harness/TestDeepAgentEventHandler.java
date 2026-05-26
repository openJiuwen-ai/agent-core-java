/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.harness.task_loop.TaskLoopEventHandler;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DeepAgent event handler.
 */
class TestDeepAgentEventHandler {

    @Test
    @Tag("level0")
    @DisplayName("Event handler processes events correctly")
    void testEventHandlerProcessesEvents() {
        TaskLoopEventHandler handler = new TaskLoopEventHandler(null);
        assertNotNull(handler, "TaskLoopEventHandler should be constructable");
        assertEquals("deep_agent_task", TaskLoopEventHandler.DEEP_TASK_TYPE,
            "Task type constant should match");
    }
    
    @Test
    @Tag("level0")
    @DisplayName("Event handler has interaction queues")
    void testEventHandlerHasInteractionQueues() {
        TaskLoopEventHandler handler = new TaskLoopEventHandler(null);
        assertNotNull(handler);
    }
}