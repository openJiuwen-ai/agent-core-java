/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.harness.rails.interrupt.AskUserRail;
import com.openjiuwen.harness.rails.interrupt.AskUserRail.AskUserPayload;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision.InterruptResult;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision.RejectResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * System tests for DeepAgent ask_user rail.
 * <p>
 * Mirrors Python's {@code test_deep_agent_ask_user.py} in
 * {@code tests.system_tests.harness.rail}.
 */
class TestDeepAgentAskUser {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String QUESTION_TEXT = "Which implementation plan should we use?";

    @Test
    void testHitlAskUserRailMultiQuestion() {
        AskUserRail rail = new AskUserRail();
        ToolCall toolCall = buildPlanChoiceToolCall("ask_user_multi");

        InterruptDecision firstDecision = rail.resolveInterrupt(null, toolCall, null, null);
        Map<String, Object> request = requestMap(firstDecision);
        List<?> questions = assertInstanceOf(List.class, request.get("questions"));
        assertTrue(questions.size() >= 1, "Should have at least one question");

        Map<?, ?> firstQuestion = assertInstanceOf(Map.class, questions.get(0));
        assertTrue(firstQuestion.containsKey("question"));
        assertTrue(firstQuestion.containsKey("header"));
        assertTrue(firstQuestion.containsKey("options"));
        assertNotNull(request.get("payload_schema"));

        List<?> options = assertInstanceOf(List.class, firstQuestion.get("options"));
        assertTrue(options.size() >= 3, "Should have at least 3 options (Plan A, B, C)");
        String optionLabels = options.toString();
        assertTrue(optionLabels.contains("Plan A"));
        assertTrue(optionLabels.contains("Plan B"));
        assertTrue(optionLabels.contains("Plan C"));

        InterruptDecision resumeDecision = rail.resolveInterrupt(null, toolCall,
                Map.of("answers", Map.of(QUESTION_TEXT, "Plan B")), null);
        String result = rejectResult(resumeDecision);
        assertTrue(result.contains("User has answered your questions:"));
        assertTrue(result.contains("Plan B"));
    }

    @Test
    void testHitlAskUserRailWithPayloadObject() {
        AskUserRail rail = new AskUserRail();
        ToolCall toolCall = buildPlanChoiceToolCall("ask_user_payload");

        InterruptDecision firstDecision = rail.resolveInterrupt(null, toolCall, null, null);
        Map<String, Object> request = requestMap(firstDecision);
        assertNotNull(request.get("payload_schema"));
        List<?> questions = assertInstanceOf(List.class, request.get("questions"));
        assertTrue(questions.size() >= 1);

        AskUserPayload payload = new AskUserPayload(Map.of(QUESTION_TEXT, "user_answer_payload.txt"));
        InterruptDecision resumeDecision = rail.resolveInterrupt(null, toolCall, payload, null);
        String result = rejectResult(resumeDecision);
        assertTrue(result.contains("User has answered your questions:"));
        assertTrue(result.contains("user_answer_payload.txt"));
    }

    private static ToolCall buildPlanChoiceToolCall(String id) {
        try {
            Map<String, Object> arguments = Map.of("questions", List.of(Map.of(
                    "header", "Plan",
                    "question", QUESTION_TEXT,
                    "options", List.of(
                            Map.of("label", "Plan A", "description", "Fastest implementation."),
                            Map.of("label", "Plan B", "description", "Most stable implementation."),
                            Map.of("label", "Plan C", "description", "Most scalable implementation.")),
                    "multi_select", false)));
            return ToolCall.builder()
                    .id(id)
                    .type("function")
                    .name("ask_user")
                    .arguments(JSON.writeValueAsString(arguments))
                    .index(0)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build ask_user tool call", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requestMap(InterruptDecision decision) {
        InterruptResult interruptResult = assertInstanceOf(InterruptResult.class, decision);
        return (Map<String, Object>) interruptResult.getRequest();
    }

    private static String rejectResult(InterruptDecision decision) {
        RejectResult rejectResult = assertInstanceOf(RejectResult.class, decision);
        assertTrue(rejectResult.getToolResult().isPresent());
        return rejectResult.getToolResult().get().toString();
    }
}
