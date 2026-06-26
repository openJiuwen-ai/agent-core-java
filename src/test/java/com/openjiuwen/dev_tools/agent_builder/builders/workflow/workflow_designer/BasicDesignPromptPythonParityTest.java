/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestBasicDesignSystemPrompt} and {@code TestBasicDesignUserPromptTemplate} tests in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/workflow_designer/test_basic_design_prompt.py}.
 */
class BasicDesignPromptPythonParityTest {

    @Test
    void systemPromptIsNonEmptyString() {
        assertThat(BasicDesignPrompt.BASIC_DESIGN_SYSTEM_PROMPT).isInstanceOf(String.class);
        assertThat(BasicDesignPrompt.BASIC_DESIGN_SYSTEM_PROMPT).isNotEmpty();
    }

    @Test
    void systemPromptContainsRoleDefinition() {
        assertThat(BasicDesignPrompt.BASIC_DESIGN_SYSTEM_PROMPT).contains("角色定位");
    }

    @Test
    void systemPromptContainsCoreTask() {
        assertThat(BasicDesignPrompt.BASIC_DESIGN_SYSTEM_PROMPT).contains("核心任务");
    }

    @Test
    void systemPromptContainsInputAnalysis() {
        assertThat(BasicDesignPrompt.BASIC_DESIGN_SYSTEM_PROMPT).contains("输入需求分析");
    }

    @Test
    void systemPromptContainsModuleDesign() {
        assertThat(BasicDesignPrompt.BASIC_DESIGN_SYSTEM_PROMPT).contains("模块设计");
    }

    @Test
    void systemPromptContainsApiUsage() {
        assertThat(BasicDesignPrompt.BASIC_DESIGN_SYSTEM_PROMPT).contains("API");
    }

    @Test
    void systemPromptContainsOutputFormat() {
        assertThat(BasicDesignPrompt.BASIC_DESIGN_SYSTEM_PROMPT).contains("输出格式规范");
    }

    @Test
    void userPromptTemplateExists() {
        assertThat(BasicDesignPrompt.BASIC_DESIGN_USER_PROMPT_TEMPLATE).isNotNull();
    }

    @Test
    void userPromptTemplateHasContent() {
        assertThat(BasicDesignPrompt.BASIC_DESIGN_USER_PROMPT_TEMPLATE.getContent()).isNotNull();
    }

    @Test
    void userPromptTemplateCanBeFormattedToMessages() {
        List<BaseMessage> messages = BasicDesignPrompt.BASIC_DESIGN_USER_PROMPT_TEMPLATE
                .format(Map.of("user_query", "create workflow", "tool_list", "tool1, tool2"))
                .toMessages();

        assertThat(messages).isNotEmpty();
    }

    @Test
    void userPromptTemplateContainsFormattedUserQuery() {
        List<BaseMessage> messages = BasicDesignPrompt.BASIC_DESIGN_USER_PROMPT_TEMPLATE
                .format(Map.of("user_query", "test query", "tool_list", ""))
                .toMessages();

        assertThat(messages.getFirst().getContentAsString()).contains("test query");
    }

    @Test
    void userPromptTemplateContainsFormattedToolList() {
        List<BaseMessage> messages = BasicDesignPrompt.BASIC_DESIGN_USER_PROMPT_TEMPLATE
                .format(Map.of("user_query", "", "tool_list", "test tool"))
                .toMessages();

        assertThat(messages.getFirst().getContentAsString()).contains("test tool");
    }
}
