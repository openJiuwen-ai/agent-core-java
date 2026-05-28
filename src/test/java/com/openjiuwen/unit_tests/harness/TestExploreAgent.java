/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.harness.subagents.ExploreAgent;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ExploreAgent.
 */
class TestExploreAgent {

    @Test
    @Tag("level0")
    @DisplayName("ExploreAgent can be created")
    void testExploreAgentCanBeCreated() {
        assertEquals("explore_agent", ExploreAgent.FACTORY_NAME,
            "Factory name should be 'explore_agent'");
        
        String cnPrompt = ExploreAgent.getSystemPrompt("cn");
        String enPrompt = ExploreAgent.getSystemPrompt("en");
        
        assertNotNull(cnPrompt, "Chinese prompt should not be null");
        assertNotNull(enPrompt, "English prompt should not be null");
        assertTrue(cnPrompt.contains("探索"), "Chinese prompt should contain '探索'");
        assertTrue(enPrompt.contains("exploration"), "English prompt should contain 'exploration'");
    }
}