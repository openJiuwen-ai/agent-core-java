/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.resource;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test resource prompt constants.
 * <p>
 * Mirrors Python's {@code test_prompt.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/resource/test_prompt.py}.
 *
 */
class TestPrompt {

    /**
     * Test RETRIEVE_SYSTEM_PROMPT constant.
     * <p>
     * Mirrors Python's {@code TestRetrieveSystemPrompt} class.
     */
    @Nested
    class TestRetrieveSystemPrompt {

        @Test
        void testPromptIsString() {
            assertFalse(Prompt.RETRIEVE_SYSTEM_PROMPT.isBlank());
        }

        @Test
        void testPromptContainsKeySections() {
            assertTrue(Prompt.RETRIEVE_SYSTEM_PROMPT.contains("人设"));
            assertTrue(Prompt.RETRIEVE_SYSTEM_PROMPT.contains("任务描述"));
            assertTrue(Prompt.RETRIEVE_SYSTEM_PROMPT.contains("输入信息"));
            assertTrue(Prompt.RETRIEVE_SYSTEM_PROMPT.contains("选择规则"));
            assertTrue(Prompt.RETRIEVE_SYSTEM_PROMPT.contains("输出格式"));
        }

        @Test
        void testPromptContainsPlaceholders() {
            assertTrue(Prompt.RETRIEVE_SYSTEM_PROMPT.contains("{{dialog_history}}"));
            assertTrue(Prompt.RETRIEVE_SYSTEM_PROMPT.contains("{{plugin_info_list}}"));
        }

        @Test
        void testPromptContainsJsonFormat() {
            assertTrue(Prompt.RETRIEVE_SYSTEM_PROMPT.contains("tool_id_list"));
            assertTrue(Prompt.RETRIEVE_SYSTEM_PROMPT.contains("```json"));
        }
    }

    /**
     * Test RETRIEVE_SYSTEM_TEMPLATE.
     * <p>
     * Mirrors Python's {@code TestRetrieveSystemTemplate} class.
     */
    @Nested
    class TestRetrieveSystemTemplate {

        @Test
        void testTemplateIsPromptTemplate() {
            assertInstanceOf(PromptTemplate.class, Prompt.RETRIEVE_SYSTEM_TEMPLATE);
        }

        @Test
        void testTemplateFormat() {
            PromptTemplate result = Prompt.RETRIEVE_SYSTEM_TEMPLATE.format(Map.of(
                    "dialog_history", "User: Hello",
                    "plugin_info_list", "[{'plugin_name': 'Test'}]"));

            List<BaseMessage> messages = result.toMessages();
            assertFalse(messages.isEmpty());
            assertInstanceOf(SystemMessage.class, messages.get(0));
        }

        @Test
        void testTemplateContainsFormattedContent() {
            PromptTemplate result = Prompt.RETRIEVE_SYSTEM_TEMPLATE.format(Map.of(
                    "dialog_history", "User: Test query",
                    "plugin_info_list", "[{'plugin_name': 'Calculator'}]"));

            String content = result.toMessages().get(0).getContentAsString();
            assertTrue(content.contains("User: Test query"));
            assertTrue(content.contains("Calculator"));
        }
    }
}
