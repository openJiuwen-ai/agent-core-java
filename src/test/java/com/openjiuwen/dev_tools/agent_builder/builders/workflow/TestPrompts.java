/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

/**
 * Test workflow prompts constants.
 * <p>
 * Mirrors Python's {@code test_prompts.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/test_prompts.py}.
 */
class TestPrompts {

    /**
     * Test INITIAL_INTENTION_SYSTEM_PROMPT constant.
     */
    static class TestInitialIntentionSystemPrompt {

        @Test
        void testIsString() {
            Assertions.assertTrue(Prompts.INITIAL_INTENTION_SYSTEM_PROMPT instanceof String);
            Assertions.assertTrue(Prompts.INITIAL_INTENTION_SYSTEM_PROMPT.length() > 0);
        }

        @Test
        void testContainsRole() {
            Assertions.assertTrue(Prompts.INITIAL_INTENTION_SYSTEM_PROMPT.contains("角色"));
        }

        @Test
        void testContainsTrueCondition() {
            Assertions.assertTrue(Prompts.INITIAL_INTENTION_SYSTEM_PROMPT.toLowerCase().contains("true"));
        }

        @Test
        void testContainsFalseCondition() {
            Assertions.assertTrue(Prompts.INITIAL_INTENTION_SYSTEM_PROMPT.toLowerCase().contains("false"));
        }

        @Test
        void testContainsProvideProcess() {
            Assertions.assertTrue(Prompts.INITIAL_INTENTION_SYSTEM_PROMPT.contains("provide_process"));
        }
    }

    /**
     * Test INITIAL_INTENTION_USER_TEMPLATE.
     */
    static class TestInitialIntentionUserTemplate {

        @Test
        void testTemplateExists() {
            Assertions.assertNotNull(Prompts.INITIAL_INTENTION_USER_TEMPLATE);
        }

        @Test
        void testTemplateHasContent() {
            Assertions.assertTrue(Prompts.INITIAL_INTENTION_USER_TEMPLATE.length() > 0);
        }

        @Test
        void testTemplateFormat() {
            String result = Prompts.formatInitialIntentionUserTemplate("test history");

            Assertions.assertTrue(result.contains("test history"));
        }
    }

    /**
     * Test REFINE_INTENTION_SYSTEM_PROMPT constant.
     */
    static class TestRefineIntentionSystemPrompt {

        @Test
        void testIsString() {
            Assertions.assertTrue(Prompts.REFINE_INTENTION_SYSTEM_PROMPT instanceof String);
            Assertions.assertTrue(Prompts.REFINE_INTENTION_SYSTEM_PROMPT.length() > 0);
        }

        @Test
        void testContainsRole() {
            Assertions.assertTrue(Prompts.REFINE_INTENTION_SYSTEM_PROMPT.contains("角色"));
        }

        @Test
        void testContainsNeedRefined() {
            Assertions.assertTrue(Prompts.REFINE_INTENTION_SYSTEM_PROMPT.contains("need_refined"));
        }
    }

    /**
     * Test REFINE_INTENTION_USER_TEMPLATE.
     */
    static class TestRefineIntentionUserTemplate {

        @Test
        void testTemplateExists() {
            Assertions.assertNotNull(Prompts.REFINE_INTENTION_USER_TEMPLATE);
        }

        @Test
        void testTemplateFormat() {
            String result = Prompts.formatRefineIntentionUserTemplate("test mermaid", "test history");

            Assertions.assertTrue(result.contains("test mermaid"));
            Assertions.assertTrue(result.contains("test history"));
        }
    }

    /**
     * Test EMPTY_RESOURCE_CONTENT.
     */
    static class TestEmptyResourceContent {

        @Test
        void testContentExists() {
            Assertions.assertNotNull(Prompts.EMPTY_RESOURCE_CONTENT);
        }

        @Test
        void testContentHasValue() {
            Assertions.assertTrue(Prompts.EMPTY_RESOURCE_CONTENT.length() > 0);
        }
    }
}