/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.runner.callback;

import com.openjiuwen.core.runner.callback.CallbackFramework;
import com.openjiuwen.core.runner.callback.FilterAction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Callback framework filters test cases.
 *
 * <p>Mirrors Python's {@code test_filters.py} in
 * {@code tests/unit_tests/core/runner/callback/test_filters}.</p>
 */
@DisplayName("Callback Filters Tests")
class TestFilters {

    private CallbackFramework framework;

    @BeforeEach
    void setUp() {
        framework = new CallbackFramework();
    }

    @Nested
    @DisplayName("Filter Registration")
    class FilterRegistrationTests {

        @Test
        @DisplayName("test_register_filter - registering a filter")
        void testRegisterFilter() {
            Function<Map<String, Object>, FilterAction> filter = (ctx) -> FilterAction.CONTINUE;
            framework.registerFilter("event", filter);

            assertThat(framework.hasFilters("event")).isTrue();
        }

        @Test
        @DisplayName("test_register_multiple_filters - registering multiple filters")
        void testRegisterMultipleFilters() {
            Function<Map<String, Object>, FilterAction> filter1 = (ctx) -> FilterAction.CONTINUE;
            Function<Map<String, Object>, FilterAction> filter2 = (ctx) -> FilterAction.SKIP;

            framework.registerFilter("event", filter1);
            framework.registerFilter("event", filter2);

            assertThat(framework.hasFilters("event")).isTrue();
        }
    }

    @Nested
    @DisplayName("Filter Actions")
    class FilterActionTests {

        @Test
        @DisplayName("test_filter_continue - CONTINUE allows callback execution")
        void testFilterContinue() {
            Function<Map<String, Object>, Object> callback = (ctx) -> "result";
            framework.on("event", callback);

            Function<Map<String, Object>, FilterAction> filter = (ctx) -> FilterAction.CONTINUE;
            framework.registerFilter("event", filter);

            Map<String, Object> context = new HashMap<>();
            Object result = framework.trigger("event", context);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("test_filter_stop - STOP prevents callback execution")
        void testFilterStop() {
            Function<Map<String, Object>, FilterAction> filter = (ctx) -> FilterAction.STOP;
            framework.registerFilter("event", filter);

            Function<Map<String, Object>, Object> callback = (ctx) -> "result";
            framework.on("event", callback);

            Map<String, Object> context = new HashMap<>();
            Object result = framework.trigger("event", context);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Filter Context")
    class FilterContextTests {

        @Test
        @DisplayName("test_filter_receives_context - filter receives trigger context")
        void testFilterReceivesContext() {
            Map<String, Object> received = new HashMap<>();

            Function<Map<String, Object>, FilterAction> filter = (ctx) -> {
                received.putAll(ctx);
                return FilterAction.CONTINUE;
            };
            framework.registerFilter("event", filter);

            Function<Map<String, Object>, Object> callback = (ctx) -> "result";
            framework.on("event", callback);

            Map<String, Object> context = new HashMap<>();
            context.put("key", "value");
            framework.trigger("event", context);

            assertThat(received.get("key")).isEqualTo("value");
        }
    }
}