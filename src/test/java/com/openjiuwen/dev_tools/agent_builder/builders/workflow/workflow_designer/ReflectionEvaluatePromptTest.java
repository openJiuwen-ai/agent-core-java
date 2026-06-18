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
 * Focused parity tests for T01380.
 *
 * <p>Mirrors Python's
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer.reflection_evaluate_prompt}
 * in {@code openjiuwen/dev_tools/agent_builder/builders/workflow/workflow_designer/reflection_evaluate_prompt.py}.</p>
 */
class ReflectionEvaluatePromptTest {

    @Test
    void reflectionEvaluateSystemPromptKeepsEvaluationRules() {
        String prompt = ReflectionEvaluatePrompt.REFLECTION_EVALUATE_SYSTEM_PROMPT;

        assertTrue(prompt.startsWith("\n# 角色定位\n"));
        assertTrue(prompt.contains("设计方案完整性评估"));
        assertTrue(prompt.contains("有向无环图 (DAG)"));
        assertTrue(prompt.endsWith("[在此处提供优化后的分支设计方案，保持原始格式。如需修改则提供新内容，否则原文复制。禁止使用\"和原始设计相同\"等说明性语句]\n"));
    }

    @Test
    void reflectionEvaluateUserTemplateWrapsSingleUserMessage() {
        List<BaseMessage> messages = ReflectionEvaluatePrompt.REFLECTION_EVALUATE_USER_PROMPT_TEMPLATE.toMessages();

        assertEquals(1, messages.size());
        assertInstanceOf(UserMessage.class, messages.get(0));
        assertEquals("user", messages.get(0).getRole());
        assertTrue(messages.get(0).getContentAsString().startsWith("\n"));
        assertTrue(messages.get(0).getContentAsString().contains("{{user_query}}"));
        assertTrue(messages.get(0).getContentAsString().contains("{{basic_design}}"));
        assertTrue(messages.get(0).getContentAsString().contains("{{branch_design}}"));
    }
}
