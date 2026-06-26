/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for T01378.
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer.basic_design_prompt}
 * in {@code openjiuwen/dev_tools/agent_builder/builders/workflow/workflow_designer/basic_design_prompt.py}.</p>
 */
class BasicDesignPromptTest {

    @Test
    void basicDesignSystemPromptKeepsDesignRules() {
        String prompt = BasicDesignPrompt.BASIC_DESIGN_SYSTEM_PROMPT;

        assertTrue(prompt.startsWith("\n# 角色定位\n"));
        assertTrue(prompt.contains("禁止规划用户输入解析模块"));
        assertTrue(prompt.contains("严禁因缺少可用API而简化功能设计"));
        assertTrue(prompt.endsWith("2. 使用大模型模拟API功能\n"));
    }

    @Test
    void basicDesignUserTemplateWrapsSingleUserMessage() {
        List<BaseMessage> messages = BasicDesignPrompt.BASIC_DESIGN_USER_PROMPT_TEMPLATE.toMessages();

        assertEquals(1, messages.size());
        assertInstanceOf(UserMessage.class, messages.get(0));
        assertEquals("user", messages.get(0).getRole());
        assertTrue(messages.get(0).getContentAsString().startsWith("\n"));
        assertTrue(messages.get(0).getContentAsString().contains("{{user_query}}"));
        assertTrue(messages.get(0).getContentAsString().contains("{{tool_list}}"));
    }
}
