/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for T01377.
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.agent_builder.builders.workflow.prompts} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/prompts.py}.</p>
 */
class WorkflowPromptsTest {

    @Test
    void initialIntentionPromptKeepsProcessBooleanContract() {
        String prompt = WorkflowPrompts.INITIAL_INTENTION_SYSTEM_PROMPT;

        assertTrue(prompt.startsWith("\n## 角色\n"));
        assertTrue(prompt.contains("\"provide_process\": true"));
        assertTrue(prompt.contains("\"provide_process\": false"));
        assertTrue(prompt.endsWith("请严格按上述要求分析并输出结果。\n"));
    }

    @Test
    void refineIntentionPromptKeepsMermaidBooleanContract() {
        String prompt = WorkflowPrompts.REFINE_INTENTION_SYSTEM_PROMPT;

        assertTrue(prompt.startsWith("\n## 角色\n"));
        assertTrue(prompt.contains("Mermaid代码意图匹配评估专家"));
        assertTrue(prompt.contains("\"need_refined\": false"));
        assertTrue(prompt.endsWith("请严格按上述要求分析并输出结果。\n"));
    }

    @Test
    void emptyResourceContentMatchesPythonLiteral() {
        assertEquals("无可用工具/资源/外部接口。", WorkflowPrompts.EMPTY_RESOURCE_CONTENT);
    }

    @Test
    void checkCyclePromptKeepsLoopSchema() {
        String prompt = WorkflowPrompts.CHECK_CYCLE_SYSTEM_PROMPT;

        assertTrue(prompt.startsWith("\n## 角色设定\n"));
        assertTrue(prompt.contains("有向无环图 (DAG)"));
        assertTrue(prompt.contains("\"loop_desc\": \"\""));
        assertTrue(prompt.endsWith("}\n"));
    }

    @Test
    void initialIntentionUserTemplateWrapsSingleUserMessage() {
        assertUserTemplate(
                WorkflowPrompts.INITIAL_INTENTION_USER_TEMPLATE,
                "{{dialog_history}}"
        );
    }

    @Test
    void refineIntentionUserTemplateWrapsSingleUserMessage() {
        assertUserTemplate(
                WorkflowPrompts.REFINE_INTENTION_USER_TEMPLATE,
                "{{mermaid_code}}"
        );
    }

    @Test
    void checkCycleUserTemplateWrapsSingleUserMessage() {
        assertUserTemplate(
                WorkflowPrompts.CHECK_CYCLE_USER_PROMPT_TEMPLATE,
                "{{mermaid_code}}"
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
