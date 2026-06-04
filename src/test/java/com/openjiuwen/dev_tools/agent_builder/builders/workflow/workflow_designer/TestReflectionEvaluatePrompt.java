/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test reflection evaluate prompt constants.
 * <p>
 * Mirrors Python's {@code test_reflection_evaluate_prompt.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/workflow_designer/test_reflection_evaluate_prompt.py}.
 *
 */
class TestReflectionEvaluatePrompt {

    /**
     * Test REFLECTION_EVALUATE_SYSTEM_PROMPT constant.
     * <p>
     * Mirrors Python's {@code TestReflectionEvaluateSystemPrompt} class.
     */
    @Nested
    class TestReflectionEvaluateSystemPrompt {

        @Test
        void testIsString() {
            assertFalse(ReflectionEvaluatePrompt.SYSTEM_PROMPT.isBlank());
        }

        @Test
        void testContainsRole() {
            assertTrue(ReflectionEvaluatePrompt.SYSTEM_PROMPT.startsWith("#"));
        }

        @Test
        void testContainsCoreTask() {
            assertTrue(ReflectionEvaluatePrompt.SYSTEM_PROMPT.contains("##"));
        }

        @Test
        void testContainsEvaluationRules() {
            assertTrue(ReflectionEvaluatePrompt.SYSTEM_PROMPT.lines().count() > 10);
        }

        @Test
        void testContainsInputEvaluation() {
            assertTrue(ReflectionEvaluatePrompt.SYSTEM_PROMPT.contains("1."));
        }

        @Test
        void testContainsModuleEvaluation() {
            assertTrue(ReflectionEvaluatePrompt.SYSTEM_PROMPT.contains("2."));
        }

        @Test
        void testContainsBranchEvaluation() {
            assertTrue(ReflectionEvaluatePrompt.SYSTEM_PROMPT.contains("3."));
        }

        @Test
        void testContainsOutputFormat() {
            assertTrue(ReflectionEvaluatePrompt.SYSTEM_PROMPT.contains("["));
        }
    }

    /**
     * Test REFLECTION_EVALUATE_USER_PROMPT_TEMPLATE.
     * <p>
     * Mirrors Python's {@code TestReflectionEvaluateUserPromptTemplate} class.
     */
    @Nested
    class TestReflectionEvaluateUserPromptTemplate {

        @Test
        void testTemplateExists() {
            assertNotNull(ReflectionEvaluatePrompt.USER_PROMPT_TEMPLATE);
        }

        @Test
        void testTemplateHasContent() {
            assertFalse(ReflectionEvaluatePrompt.USER_PROMPT_TEMPLATE.isBlank());
        }

        @Test
        void testTemplateFormat() {
            String prompt = ReflectionEvaluatePrompt.formatUserPrompt(
                    "create workflow", "basic design result", "branch design result");

            assertTrue(prompt.contains("create workflow"));
            assertTrue(prompt.contains("basic design result"));
            assertTrue(prompt.contains("branch design result"));
            assertFalse(prompt.contains("{{user_query}}"));
            assertFalse(prompt.contains("{{basic_design}}"));
            assertFalse(prompt.contains("{{branch_design}}"));
        }

        @Test
        void testTemplateContainsUserQuery() {
            String prompt = ReflectionEvaluatePrompt.formatUserPrompt("test query", "", "");

            assertTrue(prompt.contains("test query"));
        }

        @Test
        void testTemplateContainsBasicDesign() {
            String prompt = ReflectionEvaluatePrompt.formatUserPrompt("", "test basic", "");

            assertTrue(prompt.contains("test basic"));
        }

        @Test
        void testTemplateContainsBranchDesign() {
            String prompt = ReflectionEvaluatePrompt.formatUserPrompt("", "", "test branch");

            assertTrue(prompt.contains("test branch"));
        }
    }
}
