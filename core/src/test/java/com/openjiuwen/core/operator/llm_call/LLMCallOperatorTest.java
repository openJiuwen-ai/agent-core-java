/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.operator.llm_call;

import com.openjiuwen.core.operator.TunableSpec;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for LLM prompt parameter handle behavior.
 *
 * <p>Mirrors Python's {@code LLMCallOperator} in
 * {@code openjiuwen/core/operator/llm_call/base.py}.</p>
 */
class LLMCallOperatorTest {

    @Test
    void constructorUsesPythonDefaultsAndTunableOrder() {
        LLMCallOperator operator = new LLMCallOperator("system", "");

        assertEquals("llm_call", operator.getOperatorId());
        assertEquals("system", operator.getSystemPrompt().getContent());
        assertEquals(LLMCallOperator.DEFAULT_USER_PROMPT, operator.getUserPrompt().getContent());
        assertEquals(List.of("system_prompt"), new ArrayList<>(operator.getTunables().keySet()));
    }

    @Test
    void getTunablesExcludesFrozenPrompts() {
        LLMCallOperator operator = new LLMCallOperator(
                "system",
                "user",
                false,
                false,
                "custom_llm",
                null
        );

        Map<String, TunableSpec> tunables = operator.getTunables();

        assertEquals("custom_llm", operator.getOperatorId());
        assertEquals(List.of("system_prompt", "user_prompt"), new ArrayList<>(tunables.keySet()));
        assertEquals("prompt", tunables.get("system_prompt").kind());
        assertEquals("user_prompt", tunables.get("user_prompt").path());
    }

    @Test
    void setParameterUpdatesOnlyUnfrozenTargetsAndNormalizesPythonStrings() {
        List<String> updates = new ArrayList<>();
        List<Map<String, String>> userMessages = List.of(Map.of("role", "user", "content", "hello"));
        LLMCallOperator operator = new LLMCallOperator(
                "system",
                "user",
                false,
                false,
                "llm_call",
                (target, value) -> updates.add(target + "=" + value)
        );

        operator.setParameter("system_prompt", Boolean.TRUE);
        operator.setParameter("user_prompt", userMessages);

        assertEquals("True", operator.getSystemPrompt().getContent());
        assertEquals(userMessages, operator.getUserPrompt().getContent());
        assertEquals(List.of(
                "system_prompt=True",
                "user_prompt=" + userMessages
        ), updates);
    }

    @Test
    void frozenSetParameterIsIgnoredButLoadStateBypassesFreezeMarkers() {
        List<String> updates = new ArrayList<>();
        LLMCallOperator operator = new LLMCallOperator(
                "system",
                "user",
                true,
                true,
                "llm_call",
                (target, value) -> updates.add(target + "=" + value)
        );

        operator.setParameter("system_prompt", "blocked-system");
        operator.setParameter("user_prompt", "blocked-user");

        assertEquals("system", operator.getSystemPrompt().getContent());
        assertEquals("user", operator.getUserPrompt().getContent());
        assertTrue(updates.isEmpty());

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("system_prompt", "restored-system");
        state.put("user_prompt", 42);
        operator.loadState(state);

        assertEquals("restored-system", operator.getSystemPrompt().getContent());
        assertEquals("42", operator.getUserPrompt().getContent());
        assertEquals(List.of(
                "system_prompt=restored-system",
                "user_prompt=42"
        ), updates);
    }

    @Test
    void dictLikeValuesUsePythonStyleStringConversion() {
        LLMCallOperator operator = new LLMCallOperator("system", "user", false, false, "llm_call", null);
        Map<String, Object> dict = new LinkedHashMap<>();
        dict.put("enabled", Boolean.FALSE);
        dict.put("name", "Ada");

        operator.setParameter("system_prompt", dict);

        assertEquals("{'enabled': False, 'name': 'Ada'}", operator.getSystemPrompt().getContent());
    }

    @Test
    void stateAndFreezeAccessorsMirrorPythonProperties() {
        LLMCallOperator operator = new LLMCallOperator("system", "user");

        operator.setFreezeSystemPrompt(true);
        operator.setFreezeUserPrompt(false);
        Map<String, Object> state = operator.getState();

        assertTrue(operator.getFreezeSystemPrompt());
        assertFalse(operator.getFreezeUserPrompt());
        assertEquals("system", state.get("system_prompt"));
        assertEquals("user", state.get("user_prompt"));
    }

    @Test
    void backwardCompatibleAliasExtendsOperatorImplementation() {
        LLMCall alias = new LLMCall("system", "user");

        assertInstanceOf(LLMCallOperator.class, alias);
        assertEquals("llm_call", alias.getOperatorId());
        assertEquals("system", alias.getState().get("system_prompt"));
    }
}
