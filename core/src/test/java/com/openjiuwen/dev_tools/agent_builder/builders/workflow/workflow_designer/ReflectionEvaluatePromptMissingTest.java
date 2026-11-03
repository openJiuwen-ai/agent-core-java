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
 * Supplemental parity tests for reflection evaluate prompt constants.
 *
 * <p>Mirrors Python's
 * {@code tests.unit_tests.dev_tools.agent_builder.builders.workflow.workflow_designer.test_reflection_evaluate_prompt}
 * in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/workflow_designer/test_reflection_evaluate_prompt.py}.
 * </p>
 */
class ReflectionEvaluatePromptMissingTest {

    private static final String SOURCE =
            "tests/unit_tests/dev_tools/agent_builder/builders/workflow/workflow_designer/"
                    + "test_reflection_evaluate_prompt.py";

    @TestFactory
    Collection<DynamicTest> pythonReflectionEvaluatePromptCases() {
        return List.of(
                caseOf("TestReflectionEvaluateSystemPrompt::test_is_string",
                        ReflectionEvaluatePromptMissingTest::systemPromptIsString),
                caseOf("TestReflectionEvaluateSystemPrompt::test_contains_role",
                        ReflectionEvaluatePromptMissingTest::systemPromptContainsRole),
                caseOf("TestReflectionEvaluateSystemPrompt::test_contains_core_task",
                        ReflectionEvaluatePromptMissingTest::systemPromptContainsCoreTask),
                caseOf("TestReflectionEvaluateSystemPrompt::test_contains_evaluation_rules",
                        ReflectionEvaluatePromptMissingTest::systemPromptContainsEvaluationRules),
                caseOf("TestReflectionEvaluateSystemPrompt::test_contains_input_evaluation",
                        ReflectionEvaluatePromptMissingTest::systemPromptContainsInputEvaluation),
                caseOf("TestReflectionEvaluateSystemPrompt::test_contains_module_evaluation",
                        ReflectionEvaluatePromptMissingTest::systemPromptContainsModuleEvaluation),
                caseOf("TestReflectionEvaluateSystemPrompt::test_contains_branch_evaluation",
                        ReflectionEvaluatePromptMissingTest::systemPromptContainsBranchEvaluation),
                caseOf("TestReflectionEvaluateSystemPrompt::test_contains_output_format",
                        ReflectionEvaluatePromptMissingTest::systemPromptContainsOutputFormat),
                caseOf("TestReflectionEvaluateUserPromptTemplate::test_template_exists",
                        ReflectionEvaluatePromptMissingTest::userPromptTemplateExists),
                caseOf("TestReflectionEvaluateUserPromptTemplate::test_template_has_content",
                        ReflectionEvaluatePromptMissingTest::userPromptTemplateHasContent),
                caseOf("TestReflectionEvaluateUserPromptTemplate::test_template_format",
                        ReflectionEvaluatePromptMissingTest::userPromptTemplateFormats),
                caseOf("TestReflectionEvaluateUserPromptTemplate::test_template_contains_user_query",
                        ReflectionEvaluatePromptMissingTest::userPromptTemplateContainsUserQuery),
                caseOf("TestReflectionEvaluateUserPromptTemplate::test_template_contains_basic_design",
                        ReflectionEvaluatePromptMissingTest::userPromptTemplateContainsBasicDesign),
                caseOf("TestReflectionEvaluateUserPromptTemplate::test_template_contains_branch_design",
                        ReflectionEvaluatePromptMissingTest::userPromptTemplateContainsBranchDesign)
        );
    }

    private static DynamicTest caseOf(String pythonNode, Executable executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, executable);
    }

    private static void systemPromptIsString() {
        assertThat(ReflectionEvaluatePrompt.REFLECTION_EVALUATE_SYSTEM_PROMPT)
                .isInstanceOf(String.class)
                .isNotEmpty();
    }

    private static void systemPromptContainsRole() {
        assertThat(ReflectionEvaluatePrompt.REFLECTION_EVALUATE_SYSTEM_PROMPT).contains("角色定位");
    }

    private static void systemPromptContainsCoreTask() {
        assertThat(ReflectionEvaluatePrompt.REFLECTION_EVALUATE_SYSTEM_PROMPT).contains("核心任务");
    }

    private static void systemPromptContainsEvaluationRules() {
        assertThat(ReflectionEvaluatePrompt.REFLECTION_EVALUATE_SYSTEM_PROMPT).contains("评估");
    }

    private static void systemPromptContainsInputEvaluation() {
        assertThat(ReflectionEvaluatePrompt.REFLECTION_EVALUATE_SYSTEM_PROMPT).contains("输入评估");
    }

    private static void systemPromptContainsModuleEvaluation() {
        assertThat(ReflectionEvaluatePrompt.REFLECTION_EVALUATE_SYSTEM_PROMPT).contains("模块");
    }

    private static void systemPromptContainsBranchEvaluation() {
        assertThat(ReflectionEvaluatePrompt.REFLECTION_EVALUATE_SYSTEM_PROMPT).contains("分支");
    }

    private static void systemPromptContainsOutputFormat() {
        assertThat(ReflectionEvaluatePrompt.REFLECTION_EVALUATE_SYSTEM_PROMPT).contains("输出格式");
    }

    private static void userPromptTemplateExists() {
        assertThat(ReflectionEvaluatePrompt.REFLECTION_EVALUATE_USER_PROMPT_TEMPLATE).isNotNull();
    }

    private static void userPromptTemplateHasContent() {
        assertThat(ReflectionEvaluatePrompt.REFLECTION_EVALUATE_USER_PROMPT_TEMPLATE.getContent()).isNotNull();
    }

    private static void userPromptTemplateFormats() {
        List<BaseMessage> messages = ReflectionEvaluatePrompt.REFLECTION_EVALUATE_USER_PROMPT_TEMPLATE
                .format(Map.of(
                        "user_query", "create workflow",
                        "basic_design", "basic design result",
                        "branch_design", "branch design result"))
                .toMessages();

        assertThat(messages).isNotEmpty();
    }

    private static void userPromptTemplateContainsUserQuery() {
        assertFormattedContent(Map.of("user_query", "test query", "basic_design", "", "branch_design", ""))
                .contains("test query");
    }

    private static void userPromptTemplateContainsBasicDesign() {
        assertFormattedContent(Map.of("user_query", "", "basic_design", "test basic", "branch_design", ""))
                .contains("test basic");
    }

    private static void userPromptTemplateContainsBranchDesign() {
        assertFormattedContent(Map.of("user_query", "", "basic_design", "", "branch_design", "test branch"))
                .contains("test branch");
    }

    private static org.assertj.core.api.AbstractStringAssert<?> assertFormattedContent(Map<String, Object> variables) {
        List<BaseMessage> messages = ReflectionEvaluatePrompt.REFLECTION_EVALUATE_USER_PROMPT_TEMPLATE
                .format(variables)
                .toMessages();

        assertThat(messages).isNotEmpty();
        return assertThat(messages.get(0).getContentAsString());
    }
}
