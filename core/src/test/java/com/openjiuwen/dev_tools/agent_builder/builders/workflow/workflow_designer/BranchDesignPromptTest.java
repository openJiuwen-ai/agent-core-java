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
 * Focused parity tests for T01379.
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer.branch_design_prompt}
 * in {@code openjiuwen/dev_tools/agent_builder/builders/workflow/workflow_designer/branch_design_prompt.py}.</p>
 */
class BranchDesignPromptTest {

    @Test
    void branchDesignSystemPromptKeepsBranchRules() {
        String prompt = BranchDesignPrompt.BRANCH_DESIGN_SYSTEM_PROMPT;

        assertTrue(prompt.startsWith("\n# 角色定位\n"));
        assertTrue(prompt.contains("分流必须导致**调用不同的功能模块**"));
        assertTrue(prompt.contains("无需设计分支"));
        assertTrue(prompt.endsWith("3. 订单类型判断 → 实物/虚拟/服务 → [物流流程处理模块]/[数字交付模块]/[服务安排模块]\n"));
    }

    @Test
    void branchDesignUserTemplateWrapsSingleUserMessage() {
        List<BaseMessage> messages = BranchDesignPrompt.BRANCH_DESIGN_USER_PROMPT_TEMPLATE.toMessages();

        assertEquals(1, messages.size());
        assertInstanceOf(UserMessage.class, messages.get(0));
        assertEquals("user", messages.get(0).getRole());
        assertTrue(messages.get(0).getContentAsString().startsWith("\n"));
        assertTrue(messages.get(0).getContentAsString().contains("{{user_query}}"));
        assertTrue(messages.get(0).getContentAsString().contains("{{basic_design}}"));
    }
}
