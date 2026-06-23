/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for LLM workflow resume input construction.
 *
 * <p>Mirrors Python's {@code LLMController._create_query_resume_input} in
 * {@code openjiuwen/core/application/llm_agent/llm_controller.py}.</p>
 */
class LlmEventHandlerTest {

    @Test
    void buildResumeInteractiveInputUsesRawInputWhenNoComponentIds() throws Exception {
        InteractiveInput input = buildResumeInteractiveInput(List.of());

        assertEquals("reply", input.getRawInputs());
        assertTrue(input.getUserInputs().isEmpty());
    }

    @Test
    void buildResumeInteractiveInputUpdatesOnlyFirstNonQuestionerComponent() throws Exception {
        InteractiveInput input = buildResumeInteractiveInput(List.of("approval", "questioner"));

        assertNull(input.getRawInputs());
        assertEquals(1, input.getUserInputs().size());
        assertEquals("reply", input.getUserInputs().get("approval"));
    }

    @Test
    void buildResumeInteractiveInputUpdatesAllQuestionerLedComponents() throws Exception {
        InteractiveInput input = buildResumeInteractiveInput(List.of("questioner", "memory"));

        assertNull(input.getRawInputs());
        assertEquals(2, input.getUserInputs().size());
        assertEquals("reply", input.getUserInputs().get("questioner"));
        assertEquals("reply", input.getUserInputs().get("memory"));
    }

    private static InteractiveInput buildResumeInteractiveInput(List<String> componentIds) throws Exception {
        LlmEventHandler handler = new LlmEventHandler(new LegacyReActAgentConfig(), new ContextEngine());
        Method method = LlmEventHandler.class.getDeclaredMethod(
                "buildResumeInteractiveInput", String.class, List.class);
        method.setAccessible(true);
        return (InteractiveInput) method.invoke(handler, "reply", componentIds);
    }
}
