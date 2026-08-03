/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

/**
 * Mirrors Python's structured ask_user payload tests in
 * {@code tests/unit_tests/harness/rails/test_ask_user_rail_structured_payload.py}.
 */
class AskUserRailStructuredPayloadPythonParityTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TestFactory
    Collection<DynamicTest> askUserStructuredPayloadPythonParity() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "test_first_call_interrupt_contains_questions_field",
                this::firstCallInterruptContainsQuestionsField);
        add(tests, "test_resume_with_answer_string_returns_formatted_result",
                this::resumeWithAnswerStringReturnsFormattedResult);
        add(tests, "test_resume_with_ask_user_payload_returns_formatted_result",
                this::resumeWithAskUserPayloadReturnsFormattedResult);
        add(tests, "test_resume_with_string_for_multi_question_only_answers_first",
                this::resumeWithStringForMultiQuestionOnlyAnswersFirst);
        add(tests, "test_resume_with_structured_answers_returns_formatted_result",
                this::resumeWithStructuredAnswersReturnsFormattedResult);
        add(tests, "test_resume_with_string_directly_returns_formatted_result",
                this::resumeWithStringDirectlyReturnsFormattedResult);
        add(tests, "test_multi_question_interrupt_contains_all_questions",
                this::multiQuestionInterruptContainsAllQuestions);
        add(tests, "test_invalid_user_input_returns_interrupt",
                this::invalidUserInputReturnsInterrupt);
        add(tests, "test_empty_questions_returns_interrupt",
                this::emptyQuestionsReturnsInterrupt);
        add(tests, "test_no_tool_call_returns_interrupt",
                this::noToolCallReturnsInterrupt);
        add(tests, "test_preview_field_passed_through_in_questions",
                this::previewFieldPassedThroughInQuestions);
        add(tests, "test_no_questions_returns_simple_answer",
                this::noQuestionsReturnsSimpleAnswer);
        return tests;
    }

    private static void add(List<DynamicTest> tests, String pythonName, Executable executable) {
        tests.add(DynamicTest.dynamicTest(pythonName, executable));
    }

    private void firstCallInterruptContainsQuestionsField() {
        InterruptResult decision = assertInstanceOf(InterruptResult.class,
                rail().resolveInterrupt(null, toolCall(singleQuestionArgs()), null));
        AskUserRail.AskUserRequest request = request(decision);

        assertNotNull(request.getQuestions());
        assertEquals(1, request.getQuestions().size());
        assertEquals("Which feature should be enabled?", request.getQuestions().get(0).get("question"));
        assertEquals("Feature", request.getQuestions().get(0).get("header"));
        assertEquals("", request.getMessage());
        assertTrue(((Map<?, ?>) request.getPayloadSchema().get("properties")).containsKey("answers"));
    }

    private void resumeWithAnswerStringReturnsFormattedResult() {
        RejectResult decision = reject(rail().resolveInterrupt(
                null,
                toolCall(singleQuestionArgs()),
                Map.of("answers", Map.of("Which feature should be enabled?", "Dark Mode"))
        ));

        assertContains(decision.toolResult(), "User has answered your questions:");
        assertContains(decision.toolResult(), "\"Which feature should be enabled?\"=\"Dark Mode\"");
    }

    private void resumeWithAskUserPayloadReturnsFormattedResult() {
        RejectResult decision = reject(rail().resolveInterrupt(
                null,
                toolCall(singleQuestionArgs()),
                new AskUserRail.AskUserPayload(Map.of("Which feature should be enabled?", "Auto Save"))
        ));

        assertContains(decision.toolResult(), "User has answered your questions:");
        assertContains(decision.toolResult(), "\"Which feature should be enabled?\"=\"Auto Save\"");
    }

    private void resumeWithStringForMultiQuestionOnlyAnswersFirst() {
        RejectResult decision = reject(rail().resolveInterrupt(null, toolCall(multiQuestionArgs()), "React"));

        assertContains(decision.toolResult(), "User has answered your questions:");
        assertContains(decision.toolResult(), "\"Which framework?\"=\"React\"");
        assertContains(decision.toolResult(), "\"How to authenticate?\"=\"\"");
    }

    private void resumeWithStructuredAnswersReturnsFormattedResult() {
        RejectResult decision = reject(rail().resolveInterrupt(
                null,
                toolCall(multiQuestionArgs()),
                Map.of("answers", Map.of(
                        "Which framework?", "React",
                        "How to authenticate?", "JWT"
                ))
        ));

        assertContains(decision.toolResult(), "\"Which framework?\"=\"React\"");
        assertContains(decision.toolResult(), "\"How to authenticate?\"=\"JWT\"");
    }

    private void resumeWithStringDirectlyReturnsFormattedResult() {
        RejectResult decision = reject(rail().resolveInterrupt(null, toolCall(singleQuestionArgs()), "Dark Mode"));

        assertContains(decision.toolResult(), "User has answered your questions:");
        assertContains(decision.toolResult(), "\"Which feature should be enabled?\"=\"Dark Mode\"");
    }

    private void multiQuestionInterruptContainsAllQuestions() {
        InterruptResult decision = assertInstanceOf(InterruptResult.class,
                rail().resolveInterrupt(null, toolCall(multiQuestionArgs()), null));

        List<Map<String, Object>> questions = request(decision).getQuestions();
        assertEquals(2, questions.size());
        assertEquals("Framework", questions.get(0).get("header"));
        assertEquals("Auth", questions.get(1).get("header"));
    }

    private void invalidUserInputReturnsInterrupt() {
        InterruptDecision decision = rail().resolveInterrupt(
                null,
                toolCall(singleQuestionArgs()),
                Map.of("invalid_field", "value")
        );

        assertInstanceOf(InterruptResult.class, decision);
    }

    private void emptyQuestionsReturnsInterrupt() {
        InterruptResult decision = assertInstanceOf(InterruptResult.class,
                rail().resolveInterrupt(null, toolCall(Map.of("questions", List.of())), null));

        assertEquals(List.of(), request(decision).getQuestions());
        assertEquals("", request(decision).getMessage());
    }

    private void noToolCallReturnsInterrupt() {
        InterruptResult decision = assertInstanceOf(InterruptResult.class,
                rail().resolveInterrupt(null, null, null));

        assertEquals(List.of(), request(decision).getQuestions());
        assertEquals("", request(decision).getMessage());
    }

    @SuppressWarnings("unchecked")
    private void previewFieldPassedThroughInQuestions() {
        InterruptResult decision = assertInstanceOf(InterruptResult.class,
                rail().resolveInterrupt(null, toolCall(singleQuestionWithPreviewArgs()), null));

        List<Map<String, Object>> questions = request(decision).getQuestions();
        assertEquals(1, questions.size());
        List<Map<String, Object>> options = (List<Map<String, Object>>) questions.get(0).get("options");
        assertEquals(2, options.size());
        assertTrue(options.get(0).containsKey("preview"));
        assertTrue(String.valueOf(options.get(0).get("preview")).startsWith("┌"));
        assertTrue(options.get(1).containsKey("preview"));
        assertTrue(String.valueOf(options.get(1).get("preview")).startsWith("┌"));
    }

    private void noQuestionsReturnsSimpleAnswer() {
        RejectResult decision = reject(rail().resolveInterrupt(
                null,
                toolCall(Map.of("questions", List.of())),
                Map.of("answers", Map.of("", "simple answer"))
        ));

        assertEquals("{'': 'simple answer'}", decision.toolResult());
    }

    private static AskUserRail rail() {
        return new AskUserRail();
    }

    private static RejectResult reject(InterruptDecision decision) {
        return assertInstanceOf(RejectResult.class, decision);
    }

    private static AskUserRail.AskUserRequest request(InterruptResult result) {
        return assertInstanceOf(AskUserRail.AskUserRequest.class, result.request());
    }

    private static void assertContains(Object value, String fragment) {
        assertTrue(String.valueOf(value).contains(fragment));
    }

    private static ToolCall toolCall(Map<String, Object> arguments) {
        try {
            return ToolCall.builder()
                    .id("tool_ask_1")
                    .type("function")
                    .name("ask_user")
                    .arguments(OBJECT_MAPPER.writeValueAsString(arguments))
                    .index(0)
                    .build();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Map<String, Object> singleQuestionArgs() {
        return Map.of("questions", List.of(question(
                "Feature",
                "Which feature should be enabled?",
                List.of(
                        option("Dark Mode", "Enable dark theme."),
                        option("Auto Save", "Save changes automatically.")
                )
        )));
    }

    private static Map<String, Object> singleQuestionWithPreviewArgs() {
        return Map.of("questions", List.of(question(
                "Design",
                "Which design do you prefer?",
                List.of(
                        option("Option A", "Simple layout with sidebar.",
                                "┌──────┬──────────┐\n│ nav  │ content  │\n│ bar  │ area     │\n└──────┴──────────┘"),
                        option("Option B", "Full-width layout.",
                                "┌────────────────────┐\n│     content area   │\n└────────────────────┘")
                )
        )));
    }

    private static Map<String, Object> multiQuestionArgs() {
        return Map.of("questions", List.of(
                question(
                        "Framework",
                        "Which framework?",
                        List.of(option("React", "React ecosystem."), option("Vue", "Vue ecosystem."))
                ),
                question(
                        "Auth",
                        "How to authenticate?",
                        List.of(option("JWT", "Token auth."), option("Session", "Session-based auth."))
                )
        ));
    }

    private static Map<String, Object> question(String header, String question, List<Map<String, Object>> options) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("header", header);
        value.put("question", question);
        value.put("options", options);
        value.put("multi_select", false);
        return value;
    }

    private static Map<String, Object> option(String label, String description) {
        return option(label, description, null);
    }

    private static Map<String, Object> option(String label, String description, String preview) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("label", label);
        value.put("description", description);
        if (preview != null) {
            value.put("preview", preview);
        }
        return value;
    }
}
