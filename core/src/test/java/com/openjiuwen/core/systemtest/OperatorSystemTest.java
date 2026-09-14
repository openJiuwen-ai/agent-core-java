/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.operator.TunableSpec;
import com.openjiuwen.core.operator.llm_call.LLMCallOperator;
import com.openjiuwen.core.operator.tool_call.ToolCallOperator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the Operator module (LLMCallOperator, ToolCallOperator).
 * Corresponds to Python operator usage within ReAct agent workflows.
 */
@Tag("system-test")
class OperatorSystemTest {

    @Test
    @DisplayName("LLMCallOperator stores system and user prompt")
    void testLlmCallOperatorPrompts() {
        LLMCallOperator operator = new LLMCallOperator(
                "你是一个有用的AI助手。",
                "{{query}}");

        assertNotNull(operator.getSystemPrompt());
        assertNotNull(operator.getUserPrompt());
        assertEquals("你是一个有用的AI助手。", operator.getSystemPrompt().getContent());
        assertEquals("{{query}}", operator.getUserPrompt().getContent());
    }

    @Test
    @DisplayName("LLMCallOperator with frozen system prompt")
    void testLlmCallOperatorFrozenPrompt() {
        LLMCallOperator operator = new LLMCallOperator(
                "你是一个翻译助手，只做英文到中文的翻译。",
                "请翻译：{{query}}",
                true, true, "llm_call_frozen", null);

        Map<String, TunableSpec> tunables = operator.getTunables();
        assertTrue(tunables.isEmpty(), "Frozen prompts should have no tunables");
    }

    @Test
    @DisplayName("LLMCallOperator state snapshot and restore")
    void testLlmCallOperatorState() {
        LLMCallOperator operator = new LLMCallOperator(
                "System prompt",
                "User prompt: {{query}}");

        Map<String, Object> state = operator.getState();
        assertNotNull(state);
        assertTrue(state.containsKey("system_prompt"));
        assertTrue(state.containsKey("user_prompt"));

        // Restore state
        operator.loadState(state);
        Map<String, Object> stateAfterReload = operator.getState();
        assertNotNull(stateAfterReload);
    }

    @Test
    @DisplayName("ToolCallOperator with string operator id")
    void testToolCallOperator() {
        ToolCallOperator operator = new ToolCallOperator("multiply_tool");
        assertEquals("multiply_tool", operator.getOperatorId());
        Map<String, Object> state = operator.getState();
        assertNotNull(state);
    }

    @Test
    @DisplayName("LLMCallOperator tunable parameters")
    void testLlmCallOperatorTunables() {
        LLMCallOperator operator = new LLMCallOperator(
                "System: {{role}}",
                "{{query}}",
                false, true, "llm_tunables", null);

        Map<String, TunableSpec> tunables = operator.getTunables();
        assertNotNull(tunables);
        assertTrue(tunables.containsKey("system_prompt"),
                "Non-frozen system prompt should be tunable");
        assertFalse(tunables.containsKey("user_prompt"),
                "Frozen user prompt should not be tunable");
    }
}
