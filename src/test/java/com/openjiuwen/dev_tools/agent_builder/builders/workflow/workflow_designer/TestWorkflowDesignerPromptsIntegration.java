/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for workflow designer prompts integration.
 * <p>
 * Mirrors Python's {@code test_prompts_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow.workflow_designer}.
 */
class TestWorkflowDesignerPromptsIntegration {

    @Test
    void basicDesignSystemPromptContent() {
        assertThat(BasicDesignPrompt.SYSTEM_PROMPT).contains("角色定位");
        assertThat(BasicDesignPrompt.SYSTEM_PROMPT).contains("核心任务");
        assertThat(BasicDesignPrompt.SYSTEM_PROMPT).contains("输入需求分析");
        assertThat(BasicDesignPrompt.SYSTEM_PROMPT).contains("模块设计");
        assertThat(BasicDesignPrompt.SYSTEM_PROMPT).contains("API");
        assertThat(BasicDesignPrompt.SYSTEM_PROMPT).contains("输出格式规范");
    }

    @Test
    void basicDesignUserPromptTemplateFormat() {
        String messages = BasicDesignPrompt.formatUserPrompt("create workflow", "tool1, tool2");
        assertThat(messages).contains("create workflow");
        assertThat(messages).contains("tool1, tool2");
    }

    @Test
    void branchDesignSystemPromptContent() {
        assertThat(BranchDesignPrompt.SYSTEM_PROMPT).contains("角色定位");
        assertThat(BranchDesignPrompt.SYSTEM_PROMPT).contains("核心任务");
        assertThat(BranchDesignPrompt.SYSTEM_PROMPT).contains("分支设计");
        assertThat(BranchDesignPrompt.SYSTEM_PROMPT).contains("输出格式规范");
    }

    @Test
    void branchDesignUserPromptTemplateFormat() {
        String messages = BranchDesignPrompt.formatUserPrompt("create workflow", "basic design result");
        assertThat(messages).contains("create workflow");
        assertThat(messages).contains("basic design result");
    }

    @Test
    void reflectionEvaluateSystemPromptContent() {
        assertThat(ReflectionEvaluatePrompt.SYSTEM_PROMPT).contains("角色定位");
        assertThat(ReflectionEvaluatePrompt.SYSTEM_PROMPT).contains("核心任务");
        assertThat(ReflectionEvaluatePrompt.SYSTEM_PROMPT).contains("评估");
        assertThat(ReflectionEvaluatePrompt.SYSTEM_PROMPT).contains("输出格式");
    }

    @Test
    void reflectionEvaluateUserPromptTemplateFormat() {
        String messages = ReflectionEvaluatePrompt.formatUserPrompt("create workflow", "basic design", "branch design");
        assertThat(messages).contains("create workflow");
        assertThat(messages).contains("basic design");
        assertThat(messages).contains("branch design");
    }

    @Test
    void allSystemPromptsExist() {
        assertThat(BasicDesignPrompt.SYSTEM_PROMPT).isNotNull();
        assertThat(BranchDesignPrompt.SYSTEM_PROMPT).isNotNull();
        assertThat(ReflectionEvaluatePrompt.SYSTEM_PROMPT).isNotNull();
    }

    @Test
    void allUserTemplatesExist() {
        assertThat(BasicDesignPrompt.USER_PROMPT_TEMPLATE).isNotNull();
        assertThat(BranchDesignPrompt.USER_PROMPT_TEMPLATE).isNotNull();
        assertThat(ReflectionEvaluatePrompt.USER_PROMPT_TEMPLATE).isNotNull();
    }
}
