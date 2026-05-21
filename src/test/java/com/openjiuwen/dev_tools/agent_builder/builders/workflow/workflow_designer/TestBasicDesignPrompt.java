/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

/**
 * Test basic design prompt constants.
 * <p>
 * Mirrors Python's {@code test_basic_design_prompt.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/workflow_designer/test_basic_design_prompt.py}.
 */
class TestBasicDesignPrompt {

    /**
     * Test BASIC_DESIGN_SYSTEM_PROMPT constant.
     * <p>
     * Mirrors Python's {@code TestBasicDesignSystemPrompt} class.
     */
    static class TestSystemPrompt {

        @Test
        void testIsString() {
            Assertions.assertTrue(BasicDesignPrompt.SYSTEM_PROMPT instanceof String);
            Assertions.assertTrue(BasicDesignPrompt.SYSTEM_PROMPT.length() > 0);
        }

        @Test
        void testContainsRole() {
            Assertions.assertTrue(BasicDesignPrompt.SYSTEM_PROMPT.contains("角色定位"));
        }

        @Test
        void testContainsCoreTask() {
            Assertions.assertTrue(BasicDesignPrompt.SYSTEM_PROMPT.contains("核心任务"));
        }

        @Test
        void testContainsInputAnalysis() {
            Assertions.assertTrue(BasicDesignPrompt.SYSTEM_PROMPT.contains("输入需求分析"));
        }

        @Test
        void testContainsModuleDesign() {
            Assertions.assertTrue(BasicDesignPrompt.SYSTEM_PROMPT.contains("模块设计"));
        }

        @Test
        void testContainsApiUsage() {
            Assertions.assertTrue(BasicDesignPrompt.SYSTEM_PROMPT.contains("API"));
        }

        @Test
        void testContainsOutputFormat() {
            Assertions.assertTrue(BasicDesignPrompt.SYSTEM_PROMPT.contains("输出格式规范"));
        }
    }

    /**
     * Test BASIC_DESIGN_USER_PROMPT_TEMPLATE.
     * <p>
     * Mirrors Python's {@code TestBasicDesignUserPromptTemplate} class.
     */
    static class TestUserPromptTemplate {

        @Test
        void testTemplateExists() {
            Assertions.assertNotNull(BasicDesignPrompt.USER_PROMPT_TEMPLATE);
        }

        @Test
        void testTemplateHasContent() {
            Assertions.assertTrue(BasicDesignPrompt.USER_PROMPT_TEMPLATE.length() > 0);
        }

        @Test
        void testTemplateFormat() {
            String result = BasicDesignPrompt.formatUserPrompt("create workflow", "tool1, tool2");

            Assertions.assertTrue(result.contains("create workflow"));
            Assertions.assertTrue(result.contains("tool1, tool2"));
        }

        @Test
        void testTemplateContainsUserQueryPlaceholder() {
            Assertions.assertTrue(BasicDesignPrompt.USER_PROMPT_TEMPLATE.contains("{{user_query}}"));
        }

        @Test
        void testTemplateContainsToolListPlaceholder() {
            Assertions.assertTrue(BasicDesignPrompt.USER_PROMPT_TEMPLATE.contains("{{tool_list}}"));
        }

        @Test
        void testTemplateFormatWithNulls() {
            String result = BasicDesignPrompt.formatUserPrompt(null, null);

            Assertions.assertNotNull(result);
            // Should still produce valid output with empty placeholders
        }

        @Test
        void testTemplateFormatWithEmptyStrings() {
            String result = BasicDesignPrompt.formatUserPrompt("", "");

            Assertions.assertNotNull(result);
        }
    }
}