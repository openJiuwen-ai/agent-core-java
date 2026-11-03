/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's {@code tests.unit_tests.core.common.test_status_code} in
 * {@code tests/unit_tests/core/common/test_status_code.py}.
 */
class StatusCodeMissingTest {

    @Test
    void statusCodeTemplateGeneratesToolAndAgentTemplates() {
        StatusCodeTemplate toolTemplate = StatusCodeTemplate.generateStatusCode(
                "TOOL",
                "INPUT",
                "PARAM_ERROR");

        assertEquals("TOOL_INPUT_PARAM_ERROR", toolTemplate.name());
        assertEquals("182000-182999", toolTemplate.codeSuggestion());
        assertEquals("tool input parameter error, reason: {error_msg}", toolTemplate.messageTemplate());
        assertEquals("ValidationError", toolTemplate.exceptionSemantic());

        StatusCodeTemplate agentTemplate = StatusCodeTemplate.generateStatusCode(
                "AGENT",
                "INVOKE",
                "CALL_FAILED",
                "LLM");

        assertEquals("AGENT_LLM_INVOKE_CALL_FAILED", agentTemplate.name());
        assertEquals("120000-129999", agentTemplate.codeSuggestion());
        assertEquals("agent invoke call failed, reason: {error_msg}", agentTemplate.messageTemplate());
        assertEquals("FrameworkError", agentTemplate.exceptionSemantic());
    }

    @Test
    void statusSpecRendersEnumMembers() {
        StatusCodeTemplate toolTemplate = StatusCodeTemplate.generateStatusCode(
                "TOOL",
                "INPUT",
                "PARAM_ERROR");
        StatusCodeSpec toolSpec = StatusCodeTemplate.generateStatusCodeSpec(toolTemplate, 182010);

        assertEquals("    TOOL_INPUT_PARAM_ERROR = (182010, \"tool input parameter error, reason: {error_msg}\")",
                StatusCodeSpec.renderEnumMember(toolSpec));

        StatusCodeTemplate agentTemplate = StatusCodeTemplate.generateStatusCode(
                "AGENT",
                "INVOKE",
                "CALL_FAILED",
                "LLM");
        StatusCodeSpec agentSpec = StatusCodeTemplate.generateStatusCodeSpec(agentTemplate, 123010);

        assertEquals("    AGENT_LLM_INVOKE_CALL_FAILED = (123010, \"agent invoke call failed, reason: {error_msg}\")",
                StatusCodeSpec.renderEnumMember(agentSpec));

        StatusCodeTemplate workflowTemplate = StatusCodeTemplate.generateStatusCode(
                "WORKFLOW",
                "EXECUTION",
                "TIMEOUT");
        StatusCodeSpec workflowSpec = StatusCodeTemplate.generateStatusCodeSpec(workflowTemplate, 100110);

        assertEquals("    WORKFLOW_EXECUTION_TIMEOUT = (100110, "
                        + "\"workflow execution timeout ({timeout}s), reason: {error_msg}\")",
                StatusCodeSpec.renderEnumMember(workflowSpec));
    }

    @Test
    @Disabled("Skipped in Python source: should not write markdown file")
    void statusDocIsSkippedBecausePythonSkipsMarkdownFileWrite() {
        StatusCode[] codes = StatusCode.values();

        assertEquals(StatusCode.SUCCESS, codes[0]);
    }

    @Test
    void statusMessageGeneratesTemplates() {
        ErrorMessageTemplate groupAddTemplate = ErrorMessageTemplate.generateErrorMessageTemplate(
                "AGENT",
                "GROUP_ADD",
                "RUNTIME_ERROR");

        assertEquals("agent group_add runtime error, reason: {error_msg}", groupAddTemplate.template());
        assertEquals(Set.of("error_msg"), groupAddTemplate.params());

        ErrorMessageTemplate workflowTemplate = ErrorMessageTemplate.generateErrorMessageTemplate(
                "WORKFLOW",
                "EXECUTION",
                "TIMEOUT",
                false);

        assertEquals("workflow execution timeout ({timeout}s)", workflowTemplate.template());
        assertEquals(Set.of("timeout"), workflowTemplate.params());

        ErrorMessageTemplate taskTypeTemplate = ErrorMessageTemplate.generateErrorMessageTemplate(
                "AGENT",
                "TASK_TYPE",
                "NOT_SUPPORTED");

        assertEquals("agent task_type is not supported, reason: {error_msg}", taskTypeTemplate.template());
        assertEquals(Set.of("error_msg"), taskTypeTemplate.params());
    }
}
