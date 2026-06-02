/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.runner.callback;

import com.openjiuwen.core.runner.callback.AuthFilter;
import com.openjiuwen.core.runner.callback.CallbackChain;
import com.openjiuwen.core.runner.callback.CallbackFramework;
import com.openjiuwen.core.runner.callback.CallbackInfo;
import com.openjiuwen.core.runner.callback.ChainContext;
import com.openjiuwen.core.runner.callback.CircuitBreakerFilter;
import com.openjiuwen.core.runner.callback.ConditionalFilter;
import com.openjiuwen.core.runner.callback.FilterAction;
import com.openjiuwen.core.runner.callback.FilterResult;
import com.openjiuwen.core.runner.callback.LoggingFilter;
import com.openjiuwen.core.runner.callback.ParamModifyFilter;
import com.openjiuwen.core.runner.callback.RateLimitFilter;
import com.openjiuwen.core.runner.callback.ValidationFilter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Callback framework conftest fixture parity tests.
 *
 * <p>Mirrors Python's {@code conftest.py} in
 * {@code tests.unit_tests.core.runner.callback.conftest}.</p>
 */
@DisplayName("Callback Conftest Tests")
class TestConftest {

    @Nested
    @DisplayName("Fixture Tests")
    class FixtureTests {

        @Test
        @DisplayName("framework fixtures - create configured frameworks")
        void testFrameworkFixtures() {
            CallbackFramework framework = new CallbackFramework(false, false);
            CallbackFramework frameworkWithMetrics = new CallbackFramework(true, false);
            CallbackFramework frameworkWithLogging = new CallbackFramework(false, true);

            assertThat(framework).isNotNull();
            assertThat(frameworkWithMetrics).isNotNull();
            assertThat(frameworkWithLogging).isNotNull();
        }

        @Test
        @DisplayName("filter fixtures - create all shared filters")
        void testFilterFixtures() {
            RateLimitFilter rateLimitFilter = new RateLimitFilter(3, 2.0);
            CircuitBreakerFilter circuitBreakerFilter = new CircuitBreakerFilter(3, 1.0);
            ValidationFilter validationFilter = new ValidationFilter(kwargs -> ((int) kwargs.getOrDefault("value", 0)) > 0);
            LoggingFilter loggingFilter = new LoggingFilter();
            AuthFilter authFilter = new AuthFilter("admin");
            ParamModifyFilter paramModifyFilter = new ParamModifyFilter(
                    (args, kwargs) -> new Object[] {args, Map.of("value", ((int) kwargs.getOrDefault("value", 0)) * 2)});
            ConditionalFilter conditionalFilter = new ConditionalFilter(
                    (event, callback, args, kwargs) -> Boolean.TRUE.equals(kwargs.get("enabled")));

            assertThat(rateLimitFilter.getName()).isEqualTo("RateLimit");
            assertThat(circuitBreakerFilter.getName()).isEqualTo("CircuitBreaker");
            assertThat(validationFilter.getName()).isEqualTo("Validation");
            assertThat(loggingFilter.getName()).isEqualTo("Logging");
            assertThat(authFilter.getName()).isEqualTo("Auth");
            assertThat(paramModifyFilter.getName()).isEqualTo("ParamModify");
            assertThat(conditionalFilter.getName()).isEqualTo("Conditional");
        }

        @Test
        @DisplayName("callback_chain and result_tracker fixtures")
        void testCallbackChainAndResultTrackerFixtures() {
            CallbackChain callbackChain = new CallbackChain("test_chain");
            ArrayList<Object> resultTracker = new ArrayList<>();

            assertThat(callbackChain.getName()).isEqualTo("test_chain");
            assertThat(resultTracker).isEmpty();
        }

        @Test
        @DisplayName("simple_async_callback and callback_info_factory fixtures")
        void testCallbackInfoFactoryFixtures() {
            Function<Map<String, Object>, Object> callback = kwargs -> "received: "
                    + kwargs.getOrDefault("message", "default");

            CallbackInfo callbackInfo = CallbackInfo.builder()
                    .callback(callback)
                    .priority(5)
                    .callbackName("callback")
                    .build();

            assertThat(callback.apply(Map.of("message", "hello"))).isEqualTo("received: hello");
            assertThat(callbackInfo.getPriority()).isEqualTo(5);
            assertThat(callbackInfo.getCallbackDisplayName()).isEqualTo("callback");
        }

        @Test
        @DisplayName("chain_context_factory fixture")
        void testChainContextFactoryFixture() {
            ChainContext context = new ChainContext("test_event", new Object[] {"arg"}, Map.of("key", "value"));

            assertThat(context.getEvent()).isEqualTo("test_event");
            assertThat(context.getInitialArgs()).containsExactly("arg");
            assertThat(context.getInitialKwargs()).containsEntry("key", "value");
        }

        @Test
        @DisplayName("conditional and param modify fixtures apply expected actions")
        void testFilterFixtureBehavior() {
            CallbackInfo callbackInfo = CallbackInfo.builder()
                    .callback(kwargs -> "ok")
                    .callbackName("callback")
                    .build();
            ConditionalFilter conditionalFilter = new ConditionalFilter(
                    (event, callback, args, kwargs) -> Boolean.TRUE.equals(kwargs.get("enabled")));
            ParamModifyFilter paramModifyFilter = new ParamModifyFilter(
                    (args, kwargs) -> new Object[] {args, Map.of("value", ((int) kwargs.get("value")) * 2)});

            FilterResult skipped = conditionalFilter.filter("event", callbackInfo, new Object[0], Map.of("enabled", false));
            FilterResult modified = paramModifyFilter.filter("event", callbackInfo, new Object[0], Map.of("value", 2));

            assertThat(skipped.getAction()).isEqualTo(FilterAction.SKIP);
            assertThat(modified.getAction()).isEqualTo(FilterAction.MODIFY);
            assertThat(modified.getModifiedKwargs()).containsEntry("value", 4);
        }
    }
}
