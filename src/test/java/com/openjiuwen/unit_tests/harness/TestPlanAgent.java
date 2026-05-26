/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.harness.subagents.PlanAgent;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PlanAgent.
 */
class TestPlanAgent {

    @Test
    @Tag("level0")
    @DisplayName("PlanAgent handles planning correctly")
    void testPlanAgentHandlesPlanning() {
        assertEquals("plan_agent", PlanAgent.FACTORY_NAME,
            "Factory name should be 'plan_agent'");
        
        String cnPrompt = PlanAgent.getSystemPrompt("cn");
        String enPrompt = PlanAgent.getSystemPrompt("en");
        
        assertNotNull(cnPrompt, "Chinese prompt should not be null");
        assertNotNull(enPrompt, "English prompt should not be null");
        assertTrue(cnPrompt.contains("规划"), "Chinese prompt should contain '规划'");
        assertTrue(enPrompt.contains("planning"), "English prompt should contain 'planning'");
    }
}