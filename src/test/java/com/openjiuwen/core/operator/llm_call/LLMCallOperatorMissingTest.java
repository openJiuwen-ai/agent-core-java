/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.operator.llm_call;

import com.openjiuwen.agent_evolving.ApplyResult;
import com.openjiuwen.agent_evolving.UpdateValue;
import com.openjiuwen.core.operator.TunableSpec;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Supplemental tests for {@link LLMCallOperator}.
 *
 * <p>Mirrors Python's {@code TestLLMCallOperator} in
 * {@code tests/unit_tests/core/operator/test_llm_call.py}.</p>
 */
class LLMCallOperatorMissingTest {

    @Test
    void operatorIdDefaultIsLlmCall() {
        LLMCallOperator operator = defaultOperator();

        assertEquals("llm_call", operator.getOperatorId());
    }

    @Test
    void operatorIdUsesCustomValue() {
        LLMCallOperator operator = new LLMCallOperator("sys", "{{query}}", false, true, "custom_id", null);

        assertEquals("custom_id", operator.getOperatorId());
    }

    @Test
    void getTunablesReturnsBothPromptsWhenUnfrozen() {
        LLMCallOperator operator = new LLMCallOperator("sys", "{{query}}", false, false, "llm_call", null);

        Map<String, TunableSpec> tunables = operator.getTunables();

        assertTrue(tunables.containsKey("system_prompt"));
        assertTrue(tunables.containsKey("user_prompt"));
        assertEquals("prompt", tunables.get("system_prompt").kind());
        assertEquals("prompt", tunables.get("user_prompt").kind());
    }

    @Test
    void getTunablesExcludesFrozenSystemPrompt() {
        LLMCallOperator operator = new LLMCallOperator("sys", "{{query}}", true, false, "llm_call", null);

        Map<String, TunableSpec> tunables = operator.getTunables();

        assertFalse(tunables.containsKey("system_prompt"));
        assertTrue(tunables.containsKey("user_prompt"));
    }

    @Test
    void getTunablesExcludesFrozenUserPrompt() {
        LLMCallOperator operator = new LLMCallOperator("sys", "{{query}}", false, true, "llm_call", null);

        Map<String, TunableSpec> tunables = operator.getTunables();

        assertTrue(tunables.containsKey("system_prompt"));
        assertFalse(tunables.containsKey("user_prompt"));
    }

    @Test
    void getTunablesReturnsEmptyWhenBothPromptsFrozen() {
        LLMCallOperator operator = new LLMCallOperator("sys", "{{query}}", true, true, "llm_call", null);

        assertTrue(operator.getTunables().isEmpty());
    }

    @Test
    void setParameterUpdatesSystemPrompt() {
        LLMCallOperator operator = defaultOperator();

        operator.setParameter("system_prompt", "New system prompt");

        assertEquals("New system prompt", operator.getState().get("system_prompt"));
    }

    @Test
    void setParameterUpdatesUserPrompt() {
        LLMCallOperator operator = defaultOperator();

        operator.setParameter("user_prompt", "New: {{query}}");

        assertEquals("New: {{query}}", operator.getState().get("user_prompt"));
    }

    @Test
    void setParameterIgnoresFrozenSystemPrompt() {
        LLMCallOperator operator = new LLMCallOperator("original", "{{query}}", true, true, "llm_call", null);
        Object original = operator.getState().get("system_prompt");

        operator.setParameter("system_prompt", "New prompt");

        assertEquals(original, operator.getState().get("system_prompt"));
    }

    @Test
    void setParameterIgnoresFrozenUserPrompt() {
        LLMCallOperator operator = new LLMCallOperator("sys", "original {{query}}", false, true, "llm_call", null);
        Object original = operator.getState().get("user_prompt");

        operator.setParameter("user_prompt", "New: {{query}}");

        assertEquals(original, operator.getState().get("user_prompt"));
    }

    @Test
    void getStateReturnsPromptContents() {
        LLMCallOperator operator = defaultOperator();

        Map<String, Object> state = operator.getState();

        assertTrue(state.containsKey("system_prompt"));
        assertTrue(state.containsKey("user_prompt"));
        assertEquals("You are a helpful assistant.", state.get("system_prompt"));
        assertEquals("Answer: {{query}}", state.get("user_prompt"));
    }

    @Test
    void loadStateRestoresPromptContents() {
        LLMCallOperator operator = defaultOperator();

        operator.loadState(Map.of(
                "system_prompt", "Loaded system",
                "user_prompt", "Loaded: {{query}}"
        ));

        assertEquals("Loaded system", operator.getState().get("system_prompt"));
        assertEquals("Loaded: {{query}}", operator.getState().get("user_prompt"));
    }

    @Test
    void loadStateTriggersCallbackForBothParameters() {
        List<String> callbacks = new ArrayList<>();
        LLMCallOperator operator = new LLMCallOperator(
                "original system",
                "original {{query}}",
                false,
                true,
                "llm_call",
                (target, value) -> callbacks.add(target + "=" + value)
        );

        operator.loadState(Map.of(
                "system_prompt", "Loaded system",
                "user_prompt", "Loaded: {{query}}"
        ));

        assertEquals(2, callbacks.size());
        assertTrue(callbacks.contains("system_prompt=Loaded system"));
        assertTrue(callbacks.contains("user_prompt=Loaded: {{query}}"));
    }

    @Test
    void loadStateKeepsUnspecifiedPromptUnchanged() {
        LLMCallOperator operator = defaultOperator();

        operator.loadState(Map.of("system_prompt", "Partial load"));

        assertEquals("Partial load", operator.getState().get("system_prompt"));
        assertEquals("Answer: {{query}}", operator.getState().get("user_prompt"));
    }

    @Test
    void getFreezeSystemPromptReturnsCurrentFlag() {
        LLMCallOperator operator = new LLMCallOperator("sys", "{{query}}", true, true, "llm_call", null);

        assertTrue(operator.getFreezeSystemPrompt());
    }

    @Test
    void getFreezeUserPromptReturnsCurrentFlag() {
        LLMCallOperator operator = new LLMCallOperator("sys", "{{query}}", false, true, "llm_call", null);

        assertTrue(operator.getFreezeUserPrompt());
    }

    @Test
    void setFreezeSystemPromptUpdatesFlag() {
        LLMCallOperator operator = defaultOperator();

        operator.setFreezeSystemPrompt(true);

        assertTrue(operator.getFreezeSystemPrompt());
    }

    @Test
    void setFreezeUserPromptUpdatesFlag() {
        LLMCallOperator operator = defaultOperator();

        operator.setFreezeUserPrompt(true);

        assertTrue(operator.getFreezeUserPrompt());
    }

    @Test
    void setParameterInvokesCallbackForChangedParameter() {
        List<String> callbacks = new ArrayList<>();
        LLMCallOperator operator = new LLMCallOperator(
                "sys",
                "{{query}}",
                false,
                true,
                "llm_call",
                (target, value) -> callbacks.add(target + "=" + value)
        );

        operator.setParameter("system_prompt", "New prompt");

        assertEquals(List.of("system_prompt=New prompt"), callbacks);
    }

    @Test
    void applyUpdateReusesReplaceStateBehavior() {
        LLMCallOperator operator = defaultOperator();

        ApplyResult result = operator.applyUpdate("system_prompt", new UpdateValue("New system prompt"));

        assertEquals("New system prompt", operator.getState().get("system_prompt"));
        assertTrue(result.isApplied());
        assertEquals("replace", result.getMode());
        assertEquals("state", result.getEffect());
        assertEquals("New system prompt", result.getValue());
    }

    @Test
    void applyUpdateReportsNoopForFrozenPrompt() {
        LLMCallOperator operator = new LLMCallOperator("sys", "{{query}}", true, true, "llm_call", null);

        ApplyResult result = operator.applyUpdate("system_prompt", new UpdateValue("New system prompt"));

        assertEquals("sys", operator.getState().get("system_prompt"));
        assertFalse(result.isApplied());
    }

    private static LLMCallOperator defaultOperator() {
        return new LLMCallOperator(
                "You are a helpful assistant.",
                "Answer: {{query}}",
                false,
                false,
                "llm_call",
                null
        );
    }
}
