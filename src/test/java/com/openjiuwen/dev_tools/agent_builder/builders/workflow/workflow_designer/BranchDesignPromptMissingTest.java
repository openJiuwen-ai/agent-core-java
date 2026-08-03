/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * <p>Mirrors Python's {@code TestBranchDesignSystemPrompt} and
 * {@code TestBranchDesignUserPromptTemplate} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/workflow_designer/test_branch_design_prompt.py}.
 * </p>
 */
class BranchDesignPromptMissingTest {

    private static final String SOURCE =
            "tests/unit_tests/dev_tools/agent_builder/builders/workflow/workflow_designer/"
                    + "test_branch_design_prompt.py";

    @TestFactory
    Collection<DynamicTest> pythonBranchDesignPromptCases() {
        return List.of(
                caseOf("TestBranchDesignSystemPrompt::test_is_string",
                        BranchDesignPromptMissingTest::systemPromptIsString),
                caseOf("TestBranchDesignSystemPrompt::test_contains_role",
                        BranchDesignPromptMissingTest::systemPromptContainsRole),
                caseOf("TestBranchDesignSystemPrompt::test_contains_core_task",
                        BranchDesignPromptMissingTest::systemPromptContainsCoreTask),
                caseOf("TestBranchDesignSystemPrompt::test_contains_branch_design",
                        BranchDesignPromptMissingTest::systemPromptContainsBranchDesign),
                caseOf("TestBranchDesignSystemPrompt::test_contains_decision_principles",
                        BranchDesignPromptMissingTest::systemPromptContainsDecisionPrinciples),
                caseOf("TestBranchDesignSystemPrompt::test_contains_output_format",
                        BranchDesignPromptMissingTest::systemPromptContainsOutputFormat),
                caseOf("TestBranchDesignSystemPrompt::test_contains_must_branch",
                        BranchDesignPromptMissingTest::systemPromptContainsMustBranch),
                caseOf("TestBranchDesignSystemPrompt::test_contains_forbidden_branch",
                        BranchDesignPromptMissingTest::systemPromptContainsForbiddenBranch),
                caseOf("TestBranchDesignUserPromptTemplate::test_template_exists",
                        BranchDesignPromptMissingTest::userPromptTemplateExists),
                caseOf("TestBranchDesignUserPromptTemplate::test_template_has_content",
                        BranchDesignPromptMissingTest::userPromptTemplateHasContent),
                caseOf("TestBranchDesignUserPromptTemplate::test_template_format",
                        BranchDesignPromptMissingTest::userPromptTemplateFormats),
                caseOf("TestBranchDesignUserPromptTemplate::test_template_contains_user_query",
                        BranchDesignPromptMissingTest::userPromptTemplateContainsUserQuery),
                caseOf("TestBranchDesignUserPromptTemplate::test_template_contains_basic_design",
                        BranchDesignPromptMissingTest::userPromptTemplateContainsBasicDesign)
        );
    }

    private static DynamicTest caseOf(String pythonNode, Executable executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, executable);
    }

    private static void systemPromptIsString() {
        assertThat(BranchDesignPrompt.BRANCH_DESIGN_SYSTEM_PROMPT)
                .isInstanceOf(String.class)
                .isNotEmpty();
    }

    private static void systemPromptContainsRole() {
        assertThat(BranchDesignPrompt.BRANCH_DESIGN_SYSTEM_PROMPT).contains("角色定位");
    }

    private static void systemPromptContainsCoreTask() {
        assertThat(BranchDesignPrompt.BRANCH_DESIGN_SYSTEM_PROMPT).contains("核心任务");
    }

    private static void systemPromptContainsBranchDesign() {
        assertThat(BranchDesignPrompt.BRANCH_DESIGN_SYSTEM_PROMPT).contains("分支设计");
    }

    private static void systemPromptContainsDecisionPrinciples() {
        assertThat(BranchDesignPrompt.BRANCH_DESIGN_SYSTEM_PROMPT).containsAnyOf("分流决策原则", "决策原则");
    }

    private static void systemPromptContainsOutputFormat() {
        assertThat(BranchDesignPrompt.BRANCH_DESIGN_SYSTEM_PROMPT).contains("输出格式规范");
    }

    private static void systemPromptContainsMustBranch() {
        assertThat(BranchDesignPrompt.BRANCH_DESIGN_SYSTEM_PROMPT).contains("必须设计分支");
    }

    private static void systemPromptContainsForbiddenBranch() {
        assertThat(BranchDesignPrompt.BRANCH_DESIGN_SYSTEM_PROMPT).contains("禁止设计分支");
    }

    private static void userPromptTemplateExists() {
        assertThat(BranchDesignPrompt.BRANCH_DESIGN_USER_PROMPT_TEMPLATE).isNotNull();
    }

    private static void userPromptTemplateHasContent() {
        assertThat(BranchDesignPrompt.BRANCH_DESIGN_USER_PROMPT_TEMPLATE.getContent()).isNotNull();
    }

    private static void userPromptTemplateFormats() {
        List<BaseMessage> messages = BranchDesignPrompt.BRANCH_DESIGN_USER_PROMPT_TEMPLATE
                .format(Map.of("user_query", "create workflow", "basic_design", "basic design result"))
                .toMessages();

        assertThat(messages).isNotEmpty();
    }

    private static void userPromptTemplateContainsUserQuery() {
        assertFormattedContent(Map.of("user_query", "test query", "basic_design", ""))
                .contains("test query");
    }

    private static void userPromptTemplateContainsBasicDesign() {
        assertFormattedContent(Map.of("user_query", "", "basic_design", "test design"))
                .contains("test design");
    }

    private static org.assertj.core.api.AbstractStringAssert<?> assertFormattedContent(Map<String, Object> variables) {
        List<BaseMessage> messages = BranchDesignPrompt.BRANCH_DESIGN_USER_PROMPT_TEMPLATE
                .format(variables)
                .toMessages();

        assertThat(messages).isNotEmpty();
        return assertThat(messages.get(0).getContentAsString());
    }
}
