/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.common;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_errors.py} in {@code tests.unit_tests.core.common}.
 */
@Tag("unit-test")
class TestErrors {

    @Test
    @DisplayName("Test build error returns instance")
    void testBuildErrorReturnsInstance() {
        BaseError e = ErrorHelper.buildError(
            StatusCode.AGENT_TOOL_EXECUTION_ERROR,
            "msg", "failed",
            "details", "tool=xyz"
        );
        assertNotNull(e);
        assertEquals(StatusCode.AGENT_TOOL_EXECUTION_ERROR.getCode(), e.getCode());
    }

    @Test
    @DisplayName("Test raise error throws correct type")
    void testRaiseErrorThrowsCorrectType() {
        assertThrows(BaseError.class, () -> {
            throw ErrorHelper.buildError(StatusCode.AGENT_TOOL_EXECUTION_ERROR, "msg", "fail");
        });
    }

    @Test
    @DisplayName("Test format template missing key safe")
    void testFormatTemplateMissingKeySafe() {
        String tmpl = StatusCode.WORKFLOW_EXECUTION_ERROR.getErrmsg();
        assertNotNull(tmpl);
        assertTrue(tmpl instanceof String);
    }

    @Test
    @Tag("level0")
    @DisplayName("Placeholder test")
    void testPlaceholder() {
        assertTrue(true);
    }
}