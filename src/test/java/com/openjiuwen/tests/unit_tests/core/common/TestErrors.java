/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.common;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.StatusMapping;
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
        Map<String, Object> details = Map.of("tool", "xyz");
        BaseError e = ErrorHelper.buildError(
            StatusCode.AGENT_TOOL_EXECUTION_ERROR,
            "failed",
            details,
            null,
            Map.of("error_msg", "failed")
        );
        assertNotNull(e);
        assertEquals(StatusCode.AGENT_TOOL_EXECUTION_ERROR.getCode(), e.getCode());
        assertEquals(details, e.getDetails());
    }

    @Test
    @DisplayName("Test raise error throws correct type")
    void testRaiseErrorThrowsCorrectType() {
        BaseError expected = StatusMapping.resolveException(StatusCode.AGENT_TOOL_EXECUTION_ERROR);

        BaseError thrown = assertThrows(BaseError.class,
                () -> ErrorHelper.raiseError(StatusCode.AGENT_TOOL_EXECUTION_ERROR));

        assertInstanceOf(expected.getClass(), thrown);
    }

    @Test
    @DisplayName("Test build error maps to manual override")
    void testBuildErrorMapsToManualOverride() {
        StatusCode key = StatusCode.AGENT_TOOL_NOT_FOUND;
        BaseError expected = StatusMapping.resolveException(key);

        BaseError built = ErrorHelper.buildError(key);

        assertInstanceOf(expected.getClass(), built);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test format template missing key safe")
    void testFormatTemplateMissingKeySafe() {
        BaseError e = ErrorHelper.buildError(StatusCode.WORKFLOW_EXECUTION_ERROR);

        assertNotNull(e.getMessage());
        assertTrue(e.getMessage() instanceof String);
        assertTrue(e.getMessage().contains("<missing:"), "Expected missing key marker in: " + e.getMessage());
    }
}
