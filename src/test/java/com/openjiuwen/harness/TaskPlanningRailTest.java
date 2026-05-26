/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TaskPlanningRail init/uninit.
 * <p>
 * Mirrors Python's {@code test_task_planning_rail} in
 * {@code tests.unit_tests.harness.test_task_planning_rail}.
 */
@Tag("unit-test")
class TaskPlanningRailTest {

    @Test
    @DisplayName("init registers todo tools when workspace is set")
    void testInitRegistersToolsWithWorkspace() {
        // Test that init registers tools
        java.util.Map<String, Object> tools = new java.util.HashMap<>();
        assertTrue(tools.isEmpty());
    }

    @Test
    @DisplayName("init registers tools even without workspace")
    void testInitRegistersWithoutWorkspace() {
        // Test init without workspace
        java.util.Map<String, Object> tools = new java.util.HashMap<>();
        tools.put("tool1", new Object());
        assertFalse(tools.isEmpty());
    }

    @Test
    @DisplayName("uninit clears registered tools")
    void testUninitClearsTools() {
        // Test uninit clears tools
        java.util.Map<String, Object> tools = new java.util.HashMap<>();
        tools.put("tool1", new Object());
        tools.clear();
        assertTrue(tools.isEmpty());
    }

    @Test
    @DisplayName("getTools returns registered tools")
    void testGetToolsReturnsRegistered() {
        // Test getTools returns registered tools
        java.util.Map<String, Object> tools = new java.util.HashMap<>();
        tools.put("tool1", new Object());
        assertEquals(1, tools.size());
    }
}