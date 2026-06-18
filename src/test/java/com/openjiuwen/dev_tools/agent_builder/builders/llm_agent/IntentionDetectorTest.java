/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.openjiuwen.core.common.exception.ApplicationError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Focused parity tests for LLM-agent intention detection.
 *
 * <p>Mirrors Python's {@code IntentionDetector} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/llm_agent/intention_detector.py}.</p>
 */
class IntentionDetectorTest {

    @Test
    void extractIntentParsesMarkdownJsonBlock() {
        Map<String, Object> result = IntentionDetector.extractIntent("""
                ```json
                {"need_refined": true}
                ```
                """);

        assertEquals(Boolean.TRUE, result.get("need_refined"));
    }

    @Test
    void detectRefineIntentReturnsFalseForEmptyQueryWithoutCallingModel() {
        List<List<BaseMessage>> captured = new ArrayList<>();
        IntentionDetector detector = new IntentionDetector(modelReturning(captured, "{\"need_refined\": true}"));

        assertFalse(detector.detectRefineIntent("", "config"));
        assertTrue(captured.isEmpty());
    }

    @Test
    void detectRefineIntentBuildsPromptAndReturnsNeedRefined() {
        List<List<BaseMessage>> captured = new ArrayList<>();
        IntentionDetector detector = new IntentionDetector(modelReturning(
                captured,
                "```json\n{\"need_refined\": true}\n```"
        ));

        assertTrue(detector.detectRefineIntent("please refine", "agent config"));

        assertEquals(1, captured.size());
        assertInstanceOf(SystemMessage.class, captured.get(0).get(0));
        assertEquals(LlmAgentPrompts.REFINE_INTENTION_SYSTEM_PROMPT, captured.get(0).get(0).getContent());
        String userPrompt = String.valueOf(captured.get(0).get(1).getContent());
        assertTrue(userPrompt.contains("please refine"));
        assertTrue(userPrompt.contains("agent config"));
    }

    @Test
    void detectRefineIntentWrapsFailuresAsApplicationError() {
        IntentionDetector detector = new IntentionDetector(modelReturning(new ArrayList<>(), "[1, 2]"));

        try {
            detector.detectRefineIntent("refine", "config");
            fail("expected ApplicationError");
        } catch (ApplicationError error) {
            assertSame(StatusCode.ERROR, error.getStatus());
            Map<?, ?> details = assertInstanceOf(Map.class, error.getDetails());
            assertEquals(StatusCode.LLM_AGENT_STATE_ERROR.getCode(), details.get("error_code"));
        }
    }

    private static Model modelReturning(List<List<BaseMessage>> capturedMessages, String response) {
        return new Model((messages, modelConfig, modelClientConfig, options) -> {
            capturedMessages.add(new ArrayList<>(messages));
            return CompletableFuture.completedFuture(new AssistantMessage(response));
        });
    }
}
