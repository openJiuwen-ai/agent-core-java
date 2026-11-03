/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental parity tests for workflow prompt constants.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.dev_tools.agent_builder.builders.workflow.test_prompts} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/test_prompts.py}.</p>
 */
class WorkflowPromptsPythonParityTest {

    private static final String SOURCE =
            "tests/unit_tests/dev_tools/agent_builder/builders/workflow/test_prompts.py";

    @TestFactory
    Collection<DynamicTest> pythonWorkflowPromptCases() {
        return List.of(
                caseOf("TestInitialIntentionSystemPrompt::test_is_string",
                        WorkflowPromptsPythonParityTest::initialIntentionSystemPromptIsString),
                caseOf("TestInitialIntentionSystemPrompt::test_contains_role",
                        WorkflowPromptsPythonParityTest::initialIntentionSystemPromptContainsRole),
                caseOf("TestInitialIntentionSystemPrompt::test_contains_true_condition",
                        WorkflowPromptsPythonParityTest::initialIntentionSystemPromptContainsTrue),
                caseOf("TestInitialIntentionSystemPrompt::test_contains_false_condition",
                        WorkflowPromptsPythonParityTest::initialIntentionSystemPromptContainsFalse),
                caseOf("TestInitialIntentionSystemPrompt::test_contains_provide_process",
                        WorkflowPromptsPythonParityTest::initialIntentionSystemPromptContainsProvideProcess),
                caseOf("TestInitialIntentionUserTemplate::test_template_exists",
                        WorkflowPromptsPythonParityTest::initialIntentionUserTemplateExists),
                caseOf("TestInitialIntentionUserTemplate::test_template_has_content",
                        WorkflowPromptsPythonParityTest::initialIntentionUserTemplateHasContent),
                caseOf("TestInitialIntentionUserTemplate::test_template_format",
                        WorkflowPromptsPythonParityTest::initialIntentionUserTemplateFormats),
                caseOf("TestRefineIntentionSystemPrompt::test_is_string",
                        WorkflowPromptsPythonParityTest::refineIntentionSystemPromptIsString),
                caseOf("TestRefineIntentionSystemPrompt::test_contains_role",
                        WorkflowPromptsPythonParityTest::refineIntentionSystemPromptContainsRole),
                caseOf("TestRefineIntentionSystemPrompt::test_contains_need_refined",
                        WorkflowPromptsPythonParityTest::refineIntentionSystemPromptContainsNeedRefined),
                caseOf("TestRefineIntentionUserTemplate::test_template_exists",
                        WorkflowPromptsPythonParityTest::refineIntentionUserTemplateExists),
                caseOf("TestRefineIntentionUserTemplate::test_template_has_content",
                        WorkflowPromptsPythonParityTest::refineIntentionUserTemplateHasContent),
                caseOf("TestRefineIntentionUserTemplate::test_template_format",
                        WorkflowPromptsPythonParityTest::refineIntentionUserTemplateFormats),
                caseOf("TestEmptyResourceContent::test_is_string",
                        WorkflowPromptsPythonParityTest::emptyResourceContentIsString),
                caseOf("TestEmptyResourceContent::test_indicates_empty",
                        WorkflowPromptsPythonParityTest::emptyResourceContentIndicatesEmpty)
        );
    }

    private static DynamicTest caseOf(String pythonNode, Executable executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, executable);
    }

    private static void initialIntentionSystemPromptIsString() {
        assertThat(WorkflowPrompts.INITIAL_INTENTION_SYSTEM_PROMPT)
                .isInstanceOf(String.class)
                .isNotEmpty();
    }

    private static void initialIntentionSystemPromptContainsRole() {
        assertThat(WorkflowPrompts.INITIAL_INTENTION_SYSTEM_PROMPT).contains("角色");
    }

    private static void initialIntentionSystemPromptContainsTrue() {
        assertThat(WorkflowPrompts.INITIAL_INTENTION_SYSTEM_PROMPT.toLowerCase()).contains("true");
    }

    private static void initialIntentionSystemPromptContainsFalse() {
        assertThat(WorkflowPrompts.INITIAL_INTENTION_SYSTEM_PROMPT.toLowerCase()).contains("false");
    }

    private static void initialIntentionSystemPromptContainsProvideProcess() {
        assertThat(WorkflowPrompts.INITIAL_INTENTION_SYSTEM_PROMPT).contains("provide_process");
    }

    private static void initialIntentionUserTemplateExists() {
        assertThat(WorkflowPrompts.INITIAL_INTENTION_USER_TEMPLATE).isNotNull();
    }

    private static void initialIntentionUserTemplateHasContent() {
        assertThat(WorkflowPrompts.INITIAL_INTENTION_USER_TEMPLATE.getContent()).isNotNull();
    }

    private static void initialIntentionUserTemplateFormats() {
        List<BaseMessage> messages = WorkflowPrompts.INITIAL_INTENTION_USER_TEMPLATE
                .format(Map.of("dialog_history", "test history"))
                .toMessages();

        assertThat(messages).isNotEmpty();
        assertThat(messages.get(0).getContentAsString()).contains("test history");
    }

    private static void refineIntentionSystemPromptIsString() {
        assertThat(WorkflowPrompts.REFINE_INTENTION_SYSTEM_PROMPT)
                .isInstanceOf(String.class)
                .isNotEmpty();
    }

    private static void refineIntentionSystemPromptContainsRole() {
        assertThat(WorkflowPrompts.REFINE_INTENTION_SYSTEM_PROMPT).contains("角色");
    }

    private static void refineIntentionSystemPromptContainsNeedRefined() {
        assertThat(WorkflowPrompts.REFINE_INTENTION_SYSTEM_PROMPT).contains("need_refined");
    }

    private static void refineIntentionUserTemplateExists() {
        assertThat(WorkflowPrompts.REFINE_INTENTION_USER_TEMPLATE).isNotNull();
    }

    private static void refineIntentionUserTemplateHasContent() {
        assertThat(WorkflowPrompts.REFINE_INTENTION_USER_TEMPLATE.getContent()).isNotNull();
    }

    private static void refineIntentionUserTemplateFormats() {
        List<BaseMessage> messages = WorkflowPrompts.REFINE_INTENTION_USER_TEMPLATE
                .format(Map.of("mermaid_code", "graph TD", "dialog_history", "test history"))
                .toMessages();

        assertThat(messages).isNotEmpty();
        assertThat(messages.get(0).getContentAsString())
                .contains("graph TD")
                .contains("test history");
    }

    private static void emptyResourceContentIsString() {
        assertThat(WorkflowPrompts.EMPTY_RESOURCE_CONTENT).isInstanceOf(String.class);
    }

    private static void emptyResourceContentIndicatesEmpty() {
        String content = WorkflowPrompts.EMPTY_RESOURCE_CONTENT;

        assertThat(content.contains("无") || content.toLowerCase().contains("empty") || content.isEmpty())
                .isTrue();
    }

    @SuppressWarnings("unused")
    private static void acceptsPromptTemplateType(PromptTemplate template) {
        assertThat(template).isNotNull();
    }
}
