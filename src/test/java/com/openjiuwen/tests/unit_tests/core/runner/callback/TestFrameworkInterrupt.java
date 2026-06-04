/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.runner.callback;

import com.openjiuwen.core.runner.callback.AbortError;
import com.openjiuwen.core.runner.callback.CallbackFramework;
import com.openjiuwen.core.runner.callback.CallbackInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Framework interrupt test cases.
 *
 * <p>Mirrors Python's {@code test_framework_interrupt.py} in
 * {@code tests/unit_tests/core/runner/callback/test_framework_interrupt}.</p>
 */
@DisplayName("Framework Interrupt Tests")
class TestFrameworkInterrupt {

    private CallbackFramework framework;

    @BeforeEach
    void setUp() {
        framework = new CallbackFramework(true, false);
    }

    @Test
    @DisplayName("test_abort_error_with_cause_reraises_cause")
    void testAbortErrorWithCauseReraisesCause() {
        IllegalArgumentException original = new IllegalArgumentException("original error");
        framework.on("process", kwargs -> {
            throw new AbortError("validation failed", original);
        }, "callback");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> framework.trigger("process"));

        assertEquals("original error", thrown.getMessage());
    }

    @Test
    @DisplayName("test_abort_error_without_cause_reraises_abort_error")
    void testAbortErrorWithoutCauseReraisesAbortError() {
        framework.on("process", kwargs -> {
            throw new AbortError("access denied");
        }, "callback");

        assertThrows(AbortError.class, () -> framework.trigger("process"));
    }

    @Test
    @DisplayName("test_abort_error_stops_subsequent_callbacks")
    void testAbortErrorStopsSubsequentCallbacks() {
        java.util.List<String> executionOrder = new java.util.ArrayList<>();
        framework.register("process", kwargs -> {
            executionOrder.add("first");
            throw new AbortError("stop here");
        }, 10, "first");
        framework.register("process", kwargs -> {
            executionOrder.add("second");
            return "second";
        }, 5, "second");

        assertThrows(AbortError.class, () -> framework.trigger("process"));

        assertEquals(List.of("first"), executionOrder);
    }

    @Test
    @DisplayName("test_normal_exception_does_not_stop_execution")
    void testNormalExceptionDoesNotStopExecution() {
        java.util.List<String> executionOrder = new java.util.ArrayList<>();
        framework.register("process", kwargs -> {
            executionOrder.add("first");
            throw new RuntimeException("plain error");
        }, 10, "failing");
        framework.register("process", kwargs -> {
            executionOrder.add("second");
            return "ok";
        }, 5, "succeeding");

        List<Object> results = framework.trigger("process");

        assertEquals(List.of("first", "second"), executionOrder);
        assertEquals(List.of("ok"), results);
    }

    @Test
    @DisplayName("test_abort_error_records_error_metric")
    void testAbortErrorRecordsErrorMetric() {
        framework.on("process", kwargs -> {
            throw new AbortError("fail");
        }, "callback");

        assertThrows(AbortError.class, () -> framework.trigger("process"));

        Map<String, Map<String, Object>> metrics = framework.getMetrics();
        assertEquals(1, metrics.get("process:callback").get("error_count"));
        assertEquals(1, metrics.get("process:callback").get("call_count"));
    }

    @Test
    @DisplayName("test_normal_exception_records_error_metric")
    void testNormalExceptionRecordsErrorMetric() {
        framework.on("process", kwargs -> {
            throw new RuntimeException("oops");
        }, "callback");

        framework.trigger("process");

        assertEquals(1, framework.getMetrics().get("process:callback").get("error_count"));
    }

    @Test
    @DisplayName("test_abort_error_triggers_circuit_breaker_failure")
    void testAbortErrorTriggersCircuitBreakerFailure() {
        int[] callCount = {0};
        CallbackInfo callback = framework.on("process", kwargs -> {
            callCount[0]++;
            throw new AbortError("abort");
        }, "callback");
        framework.addCircuitBreaker("process", callback, 1, 60.0);

        assertThrows(AbortError.class, () -> framework.trigger("process"));
        List<Object> results = framework.trigger("process");

        assertTrue(results.isEmpty());
        assertEquals(1, callCount[0]);
    }
}
