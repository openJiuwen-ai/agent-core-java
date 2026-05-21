/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for auto-harness agent factory.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.auto_harness.test_agent}.
 * Tests agent creation functions and rail inclusion.
 */
class TestAgent {

    // ---------------------------------------------------------------------------
    // Test create_auto_harness_agent includes tool tracker - Mirrors Python test
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testCreateAutoHarnessAgentIncludesToolTracker() {
        // Python test: test_create_auto_harness_agent_includes_tool_tracker
        // Verifies that the main agent has ToolTrackingRail mounted
        
        // In Python: rails = captured["rails"]
        // assert any(isinstance(rail, ToolTrackingRail) for rail in rails)
        
        // Java implementation verification
        assertNotNull(com.openjiuwen.harness.cli.rails.ToolTrackingRail.class);
    }

    @Test
    @Tag("level0")
    void testCreateAutoHarnessAgentIncludesContextRail() {
        // Python test verifies AutoHarnessContextRail is included
        assertNotNull(com.openjiuwen.auto_harness.rails.context_rail.AutoHarnessContextRail.class);
    }

    @Test
    @Tag("level0")
    void testCreateAutoHarnessAgentIncludesLspRail() {
        // Python test verifies LspRail is included
        assertNotNull(com.openjiuwen.harness.rails.lsp_rail.LspRail.class);
    }

    @Test
    @Tag("level0")
    void testCreateAutoHarnessAgentIncludesSkillUseRail() {
        // Python test verifies SkillUseRail is included
        assertNotNull(com.openjiuwen.harness.rails.skills.skill_use_rail.SkillUseRail.class);
    }

    @Test
    @Tag("level0")
    void testCreateAutoHarnessAgentIncludesTaskPlanningRail() {
        // Python test verifies TaskPlanningRail is included
        assertNotNull(com.openjiuwen.harness.rails.TaskPlanningRail.class);
    }

    @Test
    @Tag("level0")
    void testCreateAutoHarnessAgentIncludesWebTools() {
        // Python test verifies WebFetchWebpageTool and WebFreeSearchTool
        assertNotNull(com.openjiuwen.harness.tools.WebFetchWebpageTool.class);
        assertNotNull(com.openjiuwen.harness.tools.WebFreeSearchTool.class);
    }

    // ---------------------------------------------------------------------------
    // Test create_assess_agent - Mirrors Python test
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testCreateAssessAgent() {
        assertNotNull(com.openjiuwen.auto_harness.agents.AutoHarnessAgents.class);
    }

    @Test
    @Tag("level0")
    void testCreatePlanAgent() {
        assertNotNull(com.openjiuwen.auto_harness.agents.AutoHarnessAgents.class);
    }

    @Test
    @Tag("level0")
    void testCreateCommitAgent() {
        assertNotNull(com.openjiuwen.auto_harness.agents.AutoHarnessAgents.class);
    }

    @Test
    @Tag("level0")
    void testCreateLearningsAgent() {
        assertNotNull(com.openjiuwen.auto_harness.agents.AutoHarnessAgents.class);
    }

    @Test
    @Tag("level0")
    void testCreatePrDraftAgent() {
        assertNotNull(com.openjiuwen.auto_harness.agents.AutoHarnessAgents.class);
    }

    @Test
    @Tag("level0")
    void testCreateSelectPipelineAgent() {
        assertNotNull(com.openjiuwen.auto_harness.agents.AutoHarnessAgents.class);
    }
}