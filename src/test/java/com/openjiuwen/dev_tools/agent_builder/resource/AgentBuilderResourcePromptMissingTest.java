/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.resource;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/dev_tools/agent_builder/resource/test_prompt.py}.
 */
class AgentBuilderResourcePromptMissingTest {

    @Test
    void promptIsString() {
        assertInstanceOf(String.class, AgentBuilderResourcePrompt.RETRIEVE_SYSTEM_PROMPT);
        assertFalse(AgentBuilderResourcePrompt.RETRIEVE_SYSTEM_PROMPT.isEmpty());
    }

    @Test
    void promptContainsKeySections() {
        String prompt = AgentBuilderResourcePrompt.RETRIEVE_SYSTEM_PROMPT;

        assertTrue(prompt.contains("\u4eba\u8bbe"));
        assertTrue(prompt.contains("\u4efb\u52a1\u63cf\u8ff0"));
        assertTrue(prompt.contains("\u8f93\u5165\u4fe1\u606f"));
        assertTrue(prompt.contains("\u9009\u62e9\u89c4\u5219"));
        assertTrue(prompt.contains("\u8f93\u51fa\u683c\u5f0f"));
    }

    @Test
    void promptContainsPlaceholders() {
        String prompt = AgentBuilderResourcePrompt.RETRIEVE_SYSTEM_PROMPT;

        assertTrue(prompt.contains("{{dialog_history}}"));
        assertTrue(prompt.contains("{{plugin_info_list}}"));
    }

    @Test
    void promptContainsJsonFormat() {
        String prompt = AgentBuilderResourcePrompt.RETRIEVE_SYSTEM_PROMPT;

        assertTrue(prompt.contains("tool_id_list"));
        assertTrue(prompt.contains("```json"));
    }

    @Test
    void templateIsPromptTemplate() {
        assertInstanceOf(PromptTemplate.class, AgentBuilderResourcePrompt.RETRIEVE_SYSTEM_TEMPLATE);
    }

    @Test
    void templateFormat() {
        PromptTemplate result = AgentBuilderResourcePrompt.RETRIEVE_SYSTEM_TEMPLATE.format(Map.of(
                "dialog_history", "User: Hello",
                "plugin_info_list", "[{'plugin_name': 'Test'}]"
        ));

        List<BaseMessage> messages = result.toMessages();
        assertFalse(messages.isEmpty());
        assertInstanceOf(SystemMessage.class, messages.get(0));
    }

    @Test
    void templateContainsFormattedContent() {
        PromptTemplate result = AgentBuilderResourcePrompt.RETRIEVE_SYSTEM_TEMPLATE.format(Map.of(
                "dialog_history", "User: Test query",
                "plugin_info_list", "[{'plugin_name': 'Calculator'}]"
        ));

        List<BaseMessage> messages = result.toMessages();
        String content = String.valueOf(messages.get(0).getContent());

        assertTrue(content.contains("User: Test query"));
        assertTrue(content.contains("Calculator"));
    }
}
