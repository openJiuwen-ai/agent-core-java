// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 单元测试类：测试 StatusCode 枚举
 */
class StatusCodeTest {

    @Test
    void testSuccessCode() {
        assertEquals(0, StatusCode.SUCCESS.code());
        assertEquals("success", StatusCode.SUCCESS.errmsg());
        assertEquals("SUCCESS", StatusCode.SUCCESS.name());
    }

    @Test
    void testErrorCode() {
        assertEquals(-1, StatusCode.ERROR.code());
        assertEquals("error", StatusCode.ERROR.errmsg());
        assertEquals("ERROR", StatusCode.ERROR.name());
    }

    @Test
    void testWorkflowValidationCodes() {
        assertEquals(100010, StatusCode.WORKFLOW_COMPONENT_ID_INVALID.code());
        assertTrue(StatusCode.WORKFLOW_COMPONENT_ID_INVALID.errmsg().contains("component id is invalid"));

        assertEquals(100011, StatusCode.WORKFLOW_COMPONENT_ABILITY_INVALID.code());
        assertTrue(StatusCode.WORKFLOW_COMPONENT_ABILITY_INVALID.errmsg().contains("ability is invalid"));

        assertEquals(100012, StatusCode.WORKFLOW_EDGE_INVALID.code());
        assertTrue(StatusCode.WORKFLOW_EDGE_INVALID.errmsg().contains("edge is invalid"));
    }

    @Test
    void testWorkflowExecutionCodes() {
        assertEquals(100100, StatusCode.WORKFLOW_COMPILE_ERROR.code());
        assertTrue(StatusCode.WORKFLOW_COMPILE_ERROR.errmsg().contains("workflow compilation has error"));

        assertEquals(100101, StatusCode.WORKFLOW_EXECUTION_TIMEOUT.code());
        assertTrue(StatusCode.WORKFLOW_EXECUTION_TIMEOUT.errmsg().contains("timeout"));

        assertEquals(100102, StatusCode.WORKFLOW_EXECUTION_ERROR.code());
        assertTrue(StatusCode.WORKFLOW_EXECUTION_ERROR.errmsg().contains("workflow execution has error"));
    }

    @Test
    void testAgentCodes() {
        assertEquals(120000, StatusCode.AGENT_TOOL_NOT_FOUND.code());
        assertTrue(StatusCode.AGENT_TOOL_NOT_FOUND.errmsg().contains("agent tool not found"));

        assertEquals(120001, StatusCode.AGENT_TOOL_EXECUTION_ERROR.code());
        assertTrue(StatusCode.AGENT_TOOL_EXECUTION_ERROR.errmsg().contains("agent tool execution error"));
    }

    @Test
    void testToolCodes() {
        assertEquals(182000, StatusCode.TOOP_CARD_INVALID.code());
        assertTrue(StatusCode.TOOP_CARD_INVALID.errmsg().contains("card is invalid"));

        assertEquals(182010, StatusCode.TOOL_STREAM_NOT_SUPPORTED.code());
        assertTrue(StatusCode.TOOL_STREAM_NOT_SUPPORTED.errmsg().contains("stream is not support"));
    }

    @Test
    void testGuardrailCodes() {
        assertEquals(190000, StatusCode.GUARDRAIL_BLOCKED.code());
        assertTrue(StatusCode.GUARDRAIL_BLOCKED.errmsg().contains("guardrail blocked"));
        assertTrue(StatusCode.GUARDRAIL_BLOCKED.errmsg().contains("risk_type"));
        assertTrue(StatusCode.GUARDRAIL_BLOCKED.errmsg().contains("risk_level"));
        assertTrue(StatusCode.GUARDRAIL_BLOCKED.errmsg().contains("event"));
    }

    @Test
    void testMessageTemplateWithPlaceholders() {
        StatusCode status = StatusCode.WORKFLOW_EXECUTION_TIMEOUT;
        String template = status.errmsg();
        // 验证模板包含占位符
        assertTrue(template.contains("{workflow}"));
        assertTrue(template.contains("{timeout}"));
    }

    @Test
    void testCodeRange() {
        // Workflow 范围: 100000-100999
        assertTrue(StatusCode.WORKFLOW_COMPONENT_ID_INVALID.code() >= 100000);
        assertTrue(StatusCode.WORKFLOW_COMPONENT_ID_INVALID.code() <= 100999);

        // Agent 范围: 120000-129999
        assertTrue(StatusCode.AGENT_TOOL_NOT_FOUND.code() >= 120000);
        assertTrue(StatusCode.AGENT_TOOL_NOT_FOUND.code() <= 129999);

        // Tool 范围: 182000-182999
        assertTrue(StatusCode.TOOP_CARD_INVALID.code() >= 182000);
        assertTrue(StatusCode.TOOP_CARD_INVALID.code() <= 182999);
    }
}