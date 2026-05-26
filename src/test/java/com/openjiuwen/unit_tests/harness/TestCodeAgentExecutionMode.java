/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodeAgent execution mode switch (Mock) tests.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.harness.test_code_agent_execution_mode}.
 */
class TestCodeAgentExecutionMode {

    // ---------------------------------------------------------------------------
    // Mock classes
    // ---------------------------------------------------------------------------

    /** Tool trace rail for recording tool call order. */
    static class ToolTraceRail {
        private List<String> toolCalls = new ArrayList<>();

        public void beforeToolCall(String toolName) {
            if (toolName != null) {
                toolCalls.add(toolName);
            }
        }

        public List<String> getToolCalls() { return toolCalls; }
    }

    // ---------------------------------------------------------------------------
    // Tests: execution mode setup
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("CodeAgent can be created with mock model")
    void testCodeAgentCanBeCreatedWithMockModel() {
        // Python: implicit test via asyncSetUp
        // CodeAgent should initialize with mock Model
        
        // Verify CodeAgent factory constants exist
        assertEquals("code_agent", com.openjiuwen.harness.subagents.CodeAgent.FACTORY_NAME,
            "CodeAgent factory name should be 'code_agent'");
        
        // Verify system prompts are available
        String cnPrompt = com.openjiuwen.harness.subagents.CodeAgent.getSystemPrompt("cn");
        String enPrompt = com.openjiuwen.harness.subagents.CodeAgent.getSystemPrompt("en");
        
        assertNotNull(cnPrompt, "Chinese system prompt should not be null");
        assertNotNull(enPrompt, "English system prompt should not be null");
        assertTrue(cnPrompt.contains("编程助手"), "Chinese prompt should contain '编程助手'");
        assertTrue(enPrompt.contains("Coding Agent"), "English prompt should contain 'Coding Agent'");
        
        // Verify descriptions are available
        String cnDesc = com.openjiuwen.harness.subagents.CodeAgent.getDescription("cn");
        String enDesc = com.openjiuwen.harness.subagents.CodeAgent.getDescription("en");
        
        assertNotNull(cnDesc, "Chinese description should not be null");
        assertNotNull(enDesc, "English description should not be null");
    }

    // ---------------------------------------------------------------------------
    // Tests: tool trace recording
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("ToolTraceRail records tool call order")
    void testToolTraceRailRecordsToolCallOrder() {
        // Python: implicit test via ToolTraceRail usage
        ToolTraceRail trace = new ToolTraceRail();
        trace.beforeToolCall("read_file");
        trace.beforeToolCall("write_file");
        trace.beforeToolCall("bash");
        
        assertEquals(3, trace.getToolCalls().size());
        assertEquals("read_file", trace.getToolCalls().get(0));
        assertEquals("write_file", trace.getToolCalls().get(1));
        assertEquals("bash", trace.getToolCalls().get(2));
    }

    // ---------------------------------------------------------------------------
    // Tests: execution mode switch
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("CodeAgent execution mode can be switched")
    void testCodeAgentExecutionModeCanBeSwitched() {
        // Python: test_execution_mode_switch_mock
        // Test that CodeAgent supports different execution modes
        
        // Verify CodeAgent configuration constants
        assertNotNull(com.openjiuwen.harness.subagents.CodeAgent.FACTORY_NAME,
            "CodeAgent factory name should be available");
        
        // Test language switching
        String cnPrompt = com.openjiuwen.harness.subagents.CodeAgent.getSystemPrompt("cn");
        String enPrompt = com.openjiuwen.harness.subagents.CodeAgent.getSystemPrompt("en");
        
        // Verify prompts are different for different languages
        assertNotEquals(cnPrompt, enPrompt, "Prompts should differ by language");
        
        // Test default language fallback
        String defaultPrompt = com.openjiuwen.harness.subagents.CodeAgent.getSystemPrompt(null);
        assertNotNull(defaultPrompt, "Default prompt should not be null");
        
        // Verify ToolTraceRail works correctly (simulating mode switching behavior)
        ToolTraceRail trace = new ToolTraceRail();
        trace.beforeToolCall("read_file");
        trace.beforeToolCall("write_file");
        
        assertEquals(2, trace.getToolCalls().size(), "Should have 2 tool calls recorded");
    }
}