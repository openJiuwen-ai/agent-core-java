// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.exception;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 单元测试类：测试 BaseError 异常
 */
class BaseErrorTest {

    @Test
    void testBaseErrorCreation() {
        StatusCode status = StatusCode.ERROR;
        BaseError error = new BaseError(status);

        assertEquals(status, error.getStatus());
        assertEquals(status.code(), error.getCode());
        assertEquals(status.errmsg(), error.getTemplateMessage());
    }

    @Test
    void testBaseErrorWithMessage() {
        StatusCode status = StatusCode.ERROR;
        String customMessage = "Custom error message";
        BaseError error = new BaseError(status, customMessage);

        assertEquals(status, error.getStatus());
        assertEquals(customMessage, error.getMessage());
        assertEquals(status.errmsg(), error.getTemplateMessage());
    }

    @Test
    void testBaseErrorWithDetails() {
        StatusCode status = StatusCode.ERROR;
        Object details = Map.of("key", "value");
        BaseError error = new BaseError(status, details, null);

        assertEquals(status, error.getStatus());
        assertEquals(details, error.getDetails());
    }

    @Test
    void testBaseErrorWithCause() {
        StatusCode status = StatusCode.ERROR;
        Throwable cause = new RuntimeException("Root cause");
        BaseError error = new BaseError(status, null, cause);

        assertEquals(status, error.getStatus());
        assertEquals(cause, error.getCause());
    }

    @Test
    void testBaseErrorWithParams() {
        StatusCode status = StatusCode.WORKFLOW_EXECUTION_TIMEOUT;
        Map<String, Object> params = Map.of("workflow", "test_workflow", "timeout", "30");
        BaseError error = new BaseError(status, null, null, null, params);

        assertEquals(status, error.getStatus());
        assertEquals("test_workflow", error.getParams().get("workflow"));
        assertEquals("30", error.getParams().get("timeout"));
    }

    @Test
    void testToDict() {
        StatusCode status = StatusCode.ERROR;
        String customMessage = "Custom message";
        Object details = Map.of("detail_key", "detail_value");
        BaseError error = new BaseError(status, customMessage, details, null, Map.of("param1", "value1"));

        Map<String, Object> dict = error.toDict();

        assertEquals(status.code(), dict.get("code"));
        assertEquals(status.name(), dict.get("status"));
        assertEquals(status.errmsg(), dict.get("message"));
        assertEquals(customMessage, dict.get("raw_message"));
        assertEquals(Map.of("param1", "value1"), dict.get("params"));
        assertEquals(details, dict.get("details"));
    }

    @Test
    void testToJson() {
        StatusCode status = StatusCode.ERROR;
        BaseError error = new BaseError(status);

        String json = error.toJson();

        assertNotNull(json);
        assertTrue(json.contains("\"code\":"));
        assertTrue(json.contains("\"status\":"));
        assertTrue(json.contains("\"message\":"));
    }

    @Test
    void testToString() {
        StatusCode status = StatusCode.ERROR;
        String customMessage = "Custom error";
        BaseError error = new BaseError(status, customMessage);

        String str = error.toString();

        assertTrue(str.contains(String.valueOf(status.code())));
        assertTrue(str.contains(customMessage));
    }

    @Test
    void testFrameworkErrorProperties() {
        FrameworkError error = new FrameworkError(StatusCode.ERROR);

        assertFalse(error.isRecoverable());
        assertTrue(error.isFatal());
    }

    @Test
    void testValidationErrorProperties() {
        ValidationError error = new ValidationError(StatusCode.ERROR);

        assertFalse(error.isRecoverable());
        assertFalse(error.isFatal());
    }

    @Test
    void testExecutionErrorProperties() {
        ExecutionError error = new ExecutionError(StatusCode.ERROR);

        assertTrue(error.isRecoverable());
        assertFalse(error.isFatal());
    }

    @Test
    void testTerminationProperties() {
        Termination termination = new Termination(StatusCode.SUCCESS);

        assertFalse(termination.isRecoverable());
        assertFalse(termination.isFatal());
    }
}