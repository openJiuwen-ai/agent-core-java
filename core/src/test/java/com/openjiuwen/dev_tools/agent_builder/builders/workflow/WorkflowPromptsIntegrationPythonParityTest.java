/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

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
 * Supplemental parity tests for workflow prompt integration behavior.
 *
 * <p>Mirrors Python's {@code tests.system_tests.dev_tools.agent_builder.builders.workflow.test_prompts_integration}
 * in {@code tests/system_tests/dev_tools/agent_builder/builders/workflow/test_prompts_integration.py}.</p>
 */
class WorkflowPromptsIntegrationPythonParityTest {

    private static final String SOURCE =
            "tests/system_tests/dev_tools/agent_builder/builders/workflow/test_prompts_integration.py";

    @TestFactory
    Collection<DynamicTest> pythonWorkflowPromptIntegrationCases() {
        return List.of(
                caseOf("TestWorkflowPromptsIntegration::test_initial_intention_system_prompt_content",
                        WorkflowPromptsIntegrationPythonParityTest::initialIntentionSystemPromptContent),
                caseOf("TestWorkflowPromptsIntegration::test_initial_intention_user_template",
                        WorkflowPromptsIntegrationPythonParityTest::initialIntentionUserTemplate),
                caseOf("TestWorkflowPromptsIntegration::test_refine_intention_system_prompt_content",
                        WorkflowPromptsIntegrationPythonParityTest::refineIntentionSystemPromptContent),
                caseOf("TestWorkflowPromptsIntegration::test_refine_intention_user_template",
                        WorkflowPromptsIntegrationPythonParityTest::refineIntentionUserTemplate),
                caseOf("TestWorkflowPromptsIntegration::test_empty_resource_content",
                        WorkflowPromptsIntegrationPythonParityTest::emptyResourceContent),
                caseOf("TestWorkflowPromptsIntegration::test_check_cycle_system_prompt_content",
                        WorkflowPromptsIntegrationPythonParityTest::checkCycleSystemPromptContent),
                caseOf("TestWorkflowPromptsIntegration::test_check_cycle_user_prompt_template",
                        WorkflowPromptsIntegrationPythonParityTest::checkCycleUserPromptTemplate),
                caseOf("TestPromptTemplateFormatting::test_initial_intention_template_with_long_history",
                        WorkflowPromptsIntegrationPythonParityTest::initialIntentionTemplateWithLongHistory),
                caseOf("TestPromptTemplateFormatting::test_refine_intention_template_with_complex_mermaid",
                        WorkflowPromptsIntegrationPythonParityTest::refineIntentionTemplateWithComplexMermaid),
                caseOf("TestPromptTemplateFormatting::test_check_cycle_template_with_cycle",
                        WorkflowPromptsIntegrationPythonParityTest::checkCycleTemplateWithCycle),
                caseOf("TestPromptJsonFormat::test_initial_intention_contains_json_format",
                        WorkflowPromptsIntegrationPythonParityTest::initialIntentionContainsJsonFormat),
                caseOf("TestPromptJsonFormat::test_refine_intention_contains_json_format",
                        WorkflowPromptsIntegrationPythonParityTest::refineIntentionContainsJsonFormat),
                caseOf("TestPromptJsonFormat::test_check_cycle_contains_json_format",
                        WorkflowPromptsIntegrationPythonParityTest::checkCycleContainsJsonFormat)
        );
    }

    private static DynamicTest caseOf(String pythonNode, Executable executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, executable);
    }

    private static void initialIntentionSystemPromptContent() {
        assertThat(WorkflowPrompts.INITIAL_INTENTION_SYSTEM_PROMPT)
                .isInstanceOf(String.class)
                .isNotEmpty()
                .contains("角色")
                .contains("判断规则")
                .contains("provide_process");
    }

    private static void initialIntentionUserTemplate() {
        List<BaseMessage> messages = WorkflowPrompts.INITIAL_INTENTION_USER_TEMPLATE
                .format(Map.of("dialog_history", "用户: 创建一个工作流\n助手: 请描述流程"))
                .toMessages();

        assertThat(messages).isNotEmpty();
    }

    private static void refineIntentionSystemPromptContent() {
        assertThat(WorkflowPrompts.REFINE_INTENTION_SYSTEM_PROMPT)
                .isInstanceOf(String.class)
                .isNotEmpty()
                .contains("角色")
                .contains("need_refined");
    }

    private static void refineIntentionUserTemplate() {
        List<BaseMessage> messages = WorkflowPrompts.REFINE_INTENTION_USER_TEMPLATE
                .format(Map.of("dialog_history", "用户: 修改一下\n助手: 已修改", "mermaid_code", "A --> B"))
                .toMessages();

        assertThat(messages).isNotEmpty();
    }

    private static void emptyResourceContent() {
        assertThat(WorkflowPrompts.EMPTY_RESOURCE_CONTENT)
                .isInstanceOf(String.class)
                .isNotEmpty()
                .contains("无可用工具");
    }

    private static void checkCycleSystemPromptContent() {
        assertThat(WorkflowPrompts.CHECK_CYCLE_SYSTEM_PROMPT)
                .isInstanceOf(String.class)
                .isNotEmpty()
                .contains("角色设定")
                .contains("need_refined")
                .contains("loop_desc");
    }

    private static void checkCycleUserPromptTemplate() {
        List<BaseMessage> messages = WorkflowPrompts.CHECK_CYCLE_USER_PROMPT_TEMPLATE
                .format(Map.of("mermaid_code", "A[开始] --> B[处理] --> C[结束]"))
                .toMessages();

        assertThat(messages).isNotEmpty();
    }

    private static void initialIntentionTemplateWithLongHistory() {
        StringBuilder history = new StringBuilder();
        for (int index = 0; index < 10; index++) {
            if (!history.isEmpty()) {
                history.append('\n');
            }
            history.append("用户: 消息").append(index).append('\n')
                    .append("助手: 回复").append(index);
        }

        List<BaseMessage> messages = WorkflowPrompts.INITIAL_INTENTION_USER_TEMPLATE
                .format(Map.of("dialog_history", history.toString()))
                .toMessages();

        assertThat(messages).isNotEmpty();
    }

    private static void refineIntentionTemplateWithComplexMermaid() {
        String complexMermaid = """
                graph TD
                    A[开始] --> B{判断}
                    B -->|是| C[处理1]
                    B -->|否| D[处理2]
                    C --> E[结束]
                    D --> E
                """;
        List<BaseMessage> messages = WorkflowPrompts.REFINE_INTENTION_USER_TEMPLATE
                .format(Map.of("dialog_history", "用户: 创建工作流", "mermaid_code", complexMermaid))
                .toMessages();

        assertThat(messages).isNotEmpty();
    }

    private static void checkCycleTemplateWithCycle() {
        List<BaseMessage> messages = WorkflowPrompts.CHECK_CYCLE_USER_PROMPT_TEMPLATE
                .format(Map.of("mermaid_code", "A[开始] --> B{判断} --不通过--> A"))
                .toMessages();

        assertThat(messages).isNotEmpty();
    }

    private static void initialIntentionContainsJsonFormat() {
        assertThat(WorkflowPrompts.INITIAL_INTENTION_SYSTEM_PROMPT)
                .contains("\"provide_process\": true")
                .contains("\"provide_process\": false");
    }

    private static void refineIntentionContainsJsonFormat() {
        assertThat(WorkflowPrompts.REFINE_INTENTION_SYSTEM_PROMPT)
                .contains("\"need_refined\": true")
                .contains("\"need_refined\": false");
    }

    private static void checkCycleContainsJsonFormat() {
        assertThat(WorkflowPrompts.CHECK_CYCLE_SYSTEM_PROMPT)
                .contains("\"need_refined\": true")
                .contains("\"need_refined\": false")
                .contains("\"loop_desc\"");
    }
}
