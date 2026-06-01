/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test LlmAgentBuilder prompts functionality.
 * <p>
 * Mirrors Python's {@code test_prompts.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/llm_agent/test_prompts.py}.
 */
class TestPrompts {

    @Nested
    class TestPromptsConstants {

        @Test
        void testFactorSystemPromptIsString() {
            assertThat(LlmAgentPrompts.FACTOR_SYSTEM_PROMPT).isNotEmpty();
        }

        @Test
        void testFactorSystemPromptContainsRole() {
            assertThat(LlmAgentPrompts.FACTOR_SYSTEM_PROMPT)
                    .satisfies(prompt -> assertThat(prompt.contains("角色")
                            || prompt.toLowerCase().contains("role")).isTrue());
        }

        @Test
        void testFactorSystemPromptContainsAgentTypes() {
            assertThat(LlmAgentPrompts.FACTOR_SYSTEM_PROMPT)
                    .contains("娱乐交互型")
                    .contains("创意生成型")
                    .contains("支持决策型")
                    .contains("执行任务型")
                    .contains("知识服务型")
                    .contains("对话交互型");
        }

        @Test
        void testResourceSystemPromptIsString() {
            assertThat(LlmAgentPrompts.RESOURCE_SYSTEM_PROMPT).isNotEmpty();
        }

        @Test
        void testResourceSystemPromptContainsResourceTypes() {
            assertThat(LlmAgentPrompts.RESOURCE_SYSTEM_PROMPT)
                    .contains("插件")
                    .contains("知识库");
        }

        @Test
        void testGenerateSystemPromptIsString() {
            assertThat(LlmAgentPrompts.GENERATE_SYSTEM_PROMPT).isNotEmpty();
        }

        @Test
        void testRefineIntentionSystemPromptIsString() {
            assertThat(LlmAgentPrompts.REFINE_INTENTION_SYSTEM_PROMPT).isNotEmpty();
        }
    }

    @Nested
    class TestPromptTemplates {

        @Test
        void testUserPromptTemplateHasContent() {
            assertThat(LlmAgentPrompts.formatUserPrompt("test query")).isNotEmpty();
        }

        @Test
        void testResourceUserPromptTemplateHasContent() {
            assertThat(LlmAgentPrompts.formatResourceUserPrompt("test factor", "test resource")).isNotEmpty();
        }

        @Test
        void testGenerateUserPromptTemplateHasContent() {
            assertThat(LlmAgentPrompts.formatGenerateUserPrompt(
                    "test message", "test config", "test resource")).isNotEmpty();
        }

        @Test
        void testUserIntentionPromptTemplateHasContent() {
            assertThat(LlmAgentPrompts.formatUserIntentionPrompt("test query", "test config")).isNotEmpty();
        }

        @Test
        void testUserPromptTemplateFormat() {
            String message = LlmAgentPrompts.formatUserPrompt("test query");

            assertThat(message).contains("test query");
        }

        @Test
        void testResourceUserPromptTemplateFormat() {
            String message = LlmAgentPrompts.formatResourceUserPrompt("test factor", "test resource");

            assertThat(message).contains("test factor");
            assertThat(message).contains("test resource");
        }

        @Test
        void testGenerateUserPromptTemplateFormat() {
            String message = LlmAgentPrompts.formatGenerateUserPrompt(
                    "test message", "test config", "test resource");

            assertThat(message).contains("test message");
        }

        @Test
        void testUserIntentionPromptTemplateFormat() {
            String message = LlmAgentPrompts.formatUserIntentionPrompt("test history", "test config");

            assertThat(message).contains("test history");
        }
    }
}
