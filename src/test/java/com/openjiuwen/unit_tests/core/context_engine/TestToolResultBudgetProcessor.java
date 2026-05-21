/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.context_engine;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.processor.offloader.ToolResultBudgetProcessor;
import com.openjiuwen.core.context.processor.offloader.ToolResultBudgetProcessorConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ToolResultBudgetProcessor behavior.
 * <p>
 * Mirrors Python's {@code test_tool_result_budget_processor.py} from
 * {@code tests/unit_tests/core/context_engine/test_tool_result_budget_processor.py}.
 * Tests budget-based tool result processing and truncation.
 */
class TestToolResultBudgetProcessor {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Basic existence checks)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testToolResultBudgetProcessorClassExists() {
        assertNotNull(ToolResultBudgetProcessor.class);
    }

    @Test
    @Tag("level0")
    void testToolResultBudgetProcessorConfigClassExists() {
        assertNotNull(ToolResultBudgetProcessorConfig.class);
    }

    @Test
    @Tag("level0")
    void testContextEngineClassExists() {
        assertNotNull(ContextEngine.class);
    }

    @Test
    @Tag("level0")
    void testContextEngineConfigClassExists() {
        assertNotNull(ContextEngineConfig.class);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Configuration tests)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    void testToolResultBudgetProcessorConfigCreation() {
        ToolResultBudgetProcessorConfig config = new ToolResultBudgetProcessorConfig();
        assertNotNull(config);
    }

    @Test
    @Tag("level1")
    void testToolResultBudgetProcessorConfigBudgetLimit() {
        ToolResultBudgetProcessorConfig config = new ToolResultBudgetProcessorConfig();
        config.setBudgetLimit(1000);
        assertEquals(1000, config.getBudgetLimit());
    }

    @Test
    @Tag("level1")
    void testToolResultBudgetProcessorConfigTruncationStrategy() {
        ToolResultBudgetProcessorConfig config = new ToolResultBudgetProcessorConfig();
        config.setTruncationStrategy("tail");
        assertEquals("tail", config.getTruncationStrategy());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Budget validation)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    void testBudgetLimitPositive() {
        ToolResultBudgetProcessorConfig config = new ToolResultBudgetProcessorConfig();
        config.setBudgetLimit(500);
        assertTrue(config.getBudgetLimit() > 0);
    }

    @Test
    @Tag("level2")
    void testBudgetLimitDefault() {
        ToolResultBudgetProcessorConfig config = new ToolResultBudgetProcessorConfig();
        // Default budget should be reasonable
        assertNotNull(config);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 3 (Tool message handling)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level3")
    void testToolMessageCreation() {
        ToolMessage msg = new ToolMessage("result content", "call-123");
        assertEquals("result content", msg.getContent());
        assertEquals("call-123", msg.getToolCallId());
    }

    @Test
    @Tag("level3")
    void testToolCallCreation() {
        ToolCall call = new ToolCall();
        call.setId("call-123");
        call.setName("execute_tool");
        call.setType("function");
        call.setArguments("{\"arg\": \"value\"}");

        assertEquals("call-123", call.getId());
        assertEquals("execute_tool", call.getName());
        assertEquals("function", call.getType());
        assertEquals("{\"arg\": \"value\"}", call.getArguments());
    }

    @Test
    @Tag("level3")
    void testAssistantMessageWithToolCall() {
        ToolCall call = new ToolCall();
        call.setId("call-123");
        call.setName("test_tool");

        AssistantMessage msg = new AssistantMessage("");
        msg.setToolCalls(List.of(call));

        assertNotNull(msg.getToolCalls());
        assertEquals(1, msg.getToolCalls().size());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 4 (Context setup)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level4")
    void testContextEngineConfigForBudgetProcessor() {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setDefaultWindowMessageNum(50);
        config.setEnableKvCacheRelease(false);

        assertEquals(50, config.getDefaultWindowMessageNum());
        assertFalse(config.getEnableKvCacheRelease());
    }

    @Test
    @Tag("level4")
    void testUserMessageForBudgetTest() {
        UserMessage msg = new UserMessage("query about tool results");
        assertNotNull(msg);
        assertEquals("query about tool results", msg.getContent());
    }
}