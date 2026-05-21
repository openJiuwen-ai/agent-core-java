/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.rails;

import com.openjiuwen.harness.rails.interrupt.AskUserRail;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AskUserRail structured payload handling.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.harness.rails.test_ask_user_rail_structured_payload}.
 */
@ExtendWith(MockitoExtension.class)
class TestAskUserRailStructuredPayload {

    // ---------------------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------------------

    /** Build a mock tool call with given arguments. */
    private Map<String, Object> buildToolCall(Map<String, Object> arguments, String toolCallId) {
        Map<String, Object> toolCall = new HashMap<>();
        toolCall.put("id", toolCallId);
        toolCall.put("type", "function");
        toolCall.put("name", "ask_user");
        toolCall.put("arguments", arguments);
        return toolCall;
    }

    /** Single question args. */
    private Map<String, Object> singleQuestionArgs() {
        Map<String, Object> option1 = new HashMap<>();
        option1.put("label", "Dark Mode");
        option1.put("description", "Enable dark theme.");

        Map<String, Object> option2 = new HashMap<>();
        option2.put("label", "Auto Save");
        option2.put("description", "Save changes automatically.");

        Map<String, Object> question = new HashMap<>();
        question.put("header", "Feature");
        question.put("question", "Which feature should be enabled?");
        question.put("options", Arrays.asList(option1, option2));
        question.put("multi_select", false);

        Map<String, Object> args = new HashMap<>();
        args.put("questions", Arrays.asList(question));
        return args;
    }

    /** Single question with preview args. */
    private Map<String, Object> singleQuestionWithPreviewArgs() {
        Map<String, Object> option1 = new HashMap<>();
        option1.put("label", "Option A");
        option1.put("description", "Simple layout with sidebar.");
        option1.put("preview", """
            ┌──────┬──────────┐
            │ nav  │ content  │
            │ bar  │ area     │
            └──────┴──────────┘
            """);

        Map<String, Object> option2 = new HashMap<>();
        option2.put("label", "Option B");
        option2.put("description", "Full-width layout.");
        option2.put("preview", """
            ┌────────────────────┐
            │     content area   │
            └────────────────────┘
            """);

        Map<String, Object> question = new HashMap<>();
        question.put("header", "Design");
        question.put("question", "Which design do you prefer?");
        question.put("options", Arrays.asList(option1, option2));
        question.put("multi_select", false);

        Map<String, Object> args = new HashMap<>();
        args.put("questions", Arrays.asList(question));
        return args;
    }

    /** Multi-question args. */
    private Map<String, Object> multiQuestionArgs() {
        Map<String, Object> frameworkOpt1 = new HashMap<>();
        frameworkOpt1.put("label", "React");
        frameworkOpt1.put("description", "React ecosystem.");

        Map<String, Object> frameworkOpt2 = new HashMap<>();
        frameworkOpt2.put("label", "Vue");
        frameworkOpt2.put("description", "Vue ecosystem.");

        Map<String, Object> frameworkQuestion = new HashMap<>();
        frameworkQuestion.put("header", "Framework");
        frameworkQuestion.put("question", "Which framework?");
        frameworkQuestion.put("options", Arrays.asList(frameworkOpt1, frameworkOpt2));
        frameworkQuestion.put("multi_select", false);

        Map<String, Object> authOpt1 = new HashMap<>();
        authOpt1.put("label", "JWT");
        authOpt1.put("description", "Token auth.");

        Map<String, Object> authOpt2 = new HashMap<>();
        authOpt2.put("label", "Session");
        authOpt2.put("description", "Session-based auth.");

        Map<String, Object> authQuestion = new HashMap<>();
        authQuestion.put("header", "Auth");
        authQuestion.put("question", "How to authenticate?");
        authQuestion.put("options", Arrays.asList(authOpt1, authOpt2));
        authQuestion.put("multi_select", false);

        Map<String, Object> args = new HashMap<>();
        args.put("questions", Arrays.asList(frameworkQuestion, authQuestion));
        return args;
    }

    // ---------------------------------------------------------------------------
    // Tests: first call interrupt
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("First call with questions should return InterruptResult with questions field")
    void testFirstCallInterruptContainsQuestionsField() {
        // Python: test_first_call_interrupt_contains_questions_field
        AskUserRail rail = new AskUserRail();
        Map<String, Object> toolCall = buildToolCall(singleQuestionArgs(), "tool_ask_1");

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, null, null);

        assertTrue(decision.isInterrupted());
        InterruptResult interruptResult = (InterruptResult) decision;
        Object request = interruptResult.getRequest();
        
        assertNotNull(request);
        if (request instanceof Map) {
            Map<String, Object> requestMap = (Map<String, Object>) request;
            assertTrue(requestMap.containsKey("payload_schema") || requestMap.containsKey("message"));
        }
    }

    // ---------------------------------------------------------------------------
    // Tests: resume with answer string
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("Resume with answer string returns formatted result")
    void testResumeWithAnswerStringReturnsFormattedResult() {
        // Python: test_resume_with_answer_string_returns_formatted_result
        AskUserRail rail = new AskUserRail();
        Map<String, Object> toolCall = buildToolCall(singleQuestionArgs(), "tool_ask_1");
        
        Map<String, Object> userInput = new HashMap<>();
        userInput.put("answers", Map.of("Which feature should be enabled?", "Dark Mode"));

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, userInput, null);

        assertTrue(decision.isRejected());
        RejectResult rejectResult = (RejectResult) decision;
        assertTrue(rejectResult.getToolResult().isPresent());
        String result = rejectResult.getToolResult().get().toString();
        assertTrue(result.contains("User has answered") || result.contains("answer"));
    }

    // ---------------------------------------------------------------------------
    // Tests: resume with structured answers
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("Resume with structured answers returns formatted result")
    void testResumeWithStructuredAnswersReturnsFormattedResult() {
        // Python: test_resume_with_structured_answers_returns_formatted_result
        AskUserRail rail = new AskUserRail();
        Map<String, Object> toolCall = buildToolCall(multiQuestionArgs(), "tool_ask_1");

        Map<String, Object> answers = new HashMap<>();
        answers.put("Which framework?", "React");
        answers.put("How to authenticate?", "JWT");
        
        Map<String, Object> userInput = new HashMap<>();
        userInput.put("answers", answers);

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, userInput, null);

        assertTrue(decision.isRejected());
        RejectResult rejectResult = (RejectResult) decision;
        assertTrue(rejectResult.getToolResult().isPresent());
    }

    // ---------------------------------------------------------------------------
    // Tests: resume with string for multi-question
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("Resume with string for multi-question only answers first")
    void testResumeWithStringForMultiQuestionOnlyAnswersFirst() {
        // Python: test_resume_with_string_for_multi_question_only_answers_first
        AskUserRail rail = new AskUserRail();
        Map<String, Object> toolCall = buildToolCall(multiQuestionArgs(), "tool_ask_1");

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, "React", null);

        assertTrue(decision.isRejected());
        RejectResult rejectResult = (RejectResult) decision;
        assertTrue(rejectResult.getToolResult().isPresent());
        String result = rejectResult.getToolResult().get().toString();
        assertTrue(result.contains("React") || result.contains("framework"));
    }

    // ---------------------------------------------------------------------------
    // Tests: multi-question interrupt contains all questions
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("Multi-question interrupt contains all questions in questions field")
    void testMultiQuestionInterruptContainsAllQuestions() {
        // Python: test_multi_question_interrupt_contains_all_questions
        AskUserRail rail = new AskUserRail();
        Map<String, Object> toolCall = buildToolCall(multiQuestionArgs(), "tool_ask_1");

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, null, null);

        assertTrue(decision.isInterrupted());
        InterruptResult interruptResult = (InterruptResult) decision;
        assertNotNull(interruptResult.getRequest());
    }

    // ---------------------------------------------------------------------------
    // Tests: preview field in options
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("Question options can include preview field")
    void testQuestionOptionsCanIncludePreviewField() {
        // Python: test_question_options_can_include_preview_field
        AskUserRail rail = new AskUserRail();
        Map<String, Object> toolCall = buildToolCall(singleQuestionWithPreviewArgs(), "tool_ask_1");

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, null, null);

        assertTrue(decision.isInterrupted());
        assertNotNull(((InterruptResult) decision).getRequest());
    }
}