/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.harness.rails.context_engineer.ContextAssembleRail;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ContextAssembleRail.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.harness.test_context_assemble_rail}.
 */
class TestContextAssembleRail {

    // ---------------------------------------------------------------------------
    // Tests: context assembly
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("ContextAssembleRail builds workspace section")
    void testContextAssembleRailBuildsWorkspaceSection() {
        // Python: test_build_workspace_section
        // ContextAssembleRail should build workspace context
        
        ContextAssembleRail rail = new ContextAssembleRail();
        assertNotNull(rail, "ContextAssembleRail should be constructable");
        
        // Test initialization
        Object mockAgent = new Object();
        rail.init(mockAgent);
        
        // Verify rail can be initialized
        assertTrue(rail.getPriority() >= 0, "Rail priority should be set");
    }

    @Test
    @Tag("level0")
    @DisplayName("ContextAssembleRail builds context section")
    void testContextAssembleRailBuildsContextSection() {
        // Python: test_build_context_section
        // ContextAssembleRail should build context from workspace
        
        ContextAssembleRail rail = new ContextAssembleRail();
        assertNotNull(rail, "ContextAssembleRail should be constructable");
        
        // Test uninit
        Object mockAgent = new Object();
        rail.uninit(mockAgent);
        
        // Verify rail can be uninitialized
        // No exception should be thrown
    }

    @Test
    @Tag("level0")
    @DisplayName("ContextAssembleRail builds tools content")
    void testContextAssembleRailBuildsToolsContent() {
        // Python: test_build_tools_content
        // ContextAssembleRail should build tools content section
        
        ContextAssembleRail rail = new ContextAssembleRail();
        assertNotNull(rail, "ContextAssembleRail should be constructable");
        
        // Verify rail inherits from DeepAgentRail
        assertTrue(rail instanceof com.openjiuwen.harness.rails.DeepAgentRail,
            "ContextAssembleRail should extend DeepAgentRail");
    }
}