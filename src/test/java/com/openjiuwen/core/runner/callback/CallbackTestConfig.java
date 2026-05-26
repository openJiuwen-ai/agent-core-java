/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Test configuration and fixtures for runner callback tests.
 * Mirrors Python's tests/unit_tests/core/runner/callback/conftest.py
 * 
 * Provides factory methods and setup helpers for testing the callback framework.
 * In Java, pytest fixtures are translated to static factory methods that can be
 * called from test classes using @BeforeEach or directly in test methods.
 */
public class CallbackTestConfig {

    /**
     * Create a CallbackFramework instance without metrics.
     * Mirrors Python's framework fixture.
     *
     * @return CallbackFramework with enableMetrics=false, enableLogging=false
     */
    public static CallbackFramework createFramework() {
        return new CallbackFramework(false, false);
    }

    /**
     * Create a CallbackFramework instance with metrics enabled.
     * Mirrors Python's framework_with_metrics fixture.
     *
     * @return CallbackFramework with enableMetrics=true, enableLogging=false
     */
    public static CallbackFramework createFrameworkWithMetrics() {
        return new CallbackFramework(true, false);
    }

    /**
     * Create a CallbackFramework instance with logging enabled.
     * Mirrors Python's framework_with_logging fixture.
     *
     * @return CallbackFramework with enableMetrics=false, enableLogging=true
     */
    public static CallbackFramework createFrameworkWithLogging() {
        return new CallbackFramework(false, true);
    }

    /**
     * Create a RateLimitFilter with 3 calls per 2 seconds.
     * Mirrors Python's rate_limit_filter fixture.
     *
     * @return RateLimitFilter with maxCalls=3, timeWindow=2.0
     */
    public static RateLimitFilter createRateLimitFilter() {
        return new RateLimitFilter(3, 2.0);
    }

    /**
     * Create a CircuitBreakerFilter with 3 failure threshold and 1s timeout.
     * Mirrors Python's circuit_breaker_filter fixture.
     *
     * @return CircuitBreakerFilter with failureThreshold=3, timeout=1.0
     */
    public static CircuitBreakerFilter createCircuitBreakerFilter() {
        return new CircuitBreakerFilter(3, 1.0);
    }

    /**
     * Create a ValidationFilter that validates value > 0.
     * Mirrors Python's validation_filter fixture.
     *
     * @return ValidationFilter that checks if kwargs contains "value" > 0
     */
    public static ValidationFilter createValidationFilter() {
        return new ValidationFilter(kwargs -> {
            if (kwargs == null) {
                return false;
            }
            Object valueObj = kwargs.get("value");
            if (valueObj == null) {
                return false;
            }
            if (valueObj instanceof Number) {
                return ((Number) valueObj).doubleValue() > 0;
            }
            return false;
        });
    }

    /**
     * Create a LoggingFilter instance.
     * Mirrors Python's logging_filter fixture.
     *
     * @return LoggingFilter instance
     */
    public static LoggingFilter createLoggingFilter() {
        return new LoggingFilter();
    }

    /**
     * Create an AuthFilter requiring 'admin' role.
     * Mirrors Python's auth_filter fixture.
     *
     * @return AuthFilter with requiredRole="admin"
     */
    public static AuthFilter createAuthFilter() {
        return new AuthFilter("admin");
    }

    /**
     * Create a ParamModifyFilter that doubles the value.
     * Mirrors Python's param_modify_filter fixture.
     *
     * @return ParamModifyFilter that doubles the "value" in kwargs
     */
    public static ParamModifyFilter createParamModifyFilter() {
        return new ParamModifyFilter((args, kwargs) -> {
            Map<String, Object> newKwargs = new HashMap<>(kwargs != null ? kwargs : new HashMap<>());
            Object valueObj = newKwargs.get("value");
            double value = 0;
            if (valueObj instanceof Number) {
                value = ((Number) valueObj).doubleValue();
            }
            newKwargs.put("value", value * 2);
            return new Object[]{args, newKwargs};
        });
    }

    /**
     * Create a ConditionalFilter that checks 'enabled' kwarg.
     * Mirrors Python's conditional_filter fixture.
     *
     * @return ConditionalFilter that checks if kwargs contains "enabled" = true
     */
    public static ConditionalFilter createConditionalFilter() {
        return new ConditionalFilter((event, callback, args, kwargs) -> {
            if (kwargs == null) {
                return false;
            }
            Object enabledObj = kwargs.get("enabled");
            return Boolean.TRUE.equals(enabledObj);
        });
    }

    /**
     * Create an empty CallbackChain instance.
     * Mirrors Python's callback_chain fixture.
     *
     * @return CallbackChain with name="test_chain"
     */
    public static CallbackChain createCallbackChain() {
        return new CallbackChain("test_chain");
    }

    /**
     * Create a List for tracking callback execution order.
     * Mirrors Python's result_tracker fixture.
     *
     * @return Empty ArrayList for tracking results
     */
    public static List<Object> createResultTracker() {
        return new ArrayList<>();
    }

    /**
     * Create a simple async callback function.
     * Mirrors Python's simple_async_callback fixture.
     * 
     * In Java, this returns a Function that accepts kwargs map and returns a formatted string.
     *
     * @return Function that returns "received: {message}" from kwargs["message"]
     */
    public static Function<Map<String, Object>, Object> createSimpleAsyncCallback() {
        return kwargs -> {
            if (kwargs == null) {
                kwargs = new HashMap<>();
            }
            Object messageObj = kwargs.getOrDefault("message", "default");
            String message = messageObj != null ? messageObj.toString() : "default";
            return "received: " + message;
        };
    }

    /**
     * Factory for creating CallbackInfo instances.
     * Mirrors Python's callback_info_factory fixture.
     *
     * @param callback The callback function
     * @param priority Execution priority (default 0)
     * @return CallbackInfo instance
     */
    public static CallbackInfo createCallbackInfo(Function<Map<String, Object>, Object> callback, int priority) {
        return CallbackInfo.builder()
                .callback(callback)
                .priority(priority)
                .build();
    }

    /**
     * Factory for creating CallbackInfo instances with default priority.
     * Mirrors Python's callback_info_factory fixture with default priority=0.
     *
     * @param callback The callback function
     * @return CallbackInfo instance with priority=0
     */
    public static CallbackInfo createCallbackInfo(Function<Map<String, Object>, Object> callback) {
        return createCallbackInfo(callback, 0);
    }

    /**
     * Factory for creating ChainContext instances.
     * Mirrors Python's chain_context_factory fixture.
     *
     * @param event Event name (default "test_event")
     * @param args Positional arguments (default empty array)
     * @param kwargs Keyword arguments (default empty map)
     * @return ChainContext instance
     */
    public static ChainContext createChainContext(String event, Object[] args, Map<String, Object> kwargs) {
        String actualEvent = event != null ? event : "test_event";
        Object[] actualArgs = args != null ? args : new Object[0];
        Map<String, Object> actualKwargs = kwargs != null ? kwargs : new HashMap<>();
        return new ChainContext(actualEvent, actualArgs, actualKwargs);
    }

    /**
     * Factory for creating ChainContext instances with default values.
     * Mirrors Python's chain_context_factory fixture with default parameters.
     *
     * @return ChainContext instance with event="test_event", empty args and kwargs
     */
    public static ChainContext createChainContext() {
        return createChainContext("test_event", null, null);
    }
}