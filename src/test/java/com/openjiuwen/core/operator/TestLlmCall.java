/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.operator;

import com.openjiuwen.core.operator.llm_call.LLMCallOperator;
import com.openjiuwen.core.operator.TunableSpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LLMCallOperator.
 * Mirrors Python's tests/unit_tests/core/operator/test_llm_call.py
 */
class TestLlmCall {

    @Nested
    @DisplayName("LlmCall tests")
    class LlmCallTests {

        @Test
        @DisplayName("test operator id default")
        void testOperatorIdDefault() {
            // Test default operator_id.
            LLMCallOperator op = new LLMCallOperator("test_model", null, "sys", "{{query}}");
            assertEquals("llm_call", op.getOperatorId());
        }

        @Test
        @DisplayName("test operator id custom")
        void testOperatorIdCustom() {
            // Test custom operator_id.
            LLMCallOperator op = new LLMCallOperator("test_model", null, "sys", "{{query}}", 
                    false, false, "custom_id", null);
            assertEquals("custom_id", op.getOperatorId());
        }

        @Test
        @DisplayName("test get tunables both prompts")
        void testGetTunablesBothPrompts() {
            // Test getTunables returns both prompts when not frozen.
            LLMCallOperator op = new LLMCallOperator("test_model", null, "sys", "{{query}}", 
                    false, false, "llm_call", null);
            Map<String, TunableSpec> tunables = op.getTunables();

            assertTrue(tunables.containsKey("system_prompt"));
            assertTrue(tunables.containsKey("user_prompt"));
            assertEquals("prompt", tunables.get("system_prompt").kind());
            assertEquals("prompt", tunables.get("user_prompt").kind());
        }

        @Test
        @DisplayName("test get tunables frozen system prompt")
        void testGetTunablesFrozenSystemPrompt() {
            // Test getTunables excludes frozen system prompt.
            LLMCallOperator op = new LLMCallOperator("test_model", null, "sys", "{{query}}", 
                    true, false, "llm_call", null);
            Map<String, TunableSpec> tunables = op.getTunables();

            assertFalse(tunables.containsKey("system_prompt"));
            assertTrue(tunables.containsKey("user_prompt"));
        }

        @Test
        @DisplayName("test get tunables frozen user prompt")
        void testGetTunablesFrozenUserPrompt() {
            // Test getTunables excludes frozen user prompt.
            LLMCallOperator op = new LLMCallOperator("test_model", null, "sys", "{{query}}", 
                    false, true, "llm_call", null);
            Map<String, TunableSpec> tunables = op.getTunables();

            assertTrue(tunables.containsKey("system_prompt"));
            assertFalse(tunables.containsKey("user_prompt"));
        }

        @Test
        @DisplayName("test get tunables both frozen")
        void testGetTunablesBothFrozen() {
            // Test getTunables returns empty dict when both frozen.
            LLMCallOperator op = new LLMCallOperator("test_model", null, "sys", "{{query}}", 
                    true, true, "llm_call", null);
            Map<String, TunableSpec> tunables = op.getTunables();

            assertFalse(tunables.containsKey("system_prompt"));
            assertFalse(tunables.containsKey("user_prompt"));
        }

        @Test
        @DisplayName("test default user prompt constant")
        void testDefaultUserPromptConstant() {
            // Test DEFAULT_USER_PROMPT constant.
            assertEquals("{{query}}", LLMCallOperator.DEFAULT_USER_PROMPT);
        }
    }
}