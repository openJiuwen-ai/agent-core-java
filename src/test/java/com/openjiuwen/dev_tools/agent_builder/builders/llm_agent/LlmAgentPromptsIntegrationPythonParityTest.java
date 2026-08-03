/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code TestPromptsIntegration}, {@code TestPromptTemplatesIntegration},
 * and {@code TestPromptTemplateConsistency} in
 * {@code tests/system_tests/dev_tools/agent_builder/builders/llm_agent/test_prompts_integration.py}.
 */
class LlmAgentPromptsIntegrationPythonParityTest {

    @Test
    void testFactorSystemPromptContent() {
        String prompt = LlmAgentPrompts.FACTOR_SYSTEM_PROMPT;

        assertTrue(prompt.contains("角色"));
        assertTrue(prompt.contains("娱乐交互型"));
        assertTrue(prompt.contains("创意生成型"));
        assertTrue(prompt.contains("支持决策型"));
        assertTrue(prompt.contains("执行任务型"));
        assertTrue(prompt.contains("知识服务型"));
        assertTrue(prompt.contains("对话交互型"));
    }

    @Test
    void testResourceSystemPromptContent() {
        String prompt = LlmAgentPrompts.RESOURCE_SYSTEM_PROMPT;

        assertTrue(prompt.contains("插件"));
        assertTrue(prompt.contains("知识库"));
    }

    @Test
    void testGenerateSystemPromptContent() {
        assertFalse(LlmAgentPrompts.GENERATE_SYSTEM_PROMPT.isEmpty());
    }

    @Test
    void testRefineIntentionSystemPromptContent() {
        assertFalse(LlmAgentPrompts.REFINE_INTENTION_SYSTEM_PROMPT.isEmpty());
    }

    @Test
    void testUserPromptTemplateFormat() {
        List<BaseMessage> messages = LlmAgentPrompts.USER_PROMPT_TEMPLATE
                .format(Map.of("user_messages", "test query"))
                .toMessages();

        assertFalse(messages.isEmpty());
        assertTrue(messages.get(0).getContentAsString().contains("test query"));
    }

    @Test
    void testResourceUserPromptTemplateFormat() {
        List<BaseMessage> messages = LlmAgentPrompts.RESOURCE_USER_PROMPT_TEMPLATE
                .format(Map.of("agent_factor_info", "factor info", "resource", "resource info"))
                .toMessages();

        assertFalse(messages.isEmpty());
        assertTrue(messages.get(0).getContentAsString().contains("factor info"));
        assertTrue(messages.get(0).getContentAsString().contains("resource info"));
    }

    @Test
    void testGenerateUserPromptTemplateFormat() {
        List<BaseMessage> messages = LlmAgentPrompts.GENERATE_USER_PROMPT_TEMPLATE
                .format(Map.of(
                        "user_message", "user message",
                        "agent_config_info", "config info",
                        "agent_resource_info", "resource info"))
                .toMessages();

        assertFalse(messages.isEmpty());
        assertTrue(messages.get(0).getContentAsString().contains("user message"));
    }

    @Test
    void testUserIntentionPromptTemplateFormat() {
        List<BaseMessage> messages = LlmAgentPrompts.USER_INTENTION_PROMPT_TEMPLATE
                .format(Map.of("query", "test query", "agent_config_info", "config info"))
                .toMessages();

        assertFalse(messages.isEmpty());
        assertTrue(messages.get(0).getContentAsString().contains("test query"));
    }

    @Test
    void testAllTemplatesExist() {
        assertNotNull(LlmAgentPrompts.USER_PROMPT_TEMPLATE);
        assertNotNull(LlmAgentPrompts.RESOURCE_USER_PROMPT_TEMPLATE);
        assertNotNull(LlmAgentPrompts.GENERATE_USER_PROMPT_TEMPLATE);
        assertNotNull(LlmAgentPrompts.USER_INTENTION_PROMPT_TEMPLATE);
    }

    @Test
    void testAllSystemPromptsExist() {
        assertNotNull(LlmAgentPrompts.FACTOR_SYSTEM_PROMPT);
        assertNotNull(LlmAgentPrompts.RESOURCE_SYSTEM_PROMPT);
        assertNotNull(LlmAgentPrompts.GENERATE_SYSTEM_PROMPT);
        assertNotNull(LlmAgentPrompts.REFINE_INTENTION_SYSTEM_PROMPT);
    }

}
