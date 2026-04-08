// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.runner.callback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for callback framework filters.
 * Translated from Python test_filters.py
 */
@DisplayName("Callback Filters Tests")
class CallbackFiltersTest {

    private CallbackInfo dummyCallback() {
        return CallbackInfo.builder()
                .callback(kwargs -> null)
                .priority(0)
                .callbackName("dummy")
                .build();
    }

    // ========== EventFilter ==========

    @Test
    @DisplayName("EventFilter default returns CONTINUE")
    void testEventFilterDefaultContinues() {
        EventFilter filter = new EventFilter();
        FilterResult result = filter.filter("test_event", dummyCallback(), new Object[0], Map.of());
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("EventFilter default name is class name")
    void testEventFilterDefaultName() {
        EventFilter filter = new EventFilter();
        assertEquals("EventFilter", filter.getName());
    }

    @Test
    @DisplayName("EventFilter custom name")
    void testEventFilterCustomName() {
        EventFilter filter = new EventFilter("CustomFilter");
        assertEquals("CustomFilter", filter.getName());
    }

    // ========== RateLimitFilter ==========

    @Test
    @DisplayName("RateLimit allows within limit")
    void testRateLimitFilterAllowsWithinLimit() {
        RateLimitFilter filter = new RateLimitFilter(3, 2.0);
        CallbackInfo cb = dummyCallback();
        for (int i = 0; i < 3; i++) {
            FilterResult result = filter.filter("test", cb, new Object[0], Map.of());
            assertEquals(FilterAction.CONTINUE, result.getAction(), "Call " + (i + 1) + " should be allowed");
        }
    }

    @Test
    @DisplayName("RateLimit blocks exceeding limit")
    void testRateLimitFilterBlocksExceedingLimit() {
        RateLimitFilter filter = new RateLimitFilter(2, 2.0);
        CallbackInfo cb = dummyCallback();
        filter.filter("test", cb, new Object[0], Map.of());
        filter.filter("test", cb, new Object[0], Map.of());
        FilterResult result = filter.filter("test", cb, new Object[0], Map.of());
        assertEquals(FilterAction.SKIP, result.getAction());
        assertTrue(result.getReason().contains("Rate limit exceeded"));
    }

    @Test
    @DisplayName("RateLimit different callbacks tracked separately")
    void testRateLimitFilterDifferentCallbacksTracked() {
        RateLimitFilter filter = new RateLimitFilter(2, 2.0);
        CallbackInfo cb1 = CallbackInfo.builder().callback(kwargs -> null).priority(0).callbackName("cb1").build();
        CallbackInfo cb2 = CallbackInfo.builder().callback(kwargs -> null).priority(0).callbackName("cb2").build();

        for (int i = 0; i < 2; i++) {
            FilterResult r = filter.filter("test", cb1, new Object[0], Map.of());
            assertEquals(FilterAction.CONTINUE, r.getAction());
        }
        for (int i = 0; i < 2; i++) {
            FilterResult r = filter.filter("test", cb2, new Object[0], Map.of());
            assertEquals(FilterAction.CONTINUE, r.getAction());
        }
    }

    @Test
    @DisplayName("RateLimit window expiration")
    void testRateLimitFilterWindowExpiration() throws InterruptedException {
        RateLimitFilter filter = new RateLimitFilter(2, 0.1);
        CallbackInfo cb = dummyCallback();
        filter.filter("test", cb, new Object[0], Map.of());
        filter.filter("test", cb, new Object[0], Map.of());
        Thread.sleep(150);
        FilterResult result = filter.filter("test", cb, new Object[0], Map.of());
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("RateLimit custom name")
    void testRateLimitFilterCustomName() {
        RateLimitFilter filter = new RateLimitFilter(10, 1.0, "CustomRateLimit");
        assertEquals("CustomRateLimit", filter.getName());
    }

    // ========== CircuitBreakerFilter ==========

    @Test
    @DisplayName("CircuitBreaker closed state allows calls")
    void testCircuitBreakerClosedStateAllows() {
        CircuitBreakerFilter filter = new CircuitBreakerFilter(3, 1.0);
        CallbackInfo cb = dummyCallback();
        FilterResult result = filter.filter("test", cb, new Object[0], Map.of());
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("CircuitBreaker opens after threshold")
    void testCircuitBreakerOpensAfterThreshold() {
        CircuitBreakerFilter filter = new CircuitBreakerFilter(3, 1.0);
        CallbackInfo cb = dummyCallback();
        for (int i = 0; i < 3; i++) {
            filter.recordFailure("test", cb);
        }
        FilterResult result = filter.filter("test", cb, new Object[0], Map.of());
        assertEquals(FilterAction.SKIP, result.getAction());
        assertTrue(result.getReason().contains("Circuit breaker open"));
    }

    @Test
    @DisplayName("CircuitBreaker success resets failures")
    void testCircuitBreakerSuccessResetsFailures() {
        CircuitBreakerFilter filter = new CircuitBreakerFilter(3, 1.0);
        CallbackInfo cb = dummyCallback();
        filter.recordFailure("test", cb);
        filter.recordFailure("test", cb);
        filter.recordSuccess("test", cb);
        filter.recordFailure("test", cb);
        filter.recordFailure("test", cb);
        FilterResult result = filter.filter("test", cb, new Object[0], Map.of());
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("CircuitBreaker timeout allows retry")
    void testCircuitBreakerTimeoutAllowsRetry() throws InterruptedException {
        CircuitBreakerFilter filter = new CircuitBreakerFilter(2, 0.1);
        CallbackInfo cb = dummyCallback();
        filter.recordFailure("test", cb);
        filter.recordFailure("test", cb);
        FilterResult openResult = filter.filter("test", cb, new Object[0], Map.of());
        assertEquals(FilterAction.SKIP, openResult.getAction());
        Thread.sleep(150);
        FilterResult closeResult = filter.filter("test", cb, new Object[0], Map.of());
        assertEquals(FilterAction.CONTINUE, closeResult.getAction());
    }

    @Test
    @DisplayName("CircuitBreaker different callbacks tracked separately")
    void testCircuitBreakerDifferentCallbacksTracked() {
        CircuitBreakerFilter filter = new CircuitBreakerFilter(2, 1.0);
        CallbackInfo cb1 = CallbackInfo.builder().callback(kwargs -> null).priority(0).callbackName("cb1").build();
        CallbackInfo cb2 = CallbackInfo.builder().callback(kwargs -> null).priority(0).callbackName("cb2").build();
        filter.recordFailure("test", cb1);
        filter.recordFailure("test", cb1);
        assertEquals(FilterAction.SKIP, filter.filter("test", cb1, new Object[0], Map.of()).getAction());
        assertEquals(FilterAction.CONTINUE, filter.filter("test", cb2, new Object[0], Map.of()).getAction());
    }

    @Test
    @DisplayName("CircuitBreaker custom name")
    void testCircuitBreakerCustomName() {
        CircuitBreakerFilter filter = new CircuitBreakerFilter(5, 60.0, "CustomBreaker");
        assertEquals("CustomBreaker", filter.getName());
    }

    // ========== ValidationFilter ==========

    @Test
    @DisplayName("ValidationFilter valid args continue")
    void testValidationFilterValidArgsContinue() {
        ValidationFilter filter = new ValidationFilter(kwargs -> {
            Object val = kwargs.get("value");
            return val instanceof Integer && (Integer) val > 0;
        });
        CallbackInfo cb = dummyCallback();
        FilterResult result = filter.filter("test", cb, new Object[0], Map.of("value", 10));
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("ValidationFilter invalid args skip")
    void testValidationFilterInvalidArgsSkip() {
        ValidationFilter filter = new ValidationFilter(kwargs -> {
            Object val = kwargs.get("value");
            return val instanceof Integer && (Integer) val > 0;
        });
        CallbackInfo cb = dummyCallback();
        FilterResult result = filter.filter("test", cb, new Object[0], Map.of("value", -5));
        assertEquals(FilterAction.SKIP, result.getAction());
        assertTrue(result.getReason().toLowerCase().contains("validation failed"));
    }

    @Test
    @DisplayName("ValidationFilter exception skips")
    void testValidationFilterExceptionSkips() {
        ValidationFilter filter = new ValidationFilter(kwargs -> {
            throw new RuntimeException("Validation error");
        });
        CallbackInfo cb = dummyCallback();
        FilterResult result = filter.filter("test", cb, new Object[0], Map.of("arg", "test"));
        assertEquals(FilterAction.SKIP, result.getAction());
        assertTrue(result.getReason().contains("Validation error"));
    }

    // ========== LoggingFilter ==========

    @Test
    @DisplayName("LoggingFilter always continues")
    void testLoggingFilterAlwaysContinues() {
        LoggingFilter filter = new LoggingFilter();
        CallbackInfo cb = dummyCallback();
        FilterResult result = filter.filter("test", cb, new Object[]{"arg1"}, Map.of("key", "value"));
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("LoggingFilter default logger not null")
    void testLoggingFilterDefaultLogger() {
        LoggingFilter filter = new LoggingFilter();
        assertNotNull(filter.getName());
    }

    // ========== AuthFilter ==========

    @Test
    @DisplayName("AuthFilter authorized user continues")
    void testAuthFilterAuthorizedUserContinues() {
        AuthFilter filter = new AuthFilter("admin");
        CallbackInfo cb = dummyCallback();
        FilterResult result = filter.filter("test", cb, new Object[0], Map.of("user_role", "admin"));
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("AuthFilter unauthorized user skips")
    void testAuthFilterUnauthorizedUserSkips() {
        AuthFilter filter = new AuthFilter("admin");
        CallbackInfo cb = dummyCallback();
        FilterResult result = filter.filter("test", cb, new Object[0], Map.of("user_role", "guest"));
        assertEquals(FilterAction.SKIP, result.getAction());
        assertTrue(result.getReason().contains("Unauthorized"));
        assertTrue(result.getReason().contains("admin"));
        assertTrue(result.getReason().contains("guest"));
    }

    @Test
    @DisplayName("AuthFilter missing role defaults to guest")
    void testAuthFilterMissingRoleDefaultsToGuest() {
        AuthFilter filter = new AuthFilter("admin");
        CallbackInfo cb = dummyCallback();
        FilterResult result = filter.filter("test", cb, new Object[0], Map.of());
        assertEquals(FilterAction.SKIP, result.getAction());
    }

    // ========== ParamModifyFilter ==========

    @Test
    @DisplayName("ParamModifyFilter modifies kwargs")
    void testParamModifyFilterModifiesKwargs() {
        ParamModifyFilter filter = new ParamModifyFilter((args, kwargs) -> {
            Map<String, Object> newKwargs = new java.util.HashMap<>(kwargs);
            for (Map.Entry<String, Object> e : kwargs.entrySet()) {
                if (e.getValue() instanceof Integer) {
                    newKwargs.put(e.getKey(), (Integer) e.getValue() * 2);
                }
            }
            return new Object[]{args, newKwargs};
        });

        // Note: ParamModifyFilter returns modified in Object[][] format
        CallbackInfo cb = dummyCallback();
        FilterResult result = filter.filter("test", cb, new Object[0], Map.of("value", 5));
        assertEquals(FilterAction.MODIFY, result.getAction());
    }

    // ========== ConditionalFilter ==========

    @Test
    @DisplayName("ConditionalFilter condition met continues")
    void testConditionalFilterConditionMetContinues() {
        ConditionalFilter filter = new ConditionalFilter(
                (event, callback, args, kwargs) -> kwargs.containsKey("required_key"));
        CallbackInfo cb = dummyCallback();
        FilterResult result = filter.filter("test", cb, new Object[0], Map.of("required_key", "value"));
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("ConditionalFilter condition not met skips")
    void testConditionalFilterConditionNotMetSkips() {
        ConditionalFilter filter = new ConditionalFilter(
                (event, callback, args, kwargs) -> kwargs.containsKey("required_key"));
        CallbackInfo cb = dummyCallback();
        FilterResult result = filter.filter("test", cb, new Object[0], Map.of());
        assertEquals(FilterAction.SKIP, result.getAction());
    }
}
