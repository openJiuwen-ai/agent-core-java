/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for LLM agent prompts integration.
 * <p>
 * Mirrors Python's {@code test_prompts_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.llm_agent}.
 */
class TestPromptsIntegration {

    @Test
    void factorSystemPromptContent() {
        assertThat(LlmAgentPrompts.FACTOR_SYSTEM_PROMPT).contains("角色");
        assertThat(LlmAgentPrompts.FACTOR_SYSTEM_PROMPT).contains("娱乐交互型");
        assertThat(LlmAgentPrompts.FACTOR_SYSTEM_PROMPT).contains("创意生成型");
        assertThat(LlmAgentPrompts.FACTOR_SYSTEM_PROMPT).contains("支持决策型");
        assertThat(LlmAgentPrompts.FACTOR_SYSTEM_PROMPT).contains("执行任务型");
        assertThat(LlmAgentPrompts.FACTOR_SYSTEM_PROMPT).contains("知识服务型");
        assertThat(LlmAgentPrompts.FACTOR_SYSTEM_PROMPT).contains("对话交互型");
    }

    @Test
    void resourceSystemPromptContent() {
        assertThat(LlmAgentPrompts.RESOURCE_SYSTEM_PROMPT).contains("插件");
        assertThat(LlmAgentPrompts.RESOURCE_SYSTEM_PROMPT).contains("知识库");
    }

    @Test
    void generateSystemPromptContent() {
        assertThat(LlmAgentPrompts.GENERATE_SYSTEM_PROMPT).isNotEmpty();
    }

    @Test
    void refineIntentionSystemPromptContent() {
        assertThat(LlmAgentPrompts.REFINE_INTENTION_SYSTEM_PROMPT).isNotEmpty();
    }

    @Test
    void userPromptTemplateFormat() {
        String result = LlmAgentPrompts.formatUserPrompt("test query");
        assertThat(result).contains("test query");
    }

    @Test
    void resourceUserPromptTemplateFormat() {
        String result = LlmAgentPrompts.formatResourceUserPrompt("factor info", "resource info");
        assertThat(result).contains("factor info");
        assertThat(result).contains("resource info");
    }

    @Test
    void generateUserPromptTemplateFormat() {
        String result = LlmAgentPrompts.formatGenerateUserPrompt("user message", "config info", "resource info");
        assertThat(result).contains("user message");
    }

    @Test
    void userIntentionPromptTemplateFormat() {
        String result = LlmAgentPrompts.formatUserIntentionPrompt("test query", "config info");
        assertThat(result).contains("test query");
    }

    @Test
    void allTemplatesExist() {
        assertThat(LlmAgentPrompts.formatUserPrompt("x")).isNotNull();
        assertThat(LlmAgentPrompts.formatResourceUserPrompt("x", "y")).isNotNull();
        assertThat(LlmAgentPrompts.formatGenerateUserPrompt("x", "y", "z")).isNotNull();
        assertThat(LlmAgentPrompts.formatUserIntentionPrompt("x", "y")).isNotNull();
    }

    @Test
    void allSystemPromptsExist() {
        assertThat(LlmAgentPrompts.FACTOR_SYSTEM_PROMPT).isNotNull();
        assertThat(LlmAgentPrompts.RESOURCE_SYSTEM_PROMPT).isNotNull();
        assertThat(LlmAgentPrompts.GENERATE_SYSTEM_PROMPT).isNotNull();
        assertThat(LlmAgentPrompts.REFINE_INTENTION_SYSTEM_PROMPT).isNotNull();
    }
}
