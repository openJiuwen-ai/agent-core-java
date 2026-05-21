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
 * Callback framework events scope test cases.
 *
 * <p>Mirrors Python's {@code test_events_scope.py} in
 * {@code tests/unit_tests/core/runner/callback/test_events_scope}.</p>
 */
@DisplayName("Events Scope Tests")
class TestEventsScope {

    private CallbackFramework framework;

    @BeforeEach
    void setUp() {
        framework = new CallbackFramework();
    }

    @Nested
    @DisplayName("Event Registration")
    class EventRegistrationTests {

        @Test
        @DisplayName("test_register_event - registering an event")
        void testRegisterEvent() {
            Function<Map<String, Object>, Object> callback = (ctx) -> "result";
            framework.on("event", callback);

            assertThat(framework.hasCallbacks("event")).isTrue();
        }

        @Test
        @DisplayName("test_register_multiple_events - registering multiple events")
        void testRegisterMultipleEvents() {
            Function<Map<String, Object>, Object> callback = (ctx) -> "result";
            framework.on("event1", callback);
            framework.on("event2", callback);

            assertThat(framework.hasCallbacks("event1")).isTrue();
            assertThat(framework.hasCallbacks("event2")).isTrue();
        }
    }

    @Nested
    @DisplayName("Event Scope")
    class EventScopeTests {

        @Test
        @DisplayName("test_event_scope_isolated - events are isolated from each other")
        void testEventScopeIsolated() {
            Map<String, Object> event1Received = new HashMap<>();
            Map<String, Object> event2Received = new HashMap<>();

            Function<Map<String, Object>, Object> callback1 = (ctx) -> {
                event1Received.putAll(ctx);
                return "result1";
            };
            Function<Map<String, Object>, Object> callback2 = (ctx) -> {
                event2Received.putAll(ctx);
                return "result2";
            };

            framework.on("event1", callback1);
            framework.on("event2", callback2);

            Map<String, Object> context1 = new HashMap<>();
            context1.put("data", "event1_data");
            framework.trigger("event1", context1);

            Map<String, Object> context2 = new HashMap<>();
            context2.put("data", "event2_data");
            framework.trigger("event2", context2);

            assertThat(event1Received.get("data")).isEqualTo("event1_data");
            assertThat(event2Received.get("data")).isEqualTo("event2_data");
        }
    }
}