/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.common;

import java.util.Map;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;

/**
 * Tests for error handling utilities.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.core.common.test_errors}.
 * Tests build_error, raise_error, and error mapping functionality.
 */
class TestErrors {

    // ---------------------------------------------------------------------------
    // Test build_error returns instance - Mirrors Python test_build_error_returns_instance
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testBuildErrorReturnsInstance() {
        // Python: e = build_error(StatusCode.AGENT_TOOL_EXECUTION_ERROR, msg="failed", details={"tool": "xyz"})
        // assert isinstance(e, BaseError)
        // assert e.code == StatusCode.AGENT_TOOL_EXECUTION_ERROR.code
        // assert e.details == {"tool": "xyz"}

        BaseError e = ErrorHelper.buildError(
                StatusCode.WORKFLOW_EXECUTION_ERROR,
                "failed",
                Map.of("tool", "xyz")
        );

        assertNotNull(e);
        assertTrue(e instanceof BaseError);
        assertEquals(StatusCode.WORKFLOW_EXECUTION_ERROR.getCode(), e.getCode());
        assertEquals(Map.of("tool", "xyz"), e.getDetails());
    }

    // ---------------------------------------------------------------------------
    // Test raise_error raises correct type - Mirrors Python test_raise_error_raises_correct_type
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testRaiseErrorRaisesCorrectType() {
        // Python: expected = STATUS_TO_EXCEPTION[StatusCode.AGENT_TOOL_EXECUTION_ERROR]
        // with pytest.raises(expected):
        //     raise_error(StatusCode.AGENT_TOOL_EXECUTION_ERROR, msg="fail")

        assertThrows(BaseError.class, () -> {
            ErrorHelper.raiseError(StatusCode.WORKFLOW_EXECUTION_ERROR, "fail");
        });
    }

    // ---------------------------------------------------------------------------
    // Test build_error with null details - Python test pattern
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testBuildErrorWithNullDetails() {
        BaseError e = ErrorHelper.buildError(
                StatusCode.WORKFLOW_EXECUTION_ERROR,
                "failed",
                null
        );

        assertNotNull(e);
        assertTrue(e instanceof BaseError);
    }

    // ---------------------------------------------------------------------------
    // Test build_error with empty message - Python test pattern
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testBuildErrorWithEmptyMessage() {
        BaseError e = ErrorHelper.buildError(
                StatusCode.WORKFLOW_EXECUTION_ERROR,
                "",
                null
        );

        assertNotNull(e);
        assertTrue(e instanceof BaseError);
    }

    // ---------------------------------------------------------------------------
    // Test StatusCode enum values - Additional validation
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testStatusCodeEnumValues() {
        StatusCode[] codes = StatusCode.values();
        assertTrue(codes.length > 0);

        // Verify key status codes exist
        assertNotNull(StatusCode.SUCCESS);
        assertNotNull(StatusCode.ERROR);
        assertNotNull(StatusCode.WORKFLOW_EXECUTION_ERROR);
    }

    // ---------------------------------------------------------------------------
    // Test StatusCode code values - Mirrors Python pattern
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testStatusCodeCodeValues() {
        assertEquals(0, StatusCode.SUCCESS.getCode());
        assertEquals(-1, StatusCode.ERROR.getCode());
        assertEquals(100102, StatusCode.WORKFLOW_EXECUTION_ERROR.getCode());
    }

    // ---------------------------------------------------------------------------
    // Test BaseError message rendering - Mirrors Python test_format_template_missing_key_safe
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testBaseErrorMessageRendering() {
        BaseError e = ErrorHelper.buildError(
                StatusCode.WORKFLOW_EXECUTION_ERROR,
                null,
                null
        );

        String message = e.getMessage();
        assertNotNull(message);
        assertTrue(message.length() > 0);
    }
}
