/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.operator;

import com.openjiuwen.core.operator.TunableSpec;
import com.openjiuwen.core.operator.llm_call.LLMCallOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for LLMCallOperator.
 *
 * <p>Mirrors Python's tests/unit_tests/core/operator/test_llm_call.py.</p>
 */
class TestLlmCall {

    @Nested
    @DisplayName("LlmCall tests")
    class LlmCallTests {

        @Test
        @DisplayName("test operator id default")
        void testOperatorIdDefault() {
            LLMCallOperator op = new LLMCallOperator("test_model", null, "sys", "{{query}}");
            assertEquals("llm_call", op.getOperatorId());
        }

        @Test
        @DisplayName("test operator id custom")
        void testOperatorIdCustom() {
            LLMCallOperator op = new LLMCallOperator(
                    "test_model", null, "sys", "{{query}}", false, false, "custom_id", null);
            assertEquals("custom_id", op.getOperatorId());
        }

        @Test
        @DisplayName("test get tunables both prompts")
        void testGetTunablesBothPrompts() {
            LLMCallOperator op = new LLMCallOperator(
                    "test_model", null, "sys", "{{query}}", false, false, "llm_call", null);
            Map<String, TunableSpec> tunables = op.getTunables();

            assertTrue(tunables.containsKey("system_prompt"));
            assertTrue(tunables.containsKey("user_prompt"));
            assertEquals("prompt", tunables.get("system_prompt").kind());
            assertEquals("prompt", tunables.get("user_prompt").kind());
        }

        @Test
        @DisplayName("test get tunables frozen system prompt")
        void testGetTunablesFrozenSystemPrompt() {
            LLMCallOperator op = new LLMCallOperator(
                    "test_model", null, "sys", "{{query}}", true, false, "llm_call", null);
            Map<String, TunableSpec> tunables = op.getTunables();

            assertFalse(tunables.containsKey("system_prompt"));
            assertTrue(tunables.containsKey("user_prompt"));
        }

        @Test
        @DisplayName("test get tunables frozen user prompt")
        void testGetTunablesFrozenUserPrompt() {
            LLMCallOperator op = new LLMCallOperator(
                    "test_model", null, "sys", "{{query}}", false, true, "llm_call", null);
            Map<String, TunableSpec> tunables = op.getTunables();

            assertTrue(tunables.containsKey("system_prompt"));
            assertFalse(tunables.containsKey("user_prompt"));
        }

        @Test
        @DisplayName("test get tunables both frozen")
        void testGetTunablesBothFrozen() {
            LLMCallOperator op = new LLMCallOperator(
                    "test_model", null, "sys", "{{query}}", true, true, "llm_call", null);
            Map<String, TunableSpec> tunables = op.getTunables();

            assertFalse(tunables.containsKey("system_prompt"));
            assertFalse(tunables.containsKey("user_prompt"));
        }

        @Test
        @DisplayName("test default user prompt constant")
        void testDefaultUserPromptConstant() {
            assertEquals("{{query}}", LLMCallOperator.DEFAULT_USER_PROMPT);
        }

        @Test
        @DisplayName("test set parameter system prompt")
        void testSetParameterSystemPrompt() {
            LLMCallOperator op = createOperator();

            op.setParameter("system_prompt", "New system prompt");

            assertEquals("New system prompt", op.getState().get("system_prompt"));
        }

        @Test
        @DisplayName("test set parameter user prompt")
        void testSetParameterUserPrompt() {
            LLMCallOperator op = createOperator();

            op.setParameter("user_prompt", "New: {{query}}");

            assertEquals("New: {{query}}", op.getState().get("user_prompt"));
        }

        @Test
        @DisplayName("test set parameter frozen system prompt")
        void testSetParameterFrozenSystemPrompt() {
            LLMCallOperator op = new LLMCallOperator(
                    "test_model", null, "original", "{{query}}", true, false, "llm_call", null);
            Object original = op.getState().get("system_prompt");

            op.setParameter("system_prompt", "New prompt");

            assertEquals(original, op.getState().get("system_prompt"));
        }

        @Test
        @DisplayName("test set parameter frozen user prompt")
        void testSetParameterFrozenUserPrompt() {
            LLMCallOperator op = new LLMCallOperator(
                    "test_model", null, "sys", "original {{query}}", false, true, "llm_call", null);
            Object original = op.getState().get("user_prompt");

            op.setParameter("user_prompt", "New: {{query}}");

            assertEquals(original, op.getState().get("user_prompt"));
        }

        @Test
        @DisplayName("test get state")
        void testGetState() {
            LLMCallOperator op = createOperator();

            Map<String, Object> state = op.getState();

            assertEquals("You are a helpful assistant.", state.get("system_prompt"));
            assertEquals("Answer: {{query}}", state.get("user_prompt"));
        }

        @Test
        @DisplayName("test load state")
        void testLoadState() {
            LLMCallOperator op = createOperator();

            op.loadState(Map.of(
                    "system_prompt", "Loaded system",
                    "user_prompt", "Loaded: {{query}}"));

            assertEquals("Loaded system", op.getState().get("system_prompt"));
            assertEquals("Loaded: {{query}}", op.getState().get("user_prompt"));
        }

        @Test
        @DisplayName("test load state triggers callback")
        void testLoadStateTriggersCallback() {
            List<List<Object>> calls = new ArrayList<>();
            LLMCallOperator op = new LLMCallOperator(
                    "test_model", null, "original system", "original {{query}}",
                    false, false, "llm_call", (target, value) -> calls.add(List.of(target, value)));

            op.loadState(Map.of(
                    "system_prompt", "Loaded system",
                    "user_prompt", "Loaded: {{query}}"));

            assertEquals(2, calls.size());
            assertTrue(calls.contains(List.of("system_prompt", "Loaded system")));
            assertTrue(calls.contains(List.of("user_prompt", "Loaded: {{query}}")));
        }

        @Test
        @DisplayName("test load state partial")
        void testLoadStatePartial() {
            LLMCallOperator op = createOperator();

            op.loadState(Map.of("system_prompt", "Partial load"));

            assertEquals("Partial load", op.getState().get("system_prompt"));
            assertEquals("Answer: {{query}}", op.getState().get("user_prompt"));
        }

        @Test
        @DisplayName("test get freeze system prompt")
        void testGetFreezeSystemPrompt() {
            LLMCallOperator op = new LLMCallOperator(
                    "test_model", null, "sys", "{{query}}", true, false, "llm_call", null);

            assertTrue(op.getFreezeSystemPrompt());
        }

        @Test
        @DisplayName("test get freeze user prompt")
        void testGetFreezeUserPrompt() {
            LLMCallOperator op = new LLMCallOperator(
                    "test_model", null, "sys", "{{query}}", false, true, "llm_call", null);

            assertTrue(op.getFreezeUserPrompt());
        }

        @Test
        @DisplayName("test set freeze system prompt")
        void testSetFreezeSystemPrompt() {
            LLMCallOperator op = createOperator();

            op.setFreezeSystemPrompt(true);

            assertTrue(op.getFreezeSystemPrompt());
        }

        @Test
        @DisplayName("test set freeze user prompt")
        void testSetFreezeUserPrompt() {
            LLMCallOperator op = createOperator();

            op.setFreezeUserPrompt(true);

            assertTrue(op.getFreezeUserPrompt());
        }

        @Test
        @DisplayName("test on parameter updated callback")
        void testOnParameterUpdatedCallback() {
            List<List<Object>> calls = new ArrayList<>();
            LLMCallOperator op = new LLMCallOperator(
                    "test_model", null, "sys", "{{query}}",
                    false, false, "llm_call", (target, value) -> calls.add(List.of(target, value)));

            op.setParameter("system_prompt", "New prompt");

            assertEquals(List.of(List.of("system_prompt", "New prompt")), calls);
        }

        private LLMCallOperator createOperator() {
            return new LLMCallOperator(
                    "test_model", null, "You are a helpful assistant.", "Answer: {{query}}",
                    false, false, "llm_call", null);
        }
    }
}
