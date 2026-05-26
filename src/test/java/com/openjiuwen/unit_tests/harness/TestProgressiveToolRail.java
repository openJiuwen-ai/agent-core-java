/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.harness.rails.ProgressiveToolRail;
import org.junit.jupiter.api.*;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProgressiveToolRail.
 */
class TestProgressiveToolRail {

    @Test
    @Tag("level0")
    @DisplayName("ProgressiveToolRail manages tool progression")
    void testProgressiveToolRailManagesTools() {
        ProgressiveToolRail rail = new ProgressiveToolRail(
            Set.of("read_file", "write_file"),
            Set.of("bash"),
            10
        );
        assertNotNull(rail, "ProgressiveToolRail should be constructable");
        assertEquals(90, ProgressiveToolRail.PRIORITY, "Priority should be 90");
        assertTrue(rail instanceof com.openjiuwen.harness.rails.DeepAgentRail,
            "ProgressiveToolRail should extend DeepAgentRail");
    }
    
    @Test
    @Tag("level0")
    @DisplayName("ProgressiveToolRail can load tools")
    void testProgressiveToolRailLoadsTools() {
        ProgressiveToolRail rail = new ProgressiveToolRail(null, null, 5);
        rail.loadTool("grep");
        assertTrue(rail.getLoadedToolNames().contains("grep"),
            "Loaded tools should contain 'grep'");
    }
}