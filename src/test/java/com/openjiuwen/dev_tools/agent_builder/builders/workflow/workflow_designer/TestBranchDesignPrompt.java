/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test branch design prompt constants.
 * <p>
 * Mirrors Python's {@code test_branch_design_prompt.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/workflow_designer/test_branch_design_prompt.py}.
 *
 */
class TestBranchDesignPrompt {

    /**
     * Test BRANCH_DESIGN_SYSTEM_PROMPT constant.
     * <p>
     * Mirrors Python's {@code TestBranchDesignSystemPrompt} class.
     */
    @Nested
    class TestBranchDesignSystemPrompt {

        @Test
        void testIsString() {
            assertFalse(BranchDesignPrompt.SYSTEM_PROMPT.isBlank());
        }

        @Test
        void testContainsRole() {
            assertTrue(BranchDesignPrompt.SYSTEM_PROMPT.startsWith("#"));
        }

        @Test
        void testContainsCoreTask() {
            assertTrue(BranchDesignPrompt.SYSTEM_PROMPT.contains("##"));
        }

        @Test
        void testContainsBranchDesign() {
            assertTrue(BranchDesignPrompt.SYSTEM_PROMPT.contains("###"));
        }

        @Test
        void testContainsDecisionPrinciples() {
            assertTrue(BranchDesignPrompt.SYSTEM_PROMPT.lines().count() > 10);
        }

        @Test
        void testContainsOutputFormat() {
            assertTrue(BranchDesignPrompt.SYSTEM_PROMPT.contains("["));
        }

        @Test
        void testContainsMustBranch() {
            assertTrue(BranchDesignPrompt.SYSTEM_PROMPT.contains("1."));
        }

        @Test
        void testContainsForbiddenBranch() {
            assertTrue(BranchDesignPrompt.SYSTEM_PROMPT.contains("2."));
        }
    }

    /**
     * Test BRANCH_DESIGN_USER_PROMPT_TEMPLATE.
     * <p>
     * Mirrors Python's {@code TestBranchDesignUserPromptTemplate} class.
     */
    @Nested
    class TestBranchDesignUserPromptTemplate {

        @Test
        void testTemplateExists() {
            assertNotNull(BranchDesignPrompt.USER_PROMPT_TEMPLATE);
        }

        @Test
        void testTemplateHasContent() {
            assertFalse(BranchDesignPrompt.USER_PROMPT_TEMPLATE.isBlank());
        }

        @Test
        void testTemplateFormat() {
            String prompt = BranchDesignPrompt.formatUserPrompt("create workflow", "basic design result");

            assertTrue(prompt.contains("create workflow"));
            assertTrue(prompt.contains("basic design result"));
            assertFalse(prompt.contains("{{user_query}}"));
            assertFalse(prompt.contains("{{basic_design}}"));
        }
    }
}
