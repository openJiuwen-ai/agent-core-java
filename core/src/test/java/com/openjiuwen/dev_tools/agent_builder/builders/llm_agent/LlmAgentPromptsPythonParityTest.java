/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.dev_tools.agent_builder.builders.llm_agent.test_prompts} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/llm_agent/test_prompts.py}.
 */
class LlmAgentPromptsPythonParityTest {

    @Test
    void factorSystemPromptIsString() {
        assertNotNull(LlmAgentPrompts.FACTOR_SYSTEM_PROMPT);
        assertFalse(LlmAgentPrompts.FACTOR_SYSTEM_PROMPT.isEmpty());
    }

    @Test
    void factorSystemPromptContainsRole() {
        String prompt = LlmAgentPrompts.FACTOR_SYSTEM_PROMPT;

        assertTrue(prompt.contains("角色") || prompt.toLowerCase(Locale.ROOT).contains("role"));
    }

    @Test
    void factorSystemPromptContainsAgentTypes() {
        List<String> agentTypes = List.of("娱乐交互型", "创意生成型", "支持决策型", "执行任务型", "知识服务型", "对话交互型");

        for (String agentType : agentTypes) {
            assertTrue(LlmAgentPrompts.FACTOR_SYSTEM_PROMPT.contains(agentType));
        }
    }

    @Test
    void resourceSystemPromptIsString() {
        assertNotNull(LlmAgentPrompts.RESOURCE_SYSTEM_PROMPT);
        assertFalse(LlmAgentPrompts.RESOURCE_SYSTEM_PROMPT.isEmpty());
    }

    @Test
    void resourceSystemPromptContainsResourceTypes() {
        String prompt = LlmAgentPrompts.RESOURCE_SYSTEM_PROMPT;

        assertTrue(prompt.contains("插件") || prompt.toLowerCase(Locale.ROOT).contains("plugin"));
        assertTrue(prompt.contains("知识库") || prompt.toLowerCase(Locale.ROOT).contains("knowledge"));
    }

    @Test
    void generateSystemPromptIsString() {
        assertNotNull(LlmAgentPrompts.GENERATE_SYSTEM_PROMPT);
        assertFalse(LlmAgentPrompts.GENERATE_SYSTEM_PROMPT.isEmpty());
    }

    @Test
    void refineIntentionSystemPromptIsString() {
        assertNotNull(LlmAgentPrompts.REFINE_INTENTION_SYSTEM_PROMPT);
        assertFalse(LlmAgentPrompts.REFINE_INTENTION_SYSTEM_PROMPT.isEmpty());
    }

    @Test
    void userPromptTemplateHasContent() {
        assertTemplateHasContent(LlmAgentPrompts.USER_PROMPT_TEMPLATE);
    }

    @Test
    void resourceUserPromptTemplateHasContent() {
        assertTemplateHasContent(LlmAgentPrompts.RESOURCE_USER_PROMPT_TEMPLATE);
    }

    @Test
    void generateUserPromptTemplateHasContent() {
        assertTemplateHasContent(LlmAgentPrompts.GENERATE_USER_PROMPT_TEMPLATE);
    }

    @Test
    void userIntentionPromptTemplateHasContent() {
        assertTemplateHasContent(LlmAgentPrompts.USER_INTENTION_PROMPT_TEMPLATE);
    }

    @Test
    void userPromptTemplateFormat() {
        List<BaseMessage> messages = LlmAgentPrompts.USER_PROMPT_TEMPLATE
                .format(Map.of("user_messages", "test query"))
                .toMessages();

        assertFalse(messages.isEmpty());
    }

    @Test
    void resourceUserPromptTemplateFormat() {
        List<BaseMessage> messages = LlmAgentPrompts.RESOURCE_USER_PROMPT_TEMPLATE
                .format(Map.of("agent_factor_info", "test factor", "resource", "test resource"))
                .toMessages();

        assertFalse(messages.isEmpty());
    }

    @Test
    void generateUserPromptTemplateFormat() {
        List<BaseMessage> messages = LlmAgentPrompts.GENERATE_USER_PROMPT_TEMPLATE
                .format(Map.of(
                        "user_message", "test message",
                        "agent_config_info", "test config",
                        "agent_resource_info", "test resource"))
                .toMessages();

        assertFalse(messages.isEmpty());
    }

    @Test
    void userIntentionPromptTemplateFormat() {
        List<BaseMessage> messages = LlmAgentPrompts.USER_INTENTION_PROMPT_TEMPLATE
                .format(Map.of("dialog_history", "test history", "agent_config", "test config"))
                .toMessages();

        assertFalse(messages.isEmpty());
    }

    private static void assertTemplateHasContent(PromptTemplate template) {
        assertNotNull(template);
        assertNotNull(template.getContent());
    }
}
