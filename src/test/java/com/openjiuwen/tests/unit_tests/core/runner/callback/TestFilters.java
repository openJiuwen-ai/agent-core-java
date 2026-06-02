/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.runner.callback;

import com.openjiuwen.core.runner.callback.AuthFilter;
import com.openjiuwen.core.runner.callback.CallbackInfo;
import com.openjiuwen.core.runner.callback.CircuitBreakerFilter;
import com.openjiuwen.core.runner.callback.ConditionalFilter;
import com.openjiuwen.core.runner.callback.EventFilter;
import com.openjiuwen.core.runner.callback.FilterAction;
import com.openjiuwen.core.runner.callback.FilterResult;
import com.openjiuwen.core.runner.callback.LoggingFilter;
import com.openjiuwen.core.runner.callback.ParamModifyFilter;
import com.openjiuwen.core.runner.callback.RateLimitFilter;
import com.openjiuwen.core.runner.callback.ValidationFilter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for callback framework filters.
 * 
 * <p>Mirrors Python's {@code test_filters} in
 * {@code tests.unit_tests.core.runner.callback.test_filters}.</p>
 */
@DisplayName("TestFilters")
class TestFilters {

    private CallbackInfo createCallbackInfo(String name) {
        return CallbackInfo.builder()
                .callback(kwargs -> null)
                .callbackName(name)
                .build();
    }

    @Test
    @Tag("level0")
    @DisplayName("Test base EventFilter returns CONTINUE by default")
    void testEventFilterDefaultContinues() {
        EventFilter filter = new EventFilter();
        CallbackInfo callback = createCallbackInfo("dummy");

        FilterResult result = filter.filter("test_event", callback, new Object[0], new HashMap<>());

        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test filter uses class name as default name")
    void testEventFilterDefaultName() {
        EventFilter filter = new EventFilter();
        assertEquals("EventFilter", filter.getName());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test filter can have custom name")
    void testEventFilterCustomName() {
        EventFilter filter = new EventFilter("CustomFilter");
        assertEquals("CustomFilter", filter.getName());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test filter allows calls within rate limit")
    void testRateLimitFilterAllowsWithinLimit() {
        RateLimitFilter filter = new RateLimitFilter(3, 2.0);
        CallbackInfo callback = createCallbackInfo("callback");

        for (int i = 0; i < 3; i++) {
            FilterResult result = filter.filter("test", callback, new Object[0], new HashMap<>());
            assertEquals(FilterAction.CONTINUE, result.getAction(), "Call " + (i + 1) + " should be allowed");
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("Test filter blocks calls exceeding rate limit")
    void testRateLimitFilterBlocksExceedingLimit() {
        RateLimitFilter filter = new RateLimitFilter(2, 2.0);
        CallbackInfo callback = createCallbackInfo("callback");

        // First two calls should pass
        filter.filter("test", callback, new Object[0], new HashMap<>());
        filter.filter("test", callback, new Object[0], new HashMap<>());

        // Third call should be blocked
        FilterResult result = filter.filter("test", callback, new Object[0], new HashMap<>());
        assertEquals(FilterAction.SKIP, result.getAction());
        assertTrue(result.getReason().contains("Rate limit exceeded"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test rate limit is tracked per callback")
    void testRateLimitFilterDifferentCallbacksTrackedSeparately() {
        RateLimitFilter filter = new RateLimitFilter(2, 2.0);
        CallbackInfo callback1 = createCallbackInfo("callback1");
        CallbackInfo callback2 = createCallbackInfo("callback2");

        // Both callbacks can use their own limits
        for (int i = 0; i < 2; i++) {
            FilterResult result = filter.filter("test", callback1, new Object[0], new HashMap<>());
            assertEquals(FilterAction.CONTINUE, result.getAction());
        }

        for (int i = 0; i < 2; i++) {
            FilterResult result = filter.filter("test", callback2, new Object[0], new HashMap<>());
            assertEquals(FilterAction.CONTINUE, result.getAction());
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("Test rate limit window expiration allows retry")
    void testRateLimitFilterWindowExpiration() throws InterruptedException {
        RateLimitFilter filter = new RateLimitFilter(1, 0.05);
        CallbackInfo callback = createCallbackInfo("callback");

        assertEquals(FilterAction.CONTINUE, filter.filter("test", callback, new Object[0], new HashMap<>()).getAction());
        assertEquals(FilterAction.SKIP, filter.filter("test", callback, new Object[0], new HashMap<>()).getAction());
        Thread.sleep(80);

        FilterResult result = filter.filter("test", callback, new Object[0], new HashMap<>());
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test RateLimitFilter can have custom name")
    void testRateLimitFilterCustomName() {
        RateLimitFilter filter = new RateLimitFilter(10, 1.0, "CustomRateLimit");
        assertEquals("CustomRateLimit", filter.getName());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test filter allows calls when circuit is closed")
    void testCircuitBreakerFilterClosedStateAllowsCalls() {
        CircuitBreakerFilter filter = new CircuitBreakerFilter(3, 1.0);
        CallbackInfo callback = createCallbackInfo("callback");

        FilterResult result = filter.filter("test", callback, new Object[0], new HashMap<>());
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test circuit opens after failure threshold")
    void testCircuitBreakerFilterOpensAfterThreshold() {
        CircuitBreakerFilter filter = new CircuitBreakerFilter(3, 1.0);
        CallbackInfo callback = createCallbackInfo("callback");

        // Record failures
        for (int i = 0; i < 3; i++) {
            filter.recordFailure("test", callback);
        }

        // Circuit should be open now
        FilterResult result = filter.filter("test", callback, new Object[0], new HashMap<>());
        assertEquals(FilterAction.SKIP, result.getAction());
        assertTrue(result.getReason().contains("Circuit breaker open"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test successful call resets failure count")
    void testCircuitBreakerFilterSuccessResetsFailures() {
        CircuitBreakerFilter filter = new CircuitBreakerFilter(3, 1.0);
        CallbackInfo callback = createCallbackInfo("callback");

        // Record some failures
        filter.recordFailure("test", callback);
        filter.recordFailure("test", callback);

        // Record success
        filter.recordSuccess("test", callback);

        // Add more failures - shouldn't trip breaker yet
        filter.recordFailure("test", callback);
        filter.recordFailure("test", callback);

        FilterResult result = filter.filter("test", callback, new Object[0], new HashMap<>());
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test circuit breaker timeout allows retry")
    void testCircuitBreakerFilterTimeoutAllowsRetry() throws InterruptedException {
        CircuitBreakerFilter filter = new CircuitBreakerFilter(1, 0.05);
        CallbackInfo callback = createCallbackInfo("callback");

        filter.recordFailure("test", callback);
        assertEquals(FilterAction.SKIP, filter.filter("test", callback, new Object[0], new HashMap<>()).getAction());
        Thread.sleep(80);

        FilterResult result = filter.filter("test", callback, new Object[0], new HashMap<>());
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test circuit state is tracked per callback")
    void testCircuitBreakerFilterDifferentCallbacksTrackedSeparately() {
        CircuitBreakerFilter filter = new CircuitBreakerFilter(2, 1.0);
        CallbackInfo callback1 = createCallbackInfo("callback1");
        CallbackInfo callback2 = createCallbackInfo("callback2");

        // Trip breaker for callback1
        filter.recordFailure("test", callback1);
        filter.recordFailure("test", callback1);

        // callback1 should be blocked
        FilterResult result1 = filter.filter("test", callback1, new Object[0], new HashMap<>());
        assertEquals(FilterAction.SKIP, result1.getAction());

        // callback2 should still work
        FilterResult result2 = filter.filter("test", callback2, new Object[0], new HashMap<>());
        assertEquals(FilterAction.CONTINUE, result2.getAction());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test CircuitBreakerFilter can have custom name")
    void testCircuitBreakerFilterCustomName() {
        CircuitBreakerFilter filter = new CircuitBreakerFilter(5, 60.0, "CustomBreaker");
        assertEquals("CustomBreaker", filter.getName());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test filter continues with valid arguments")
    void testValidationFilterValidArgsContinue() {
        ValidationFilter filter = new ValidationFilter(kwargs -> {
            Object value = kwargs.get("value");
            return value instanceof Integer && (Integer) value > 0;
        });
        CallbackInfo callback = createCallbackInfo("callback");

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("value", 10);
        FilterResult result = filter.filter("test", callback, new Object[0], kwargs);
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test filter skips with invalid arguments")
    void testValidationFilterInvalidArgsSkip() {
        ValidationFilter filter = new ValidationFilter(kwargs -> {
            Object value = kwargs.get("value");
            return value instanceof Integer && (Integer) value > 0;
        });
        CallbackInfo callback = createCallbackInfo("callback");

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("value", -5);
        FilterResult result = filter.filter("test", callback, new Object[0], kwargs);
        assertEquals(FilterAction.SKIP, result.getAction());
        assertTrue(result.getReason().toLowerCase().contains("validation failed"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test validation filter can validate keyword arguments")
    void testValidationFilterKwargsValidation() {
        ValidationFilter filter = new ValidationFilter(kwargs -> "alice".equals(kwargs.get("user")));
        CallbackInfo callback = createCallbackInfo("callback");

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("user", "alice");
        assertEquals(FilterAction.CONTINUE, filter.filter("test", callback, new Object[0], kwargs).getAction());

        kwargs.put("user", "bob");
        assertEquals(FilterAction.SKIP, filter.filter("test", callback, new Object[0], kwargs).getAction());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test filter skips when validator raises exception")
    void testValidationFilterValidatorExceptionSkips() {
        ValidationFilter filter = new ValidationFilter(kwargs -> {
            throw new RuntimeException("Validation error");
        });
        CallbackInfo callback = createCallbackInfo("callback");

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("value", "arg");
        FilterResult result = filter.filter("test", callback, new Object[0], kwargs);
        assertEquals(FilterAction.SKIP, result.getAction());
        assertTrue(result.getReason().contains("Validation error"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test LoggingFilter always returns CONTINUE")
    void testLoggingFilterAlwaysContinues() {
        LoggingFilter filter = new LoggingFilter();
        CallbackInfo callback = createCallbackInfo("callback");

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("key", "value");
        FilterResult result = filter.filter("test", callback, new Object[]{"arg1"}, kwargs);
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test LoggingFilter logs execution info")
    void testLoggingFilterLogsExecutionInfo() {
        Logger logger = mock(Logger.class);
        LoggingFilter filter = new LoggingFilter(logger, "Logging");
        CallbackInfo callback = createCallbackInfo("callback");
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("key", "value");

        filter.filter("test", callback, new Object[]{"arg1"}, kwargs);

        verify(logger).info(eq("Event: {}, Callback: {}, Args: {}, Kwargs: {}"),
                eq("test"), eq("callback"), any(), eq(kwargs));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test LoggingFilter accepts a custom logger")
    void testLoggingFilterCustomLogger() {
        Logger logger = mock(Logger.class);
        LoggingFilter filter = new LoggingFilter(logger, "CustomLogging");
        CallbackInfo callback = createCallbackInfo("callback");

        FilterResult result = filter.filter("event", callback, new Object[0], new HashMap<>());

        assertEquals("CustomLogging", filter.getName());
        assertEquals(FilterAction.CONTINUE, result.getAction());
        verify(logger).info(eq("Event: {}, Callback: {}, Args: {}, Kwargs: {}"),
                eq("event"), eq("callback"), any(), any());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test LoggingFilter creates default logger")
    void testLoggingFilterDefaultLogger() {
        LoggingFilter filter = new LoggingFilter();
        assertEquals("Logging", filter.getName());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test filter continues for authorized user")
    void testAuthFilterAuthorizedUserContinues() {
        AuthFilter filter = new AuthFilter("admin");
        CallbackInfo callback = createCallbackInfo("callback");

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("user_role", "admin");
        FilterResult result = filter.filter("test", callback, new Object[0], kwargs);
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test filter skips for unauthorized user")
    void testAuthFilterUnauthorizedUserSkips() {
        AuthFilter filter = new AuthFilter("admin");
        CallbackInfo callback = createCallbackInfo("callback");

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("user_role", "guest");
        FilterResult result = filter.filter("test", callback, new Object[0], kwargs);
        assertEquals(FilterAction.SKIP, result.getAction());
        assertTrue(result.getReason().contains("Unauthorized"));
        assertTrue(result.getReason().contains("admin"));
        assertTrue(result.getReason().contains("guest"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test filter treats missing role as guest")
    void testAuthFilterMissingRoleDefaultsToGuest() {
        AuthFilter filter = new AuthFilter("admin");
        CallbackInfo callback = createCallbackInfo("callback");

        FilterResult result = filter.filter("test", callback, new Object[0], new HashMap<>());
        assertEquals(FilterAction.SKIP, result.getAction());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test filter modifies arguments correctly")
    void testParamModifyFilterModifiesArguments() {
        ParamModifyFilter filter = new ParamModifyFilter((args, kwargs) -> {
            Map<String, Object> newKwargs = new HashMap<>();
            for (Map.Entry<String, Object> entry : kwargs.entrySet()) {
                if (entry.getValue() instanceof Integer) {
                    newKwargs.put(entry.getKey(), (Integer) entry.getValue() * 2);
                } else {
                    newKwargs.put(entry.getKey(), entry.getValue());
                }
            }
            return new Object[]{args, newKwargs};
        });
        CallbackInfo callback = createCallbackInfo("callback");

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("value", 5);
        FilterResult result = filter.filter("test", callback, new Object[0], kwargs);
        assertEquals(FilterAction.MODIFY, result.getAction());
        assertEquals(10, result.getModifiedKwargs().get("value"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test filter can modify positional arguments")
    void testParamModifyFilterModifiesPositionalArgs() {
        ParamModifyFilter filter = new ParamModifyFilter((args, kwargs) -> {
            Object[] newArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Integer) {
                    newArgs[i] = (Integer) args[i] * 2;
                } else {
                    newArgs[i] = args[i];
                }
            }
            return new Object[]{newArgs, kwargs};
        });
        CallbackInfo callback = createCallbackInfo("callback");

        FilterResult result = filter.filter("test", callback, new Object[]{5, 10}, new HashMap<>());
        assertEquals(FilterAction.MODIFY, result.getAction());
        assertEquals(10, result.getModifiedArgs()[0]);
        assertEquals(20, result.getModifiedArgs()[1]);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test filter skips when modifier raises exception")
    void testParamModifyFilterModifierExceptionSkips() {
        ParamModifyFilter filter = new ParamModifyFilter((args, kwargs) -> {
            throw new RuntimeException("Modifier failed");
        });
        CallbackInfo callback = createCallbackInfo("callback");

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("value", 5);
        FilterResult result = filter.filter("test", callback, new Object[0], kwargs);
        assertEquals(FilterAction.SKIP, result.getAction());
        assertTrue(result.getReason().toLowerCase().contains("modification failed"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test filter continues when condition is true")
    void testConditionalFilterConditionTrueContinues() {
        ConditionalFilter filter = new ConditionalFilter((event, cb, args, kwargs) -> true);
        CallbackInfo callback = createCallbackInfo("callback");

        FilterResult result = filter.filter("test", callback, new Object[0], new HashMap<>());
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test filter skips when condition is false")
    void testConditionalFilterConditionFalseSkips() {
        ConditionalFilter filter = new ConditionalFilter((event, cb, args, kwargs) -> false);
        CallbackInfo callback = createCallbackInfo("callback");

        FilterResult result = filter.filter("test", callback, new Object[0], new HashMap<>());
        assertEquals(FilterAction.SKIP, result.getAction());
        assertTrue(result.getReason().contains("Condition not satisfied"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test condition receives event and arguments")
    void testConditionalFilterConditionUsesEventAndArgs() {
        String[] receivedEvent = new String[1];
        Object[][] receivedArgs = new Object[1][];
        Map<String, Object>[] receivedKwargs = new Map[1];

        ConditionalFilter filter = new ConditionalFilter((event, cb, args, kwargs) -> {
            receivedEvent[0] = event;
            receivedArgs[0] = args;
            receivedKwargs[0] = kwargs;
            return true;
        });
        CallbackInfo callback = createCallbackInfo("my_callback");

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("key", "value");
        filter.filter("my_event", callback, new Object[]{"arg1"}, kwargs);

        assertEquals("my_event", receivedEvent[0]);
        assertArrayEquals(new Object[]{"arg1"}, receivedArgs[0]);
        assertEquals(Map.of("key", "value"), receivedKwargs[0]);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test filter can use custom action when condition is false")
    void testConditionalFilterCustomActionOnFalse() {
        ConditionalFilter filter = new ConditionalFilter(
                (event, cb, args, kwargs) -> false,
                FilterAction.STOP,
                "CustomConditional"
        );
        CallbackInfo callback = createCallbackInfo("callback");

        FilterResult result = filter.filter("test", callback, new Object[0], new HashMap<>());
        assertEquals(FilterAction.STOP, result.getAction());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test filter skips when condition raises exception")
    void testConditionalFilterConditionExceptionSkips() {
        ConditionalFilter filter = new ConditionalFilter((event, cb, args, kwargs) -> {
            throw new RuntimeException("Condition failed");
        });
        CallbackInfo callback = createCallbackInfo("callback");

        FilterResult result = filter.filter("test", callback, new Object[0], new HashMap<>());
        assertEquals(FilterAction.SKIP, result.getAction());
        assertTrue(result.getReason().toLowerCase().contains("evaluation failed"));
    }
}
