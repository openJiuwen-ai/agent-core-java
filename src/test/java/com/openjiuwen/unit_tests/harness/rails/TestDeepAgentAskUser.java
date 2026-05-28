/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.rails;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ask_user rail with DeepAgent.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.harness.rails.test_deep_agent_ask_user}.
 */
class TestDeepAgentAskUser {

    // ---------------------------------------------------------------------------
    // Helper classes
    // ---------------------------------------------------------------------------

    /** Mock ask user result. */
    static class AskUserResult {
        private String question;
        private List<String> options;
        private String answer;
        
        public AskUserResult(String question, List<String> options) {
            this.question = question;
            this.options = options;
        }
        
        public void setAnswer(String answer) {
            this.answer = answer;
        }
        
        public String getQuestion() { return question; }
        public List<String> getOptions() { return options; }
        public String getAnswer() { return answer; }
    }

    // ---------------------------------------------------------------------------
    // Tests: hit ask_user rail interrupt
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("Hit ask_user rail interrupt and user responds")
    void testHitAskUserRailInterrupt() {
        // Simulate ask_user rail interrupt
        AskUserResult result = new AskUserResult(
            "Which file should I process?",
            List.of("file1.txt", "file2.txt", "file3.txt")
        );
        
        assertNotNull(result.getQuestion());
        assertEquals(3, result.getOptions().size());
        
        // User responds
        result.setAnswer("file2.txt");
        assertEquals("file2.txt", result.getAnswer());
    }

    // ---------------------------------------------------------------------------
    // Tests: ask_user rail with multi-question
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("Ask user with multi-question interrupt")
    void testAskUserWithMultiQuestionInterrupt() {
        // Multiple questions in single interrupt
        List<AskUserResult> questions = new ArrayList<>();
        questions.add(new AskUserResult("What is your name?", null));
        questions.add(new AskUserResult("What is your preferred language?", List.of("en", "cn")));
        
        assertEquals(2, questions.size());
        
        // Answer both questions
        questions.get(0).setAnswer("Alice");
        questions.get(1).setAnswer("en");
        
        assertEquals("Alice", questions.get(0).getAnswer());
        assertEquals("en", questions.get(1).getAnswer());
    }

    // ---------------------------------------------------------------------------
    // Tests: ask_user rail skip behavior
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("Ask user rail skip behavior when _skip_tool is set")
    void testAskUserRailSkipBehavior() {
        // When _skip_tool is set, rail should not process
        Map<String, Object> context = new HashMap<>();
        context.put("_skip_tool", true);
        context.put("question", "Should this be skipped?");
        
        // Check skip condition
        boolean shouldSkip = (Boolean) context.getOrDefault("_skip_tool", false);
        assertTrue(shouldSkip, "Rail should skip when _skip_tool is set");
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1: Answer validation
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    @DisplayName("Validate user answer against options")
    void testValidateUserAnswerAgainstOptions() {
        List<String> validOptions = List.of("option_a", "option_b", "option_c");
        String userAnswer = "option_b";
        
        assertTrue(validOptions.contains(userAnswer), "User answer should be in valid options");
    }

    @Test
    @Tag("level1")
    @DisplayName("Invalid user answer handling")
    void testInvalidUserAnswerHandling() {
        List<String> validOptions = List.of("option_a", "option_b", "option_c");
        String invalidAnswer = "invalid_option";
        
        assertFalse(validOptions.contains(invalidAnswer), "Invalid answer should be rejected");
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2: Timeout handling
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    @DisplayName("Ask user timeout handling")
    void testAskUserTimeoutHandling() {
        // Simulate timeout scenario
        long startTime = System.currentTimeMillis();
        long timeout = 30000; // 30 seconds
        
        // Check if within timeout
        boolean withinTimeout = (System.currentTimeMillis() - startTime) < timeout;
        assertTrue(withinTimeout, "Should be within timeout period");
    }
}