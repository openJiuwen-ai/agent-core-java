/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.runner.callback;

import com.openjiuwen.core.runner.callback.CallbackFramework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Callback framework emit sync test cases.
 *
 * <p>Mirrors Python's {@code test_emit_sync.py} in
 * {@code tests/unit_tests/core/runner/callback/test_emit_sync}.</p>
 */
@DisplayName("Emit Sync Tests")
class TestEmitSync {

    private CallbackFramework framework;

    @BeforeEach
    void setUp() {
        framework = new CallbackFramework();
    }

    @Nested
    @DisplayName("Emit Tests")
    class EmitTests {

        @Test
        @DisplayName("test_emit_sync - synchronous emit returns result")
        void testEmitSync() {
            Function<Map<String, Object>, Object> callback = (ctx) -> "sync_result";
            framework.on("event", callback, "testCallback");

            Map<String, Object> context = new HashMap<>();
            Object result = framework.trigger("event", context);

            assertThat(result).isEqualTo("sync_result");
        }

        @Test
        @DisplayName("test_emit_sync_with_context - emit with context data")
        void testEmitSyncWithContext() {
            Map<String, Object> received = new HashMap<>();

            Function<Map<String, Object>, Object> callback = (ctx) -> {
                received.putAll(ctx);
                return "processed";
            };
            framework.on("event", callback, "testCallback");

            Map<String, Object> context = new HashMap<>();
            context.put("data", "test_data");
            framework.trigger("event", context);

            assertThat(received.get("data")).isEqualTo("test_data");
        }
    }

    @Nested
    @DisplayName("Multiple Callbacks")
    class MultipleCallbacksTests {

        @Test
        @DisplayName("test_emit_sync_multiple_callbacks - emit to multiple callbacks")
        void testEmitSyncMultipleCallbacks() {
            int[] callCount = {0};

            Function<Map<String, Object>, Object> callback1 = (ctx) -> {
                callCount[0]++;
                return "result1";
            };
            Function<Map<String, Object>, Object> callback2 = (ctx) -> {
                callCount[0]++;
                return "result2";
            };

            framework.on("event", callback1, "callback1");
            framework.on("event", callback2, "callback2");

            Map<String, Object> context = new HashMap<>();
            framework.trigger("event", context);

            assertThat(callCount[0]).isEqualTo(2);
        }
    }
}