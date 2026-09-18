// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
package com.openjiuwen.core.runner.callback;

import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Focused regression tests for translated callback abort errors.
 *
 * <p>Mirrors Python's {@code errors.py} in
 * {@code openjiuwen/core/runner/callback/errors.py}.</p>
 */
@DisplayName("AbortError Tests")
class AbortErrorTest {

    @Test
    @DisplayName("Default initialization")
    void testDefaultInitialization() {
        AbortError error = new AbortError();

        assertEquals("", error.getReason());
        assertEquals(StatusCode.CALLBACK_EXECUTION_ABORTED, error.getStatus());
        assertEquals("callback execution aborted: ", error.getMessage());
        assertNull(error.getCause());
        assertNull(error.getDetails());
    }

    @Test
    @DisplayName("Reason only initialization")
    void testReasonOnlyInitialization() {
        AbortError error = new AbortError("access denied");

        assertEquals("access denied", error.getReason());
        assertEquals("callback execution aborted: access denied", error.getMessage());
        assertEquals(Map.of("reason", "access denied"), error.getParams());
    }

    @Test
    @DisplayName("Cause and details are preserved")
    void testCauseAndDetailsPreserved() {
        RuntimeException cause = new RuntimeException("inner");
        Map<String, Object> details = Map.of("phase", "validation");

        AbortError error = new AbortError("validation failed", cause, details);

        assertSame(cause, error.getCause());
        assertSame(details, error.getDetails());
        assertEquals("validation failed", error.getReason());
    }

    @Test
    @DisplayName("Framework flags match Python semantics")
    void testFrameworkFlagsMatchPythonSemantics() {
        AbortError error = new AbortError("stop");

        assertFalse(error.isRecoverable());
        assertFalse(error.isFatal());
    }
}
