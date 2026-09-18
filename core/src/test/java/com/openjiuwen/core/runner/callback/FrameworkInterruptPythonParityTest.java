/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * <p>Mirrors Python's abort interrupt callback tests in
 * {@code tests/unit_tests/core/runner/callback/test_framework_interrupt.py}.</p>
 */
class FrameworkInterruptPythonParityTest {

    @Test
    void abortErrorWithCauseReraisesCause() {
        AsyncCallbackFramework framework = framework();
        IllegalArgumentException original = new IllegalArgumentException("original error");
        register(framework, "process", "callback", kwargs -> {
            throw new AbortError("validation failed", original);
        });

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> framework.triggerResults("process")
        );

        assertThat(error).isSameAs(original);
    }

    @Test
    void abortErrorWithoutCauseReraisesAbortError() {
        AsyncCallbackFramework framework = framework();
        register(framework, "process", "callback", kwargs -> {
            throw new AbortError("access denied");
        });

        AbortError error = assertThrows(AbortError.class, () -> framework.triggerResults("process"));

        assertThat(error.getReason()).isEqualTo("access denied");
    }

    @Test
    void abortErrorStopsSubsequentCallbacks() {
        AsyncCallbackFramework framework = framework();
        List<String> executionOrder = new ArrayList<>();
        register(framework, "process", "first", 10, kwargs -> {
            executionOrder.add("first");
            throw new AbortError("stop here");
        });
        register(framework, "process", "second", 5, kwargs -> {
            executionOrder.add("second");
            return "second";
        });

        assertThrows(AbortError.class, () -> framework.triggerResults("process"));

        assertThat(executionOrder).containsExactly("first");
    }

    @Test
    void normalExceptionDoesNotStopExecution() {
        AsyncCallbackFramework framework = framework();
        List<String> executionOrder = new ArrayList<>();
        register(framework, "process", "failing", 10, kwargs -> {
            executionOrder.add("first");
            throw new IllegalStateException("plain error");
        });
        register(framework, "process", "succeeding", 5, kwargs -> {
            executionOrder.add("second");
            return "ok";
        });

        List<Object> results = framework.triggerResults("process");

        assertThat(executionOrder).containsExactly("first", "second");
        assertThat(results).containsExactly("ok");
    }

    @Test
    void abortErrorRecordsErrorMetric() {
        AsyncCallbackFramework framework = framework();
        register(framework, "process", "callback", kwargs -> {
            throw new AbortError("fail");
        });

        assertThrows(AbortError.class, () -> framework.triggerResults("process"));

        assertThat(framework.getMetrics().get("process:callback"))
                .containsEntry("error_count", 1)
                .containsEntry("call_count", 1);
    }

    @Test
    void normalExceptionRecordsErrorMetric() {
        AsyncCallbackFramework framework = framework();
        register(framework, "process", "callback", kwargs -> {
            throw new IllegalStateException("oops");
        });

        framework.triggerResults("process");

        assertThat(framework.getMetrics().get("process:callback")).containsEntry("error_count", 1);
    }

    @Test
    void abortErrorTriggersCircuitBreakerFailure() {
        AsyncCallbackFramework framework = framework();
        AtomicInteger callCount = new AtomicInteger();
        Function<Map<String, Object>, Object> callback = named("callback", kwargs -> {
            callCount.incrementAndGet();
            throw new AbortError("abort");
        });
        register(framework, "process", callback);
        framework.addCircuitBreaker("process", callback, 1, 60.0);

        assertThrows(AbortError.class, () -> framework.triggerResults("process"));
        List<Object> results = framework.triggerResults("process");

        assertThat(results).isEmpty();
        assertThat(callCount).hasValue(1);
    }

    private static AsyncCallbackFramework framework() {
        return new AsyncCallbackFramework(true, false);
    }

    private static void register(
            AsyncCallbackFramework framework,
            String event,
            String name,
            Function<Map<String, Object>, Object> callback
    ) {
        register(framework, event, named(name, callback), 0);
    }

    private static void register(
            AsyncCallbackFramework framework,
            String event,
            String name,
            int priority,
            Function<Map<String, Object>, Object> callback
    ) {
        register(framework, event, named(name, callback), priority);
    }

    private static void register(
            AsyncCallbackFramework framework,
            String event,
            Function<Map<String, Object>, Object> callback
    ) {
        register(framework, event, callback, 0);
    }

    private static void register(
            AsyncCallbackFramework framework,
            String event,
            Function<Map<String, Object>, Object> callback,
            int priority
    ) {
        framework.registerSync(event, callback, priority, false, "default", Set.of(), List.of(),
                null, null, 0, 0.0, null, "");
    }

    private static Function<Map<String, Object>, Object> named(
            String name,
            Function<Map<String, Object>, Object> delegate
    ) {
        return new NamedCallback(name, delegate);
    }

    private record NamedCallback(
            String name,
            Function<Map<String, Object>, Object> delegate
    ) implements Function<Map<String, Object>, Object> {

        @Override
        public Object apply(Map<String, Object> kwargs) {
            return delegate.apply(kwargs);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
