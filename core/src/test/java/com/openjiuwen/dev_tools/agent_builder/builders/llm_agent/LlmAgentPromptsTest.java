/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for T01376.
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.agent_builder.builders.llm_agent.prompts} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/llm_agent/prompts.py}.</p>
 */
class LlmAgentPromptsTest {

    @Test
    void factorPromptKeepsTypeRulesAndLeadingNewline() {
        String prompt = LlmAgentPrompts.FACTOR_SYSTEM_PROMPT;

        assertTrue(prompt.startsWith("\n**角色：**"));
        assertTrue(prompt.contains("娱乐交互型"));
        assertTrue(prompt.contains("通用型Agent（默认）"));
        assertTrue(prompt.endsWith("b. 个性化要素数量不超过3个，按重要性降序排列。\n"));
    }

    @Test
    void resourcePromptKeepsStrictEmptyListRules() {
        String prompt = LlmAgentPrompts.RESOURCE_SYSTEM_PROMPT;

        assertTrue(prompt.startsWith("\n**角色：**"));
        assertTrue(prompt.contains("全部为空时，必须完全静默，不输出任何字符"));
        assertTrue(prompt.contains("\"tool_id\": \"data_query\""));
        assertTrue(prompt.endsWith("（知识库和工作流部分被完全省略，不输出任何说明）\n"));
    }

    @Test
    void generatePromptKeepsOutputTags() {
        String prompt = LlmAgentPrompts.GENERATE_SYSTEM_PROMPT;

        assertTrue(prompt.startsWith("\n## 角色\n"));
        assertTrue(prompt.contains("<角色名称>"));
        assertTrue(prompt.contains("<选择的工作流列表>"));
        assertTrue(prompt.endsWith("7. 提示词内容必须有机整合，避免重复或冲突\n"));
    }

    @Test
    void refineIntentionPromptKeepsBooleanJsonContract() {
        String prompt = LlmAgentPrompts.REFINE_INTENTION_SYSTEM_PROMPT;

        assertTrue(prompt.startsWith("\n## 角色\n"));
        assertTrue(prompt.contains("\"need_refined\": true"));
        assertTrue(prompt.contains("\"need_refined\": false"));
        assertTrue(prompt.endsWith("请严格按上述要求分析并输出结果。\n"));
    }

    @Test
    void userPromptTemplateWrapsSingleUserMessage() {
        assertUserTemplate(
                LlmAgentPrompts.USER_PROMPT_TEMPLATE,
                "{{user_messages}}"
        );
    }

    @Test
    void generateUserPromptTemplateWrapsSingleUserMessage() {
        assertUserTemplate(
                LlmAgentPrompts.GENERATE_USER_PROMPT_TEMPLATE,
                "{{agent_resource_info}}"
        );
    }

    @Test
    void resourceUserPromptTemplateWrapsSingleUserMessage() {
        assertUserTemplate(
                LlmAgentPrompts.RESOURCE_USER_PROMPT_TEMPLATE,
                "{{resource}}"
        );
    }

    @Test
    void userIntentionPromptTemplateWrapsSingleUserMessage() {
        assertUserTemplate(
                LlmAgentPrompts.USER_INTENTION_PROMPT_TEMPLATE,
                "{{agent_config_info}}"
        );
    }

    private static void assertUserTemplate(PromptTemplate template, String expectedVariable) {
        List<BaseMessage> messages = template.toMessages();

        assertEquals(1, messages.size());
        assertInstanceOf(UserMessage.class, messages.get(0));
        assertEquals("user", messages.get(0).getRole());
        assertTrue(messages.get(0).getContentAsString().startsWith("\n"));
        assertTrue(messages.get(0).getContentAsString().contains(expectedVariable));
    }
}
