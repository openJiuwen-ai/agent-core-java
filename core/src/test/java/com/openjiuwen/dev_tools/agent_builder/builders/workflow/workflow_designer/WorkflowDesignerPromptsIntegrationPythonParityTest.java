/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's prompt integration test groups in
 * {@code tests/system_tests/dev_tools/agent_builder/builders/workflow/workflow_designer/test_prompts_integration.py}.
 */
class WorkflowDesignerPromptsIntegrationPythonParityTest {

    @Nested
    class TestBasicDesignPromptIntegration {

        @Test
        void testSystemPromptContent() {
            assertThat(BasicDesignPrompt.BASIC_DESIGN_SYSTEM_PROMPT)
                    .contains("角色定位")
                    .contains("核心任务")
                    .contains("输入需求分析")
                    .contains("模块设计")
                    .contains("API")
                    .contains("输出格式规范");
        }

        @Test
        void testUserPromptTemplateFormat() {
            List<BaseMessage> messages = BasicDesignPrompt.BASIC_DESIGN_USER_PROMPT_TEMPLATE
                    .format(Map.of("user_query", "create workflow", "tool_list", "tool1, tool2"))
                    .toMessages();

            assertThat(messages).isNotEmpty();
            assertThat(messages.get(0).getContentAsString())
                    .contains("create workflow")
                    .contains("tool1, tool2");
        }
    }

    @Nested
    class TestBranchDesignPromptIntegration {

        @Test
        void testSystemPromptContent() {
            assertThat(BranchDesignPrompt.BRANCH_DESIGN_SYSTEM_PROMPT)
                    .contains("角色定位")
                    .contains("核心任务")
                    .contains("分支设计")
                    .contains("输出格式规范");
        }

        @Test
        void testUserPromptTemplateFormat() {
            List<BaseMessage> messages = BranchDesignPrompt.BRANCH_DESIGN_USER_PROMPT_TEMPLATE
                    .format(Map.of("user_query", "create workflow", "basic_design", "basic design result"))
                    .toMessages();

            assertThat(messages).isNotEmpty();
            assertThat(messages.get(0).getContentAsString())
                    .contains("create workflow")
                    .contains("basic design result");
        }
    }

    @Nested
    class TestReflectionEvaluatePromptIntegration {

        @Test
        void testSystemPromptContent() {
            assertThat(ReflectionEvaluatePrompt.REFLECTION_EVALUATE_SYSTEM_PROMPT)
                    .contains("角色定位")
                    .contains("核心任务")
                    .contains("评估")
                    .contains("输出格式");
        }

        @Test
        void testUserPromptTemplateFormat() {
            List<BaseMessage> messages = ReflectionEvaluatePrompt.REFLECTION_EVALUATE_USER_PROMPT_TEMPLATE
                    .format(Map.of(
                            "user_query", "create workflow",
                            "basic_design", "basic design",
                            "branch_design", "branch design"))
                    .toMessages();

            assertThat(messages).isNotEmpty();
            assertThat(messages.get(0).getContentAsString())
                    .contains("create workflow")
                    .contains("basic design")
                    .contains("branch design");
        }
    }

    @Nested
    class TestPromptTemplateConsistency {

        @Test
        void testAllSystemPromptsExist() {
            assertThat(BasicDesignPrompt.BASIC_DESIGN_SYSTEM_PROMPT).isNotNull();
            assertThat(BranchDesignPrompt.BRANCH_DESIGN_SYSTEM_PROMPT).isNotNull();
            assertThat(ReflectionEvaluatePrompt.REFLECTION_EVALUATE_SYSTEM_PROMPT).isNotNull();
        }

        @Test
        void testAllUserTemplatesExist() {
            assertThat(BasicDesignPrompt.BASIC_DESIGN_USER_PROMPT_TEMPLATE).isNotNull();
            assertThat(BranchDesignPrompt.BRANCH_DESIGN_USER_PROMPT_TEMPLATE).isNotNull();
            assertThat(ReflectionEvaluatePrompt.REFLECTION_EVALUATE_USER_PROMPT_TEMPLATE).isNotNull();
        }
    }
}
