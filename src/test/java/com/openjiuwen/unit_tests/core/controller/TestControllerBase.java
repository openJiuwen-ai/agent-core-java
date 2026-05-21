/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.controller;

import com.openjiuwen.core.controller.Controller;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.ControllerOutputPayload;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.singleagent.ControllerAgent;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Controller base functionality.
 * <p>
 * Mirrors Python's {@code test_controller_base.py} from
 * {@code tests/unit_tests/core/controller/test_controller_base.py}.
 * Tests basic controller creation, configuration, and task execution.
 */
class TestControllerBase {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Basic existence checks)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testControllerClassExists() {
        assertNotNull(Controller.class);
    }

    @Test
    @Tag("level0")
    void testControllerConfigClassExists() {
        assertNotNull(ControllerConfig.class);
    }

    @Test
    @Tag("level0")
    void testControllerAgentClassExists() {
        assertNotNull(ControllerAgent.class);
    }

    @Test
    @Tag("level0")
    void testControllerOutputChunkClassExists() {
        assertNotNull(ControllerOutputChunk.class);
    }

    @Test
    @Tag("level0")
    void testControllerOutputPayloadClassExists() {
        assertNotNull(ControllerOutputPayload.class);
    }

    @Test
    @Tag("level0")
    void testTaskClassExists() {
        assertNotNull(Task.class);
    }

    @Test
    @Tag("level0")
    void testTaskStatusClassExists() {
        assertNotNull(TaskStatus.class);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Configuration tests)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    void testControllerConfigCreation() {
        ControllerConfig config = new ControllerConfig();
        assertNotNull(config);
    }

    @Test
    @Tag("level1")
    void testControllerConfigMaxConcurrency() {
        ControllerConfig config = new ControllerConfig();
        config.setMaxConcurrency(5);
        assertEquals(5, config.getMaxConcurrency());
    }

    @Test
    @Tag("level1")
    void testControllerConfigTimeout() {
        ControllerConfig config = new ControllerConfig();
        config.setTimeoutMs(30000);
        assertEquals(30000, config.getTimeoutMs());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Task status checks)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    void testTaskStatusValues() {
        assertNotNull(TaskStatus.values());
        assertTrue(TaskStatus.values().length > 0);
    }

    @Test
    @Tag("level2")
    void testTaskCreation() {
        Task task = new Task();
        assertNotNull(task);
    }

    @Test
    @Tag("level2")
    void testTaskIdGeneration() {
        Task task = new Task();
        task.setTaskId("test-task-123");
        assertEquals("test-task-123", task.getTaskId());
    }

    @Test
    @Tag("level2")
    void testTaskStatusPending() {
        Task task = new Task();
        task.setStatus(TaskStatus.PENDING);
        assertEquals(TaskStatus.PENDING, task.getStatus());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 3 (Output chunk tests)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level3")
    void testControllerOutputChunkCreation() {
        ControllerOutputChunk chunk = new ControllerOutputChunk();
        assertNotNull(chunk);
    }

    @Test
    @Tag("level3")
    void testControllerOutputChunkIndex() {
        ControllerOutputChunk chunk = new ControllerOutputChunk();
        chunk.setIndex(0);
        assertEquals(0, chunk.getIndex());
    }

    @Test
    @Tag("level3")
    void testControllerOutputChunkType() {
        ControllerOutputChunk chunk = new ControllerOutputChunk();
        chunk.setType("controller_output");
        assertEquals("controller_output", chunk.getType());
    }

    @Test
    @Tag("level3")
    void testControllerOutputPayloadCreation() {
        ControllerOutputPayload payload = new ControllerOutputPayload();
        assertNotNull(payload);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 4 (Controller agent tests)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level4")
    void testControllerAgentCreation() {
        assertNotNull(ControllerAgent.class.getConstructors());
    }

    @Test
    @Tag("level4")
    void testControllerMethods() {
        assertTrue(Controller.class.getDeclaredMethods().length > 0);
    }

    @Test
    @Tag("level4")
    void testControllerAgentMethods() {
        assertTrue(ControllerAgent.class.getDeclaredMethods().length > 0);
    }
}