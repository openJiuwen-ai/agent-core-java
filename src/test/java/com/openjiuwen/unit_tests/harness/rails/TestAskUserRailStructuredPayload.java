/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.rails;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.harness.rails.interrupt.AskUserRail;
import com.openjiuwen.harness.rails.interrupt.AskUserRail.AskUserPayload;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision.InterruptResult;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision.RejectResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for AskUserRail structured payload handling.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.harness.rails.test_ask_user_rail_structured_payload}.
 */
@ExtendWith(MockitoExtension.class)
class TestAskUserRailStructuredPayload {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ToolCall buildToolCall(Map<String, Object> arguments) {
        return buildToolCall(arguments, "tool_ask_1");
    }

    private ToolCall buildToolCall(Map<String, Object> arguments, String toolCallId) {
        try {
            return ToolCall.builder()
                    .id(toolCallId)
                    .type("function")
                    .name("ask_user")
                    .arguments(JSON.writeValueAsString(arguments))
                    .index(0)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize tool-call arguments", e);
        }
    }

    private Map<String, Object> singleQuestionArgs() {
        return Map.of("questions", List.of(Map.of(
                "header", "Feature",
                "question", "Which feature should be enabled?",
                "options", List.of(
                        Map.of("label", "Dark Mode", "description", "Enable dark theme."),
                        Map.of("label", "Auto Save", "description", "Save changes automatically.")),
                "multi_select", false)));
    }

    private Map<String, Object> singleQuestionWithPreviewArgs() {
        return Map.of("questions", List.of(Map.of(
                "header", "Design",
                "question", "Which design do you prefer?",
                "options", List.of(
                        Map.of(
                                "label", "Option A",
                                "description", "Simple layout with sidebar.",
                                "preview", "+------+---------+\n| nav  | content |\n+------+---------+"),
                        Map.of(
                                "label", "Option B",
                                "description", "Full-width layout.",
                                "preview", "+----------------+\n|  content area  |\n+----------------+")),
                "multi_select", false)));
    }

    private Map<String, Object> multiQuestionArgs() {
        return Map.of("questions", List.of(
                Map.of(
                        "header", "Framework",
                        "question", "Which framework?",
                        "options", List.of(
                                Map.of("label", "React", "description", "React ecosystem."),
                                Map.of("label", "Vue", "description", "Vue ecosystem.")),
                        "multi_select", false),
                Map.of(
                        "header", "Auth",
                        "question", "How to authenticate?",
                        "options", List.of(
                                Map.of("label", "JWT", "description", "Token auth."),
                                Map.of("label", "Session", "description", "Session-based auth.")),
                        "multi_select", false)));
    }

    @Test
    @Tag("level0")
    @DisplayName("First call with questions should return InterruptResult with questions field")
    void testFirstCallInterruptContainsQuestionsField() {
        AskUserRail rail = new AskUserRail();
        ToolCall toolCall = buildToolCall(singleQuestionArgs());

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, null, null);

        assertTrue(decision.isInterrupted());
        Map<String, Object> request = requestMap(decision);
        List<?> questions = (List<?>) request.get("questions");
        assertNotNull(questions);
        assertEquals(1, questions.size());
        Map<?, ?> question = (Map<?, ?>) questions.get(0);
        assertEquals("Which feature should be enabled?", question.get("question"));
        assertEquals("Feature", question.get("header"));
        assertEquals("", request.get("message"));
        Map<?, ?> payloadSchema = (Map<?, ?>) request.get("payload_schema");
        assertTrue(((Map<?, ?>) payloadSchema.get("properties")).containsKey("answers"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Resume with answer string returns formatted result")
    void testResumeWithAnswerStringReturnsFormattedResult() {
        AskUserRail rail = new AskUserRail();
        ToolCall toolCall = buildToolCall(singleQuestionArgs());
        Map<String, Object> userInput = Map.of(
                "answers", Map.of("Which feature should be enabled?", "Dark Mode"));

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, userInput, null);

        assertTrue(decision.isRejected());
        String result = rejectResult(decision);
        assertTrue(result.contains("User has answered your questions:"));
        assertTrue(result.contains("\"Which feature should be enabled?\"=\"Dark Mode\""));
    }

    @Test
    @Tag("level0")
    @DisplayName("Resume with AskUserPayload returns formatted result")
    void testResumeWithAskUserPayloadReturnsFormattedResult() {
        AskUserRail rail = new AskUserRail();
        ToolCall toolCall = buildToolCall(singleQuestionArgs());
        AskUserPayload userInput = new AskUserPayload(Map.of(
                "Which feature should be enabled?", "Auto Save"));

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, userInput, null);

        assertTrue(decision.isRejected());
        String result = rejectResult(decision);
        assertTrue(result.contains("User has answered your questions:"));
        assertTrue(result.contains("\"Which feature should be enabled?\"=\"Auto Save\""));
    }

    @Test
    @Tag("level0")
    @DisplayName("Resume with string for multi-question only answers first")
    void testResumeWithStringForMultiQuestionOnlyAnswersFirst() {
        AskUserRail rail = new AskUserRail();
        ToolCall toolCall = buildToolCall(multiQuestionArgs());

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, "React", null);

        assertTrue(decision.isRejected());
        String result = rejectResult(decision);
        assertTrue(result.contains("User has answered your questions:"));
        assertTrue(result.contains("\"Which framework?\"=\"React\""));
        assertTrue(result.contains("\"How to authenticate?\"=\"\""));
    }

    @Test
    @Tag("level0")
    @DisplayName("Resume with structured answers returns formatted result")
    void testResumeWithStructuredAnswersReturnsFormattedResult() {
        AskUserRail rail = new AskUserRail();
        ToolCall toolCall = buildToolCall(multiQuestionArgs());
        Map<String, Object> userInput = Map.of(
                "answers", Map.of(
                        "Which framework?", "React",
                        "How to authenticate?", "JWT"));

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, userInput, null);

        assertTrue(decision.isRejected());
        String result = rejectResult(decision);
        assertTrue(result.contains("User has answered your questions:"));
        assertTrue(result.contains("\"Which framework?\"=\"React\""));
        assertTrue(result.contains("\"How to authenticate?\"=\"JWT\""));
    }

    @Test
    @Tag("level0")
    @DisplayName("Resume with string directly returns formatted result")
    void testResumeWithStringDirectlyReturnsFormattedResult() {
        AskUserRail rail = new AskUserRail();
        ToolCall toolCall = buildToolCall(singleQuestionArgs());

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, "Dark Mode", null);

        assertTrue(decision.isRejected());
        String result = rejectResult(decision);
        assertTrue(result.contains("User has answered your questions:"));
        assertTrue(result.contains("\"Which feature should be enabled?\"=\"Dark Mode\""));
    }

    @Test
    @Tag("level0")
    @DisplayName("Multi-question interrupt contains all questions in questions field")
    void testMultiQuestionInterruptContainsAllQuestions() {
        AskUserRail rail = new AskUserRail();
        ToolCall toolCall = buildToolCall(multiQuestionArgs());

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, null, null);

        assertTrue(decision.isInterrupted());
        Map<String, Object> request = requestMap(decision);
        List<?> questions = (List<?>) request.get("questions");
        assertNotNull(questions);
        assertEquals(2, questions.size());
        assertEquals("Framework", ((Map<?, ?>) questions.get(0)).get("header"));
        assertEquals("Auth", ((Map<?, ?>) questions.get(1)).get("header"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Invalid user input returns interrupt")
    void testInvalidUserInputReturnsInterrupt() {
        AskUserRail rail = new AskUserRail();
        ToolCall toolCall = buildToolCall(singleQuestionArgs());

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, Map.of("invalid_field", "value"), null);

        assertTrue(decision.isInterrupted());
    }

    @Test
    @Tag("level0")
    @DisplayName("Empty questions returns interrupt")
    void testEmptyQuestionsReturnsInterrupt() {
        AskUserRail rail = new AskUserRail();
        ToolCall toolCall = buildToolCall(Map.of("questions", List.of()));

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, null, null);

        assertTrue(decision.isInterrupted());
        Map<String, Object> request = requestMap(decision);
        assertEquals(List.of(), request.get("questions"));
        assertEquals("", request.get("message"));
    }

    @Test
    @Tag("level0")
    @DisplayName("No tool call returns interrupt")
    void testNoToolCallReturnsInterrupt() {
        AskUserRail rail = new AskUserRail();

        InterruptDecision decision = rail.resolveInterrupt(null, null, null, null);

        assertTrue(decision.isInterrupted());
        Map<String, Object> request = requestMap(decision);
        assertEquals(List.of(), request.get("questions"));
        assertEquals("", request.get("message"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Preview field on options is passed through")
    void testPreviewFieldPassedThroughInQuestions() {
        AskUserRail rail = new AskUserRail();
        ToolCall toolCall = buildToolCall(singleQuestionWithPreviewArgs());

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, null, null);

        assertTrue(decision.isInterrupted());
        Map<String, Object> request = requestMap(decision);
        List<?> questions = (List<?>) request.get("questions");
        assertEquals(1, questions.size());
        List<?> options = (List<?>) ((Map<?, ?>) questions.get(0)).get("options");
        assertEquals(2, options.size());
        assertTrue(((Map<?, ?>) options.get(0)).containsKey("preview"));
        assertTrue(((String) ((Map<?, ?>) options.get(0)).get("preview")).startsWith("+"));
        assertTrue(((Map<?, ?>) options.get(1)).containsKey("preview"));
        assertTrue(((String) ((Map<?, ?>) options.get(1)).get("preview")).startsWith("+"));
    }

    @Test
    @Tag("level0")
    @DisplayName("No questions returns simple answer")
    void testNoQuestionsReturnsSimpleAnswer() {
        AskUserRail rail = new AskUserRail();
        ToolCall toolCall = buildToolCall(Map.of("questions", List.of()));
        Map<String, Object> userInput = Map.of("answers", Map.of("", "simple answer"));

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, userInput, null);

        assertTrue(decision.isRejected());
        assertEquals("{'': 'simple answer'}", rejectResult(decision));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestMap(InterruptDecision decision) {
        InterruptResult interruptResult = assertInstanceOf(InterruptResult.class, decision);
        return (Map<String, Object>) interruptResult.getRequest();
    }

    private String rejectResult(InterruptDecision decision) {
        RejectResult rejectResult = assertInstanceOf(RejectResult.class, decision);
        assertTrue(rejectResult.getToolResult().isPresent());
        return rejectResult.getToolResult().get().toString();
    }
}
