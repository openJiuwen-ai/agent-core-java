/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.resource;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for T01375.
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.agent_builder.resource.prompt} in
 * {@code openjiuwen/dev_tools/agent_builder/resource/prompt.py}.</p>
 */
class AgentBuilderResourcePromptTest {

    @Test
    void retrieveSystemPromptKeepsPythonVariablesAndOutputShape() {
        String prompt = AgentBuilderResourcePrompt.RETRIEVE_SYSTEM_PROMPT;

        assertTrue(prompt.startsWith("\n## 人设\n"));
        assertTrue(prompt.contains("{{dialog_history}}"));
        assertTrue(prompt.contains("{{plugin_info_list}}"));
        assertTrue(prompt.contains("\"tool_id_list\": [\"工具ID1\", \"工具ID2\"]"));
        assertTrue(prompt.endsWith("5. 确保生成的ID和原始ID相同\n"));
    }

    @Test
    void retrieveSystemTemplateWrapsSingleSystemMessage() {
        List<BaseMessage> messages = AgentBuilderResourcePrompt.RETRIEVE_SYSTEM_TEMPLATE.toMessages();

        assertEquals(1, messages.size());
        assertInstanceOf(SystemMessage.class, messages.get(0));
        assertEquals("system", messages.get(0).getRole());
        assertEquals(AgentBuilderResourcePrompt.RETRIEVE_SYSTEM_PROMPT, messages.get(0).getContent());
    }
}
