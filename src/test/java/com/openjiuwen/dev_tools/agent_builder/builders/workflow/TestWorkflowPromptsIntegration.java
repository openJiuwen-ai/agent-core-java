/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for workflow prompts integration.
 * <p>
 * Mirrors Python's {@code test_prompts_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow}.
 */
class TestWorkflowPromptsIntegration {

    @Nested
    class TestWorkflowPromptsIntegrationInner {

        @Test
        void initialIntentionSystemPromptContent() {
            assertThat(Prompts.INITIAL_INTENTION_SYSTEM_PROMPT).isNotEmpty();
            assertThat(Prompts.INITIAL_INTENTION_SYSTEM_PROMPT).contains("角色");
            assertThat(Prompts.INITIAL_INTENTION_SYSTEM_PROMPT).contains("判断规则");
            assertThat(Prompts.INITIAL_INTENTION_SYSTEM_PROMPT).contains("provide_process");
        }

        @Test
        void initialIntentionUserTemplate() {
            String formatted = Prompts.formatInitialIntentionUserTemplate("用户: 创建一个工作流\n助手: 请描述流程");
            assertThat(formatted).isNotEmpty();
            assertThat(formatted).contains("用户: 创建一个工作流");
        }

        @Test
        void refineIntentionSystemPromptContent() {
            assertThat(Prompts.REFINE_INTENTION_SYSTEM_PROMPT).isNotEmpty();
            assertThat(Prompts.REFINE_INTENTION_SYSTEM_PROMPT).contains("角色");
            assertThat(Prompts.REFINE_INTENTION_SYSTEM_PROMPT).contains("need_refined");
        }

        @Test
        void refineIntentionUserTemplate() {
            String formatted = Prompts.formatRefineIntentionUserTemplate("A --> B", "用户: 修改一下\n助手: 已修改");
            assertThat(formatted).isNotEmpty();
            assertThat(formatted).contains("A --> B");
            assertThat(formatted).contains("用户: 修改一下");
        }

        @Test
        void emptyResourceContent() {
            assertThat(Prompts.EMPTY_RESOURCE_CONTENT).isNotEmpty();
            assertThat(Prompts.EMPTY_RESOURCE_CONTENT).contains("无可用工具");
        }

        @Test
        void checkCycleSystemPromptContent() {
            assertThat(Prompts.CHECK_CYCLE_SYSTEM_PROMPT).isNotEmpty();
            assertThat(Prompts.CHECK_CYCLE_SYSTEM_PROMPT).contains("角色设定");
            assertThat(Prompts.CHECK_CYCLE_SYSTEM_PROMPT).contains("need_refined");
            assertThat(Prompts.CHECK_CYCLE_SYSTEM_PROMPT).contains("loop_desc");
        }

        @Test
        void checkCycleUserPromptTemplate() {
            String formatted = Prompts.formatCheckCycleUserTemplate("A[开始] --> B[处理] --> C[结束]");
            assertThat(formatted).contains("A[开始] --> B[处理] --> C[结束]");
        }
    }

    @Nested
    class TestPromptTemplateFormatting {

        @Test
        void initialIntentionTemplateWithLongHistory() {
            String longHistory = """
                    用户: 消息0
                    助手: 回复0
                    用户: 消息1
                    助手: 回复1
                    用户: 消息2
                    助手: 回复2
                    """;
            assertThat(Prompts.formatInitialIntentionUserTemplate(longHistory)).contains("消息0");
        }

        @Test
        void refineIntentionTemplateWithComplexMermaid() {
            String complexMermaid = """
                    graph TD
                        A[开始] --> B{判断}
                        B -->|是| C[处理1]
                        B -->|否| D[处理2]
                        C --> E[结束]
                        D --> E
                    """;
            String formatted = Prompts.formatRefineIntentionUserTemplate(complexMermaid, "用户: 创建工作流");
            assertThat(formatted).contains("graph TD");
            assertThat(formatted).contains("用户: 创建工作流");
        }

        @Test
        void checkCycleTemplateWithCycle() {
            String formatted = Prompts.formatCheckCycleUserTemplate("A[开始] --> B{判断} --不通过--> A");
            assertThat(formatted).contains("A[开始] --> B{判断} --不通过--> A");
        }
    }

    @Nested
    class TestPromptJsonFormat {

        @Test
        void initialIntentionContainsJsonFormat() {
            assertThat(Prompts.INITIAL_INTENTION_SYSTEM_PROMPT).contains("\"provide_process\": true");
            assertThat(Prompts.INITIAL_INTENTION_SYSTEM_PROMPT).contains("\"provide_process\": false");
        }

        @Test
        void refineIntentionContainsJsonFormat() {
            assertThat(Prompts.REFINE_INTENTION_SYSTEM_PROMPT).contains("\"need_refined\": true");
            assertThat(Prompts.REFINE_INTENTION_SYSTEM_PROMPT).contains("\"need_refined\": false");
        }

        @Test
        void checkCycleContainsJsonFormat() {
            assertThat(Prompts.CHECK_CYCLE_SYSTEM_PROMPT).contains("\"need_refined\": true");
            assertThat(Prompts.CHECK_CYCLE_SYSTEM_PROMPT).contains("\"need_refined\": false");
            assertThat(Prompts.CHECK_CYCLE_SYSTEM_PROMPT).contains("\"loop_desc\"");
        }
    }
}
