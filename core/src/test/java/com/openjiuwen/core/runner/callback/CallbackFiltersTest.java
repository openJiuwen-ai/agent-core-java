// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
package com.openjiuwen.core.runner.callback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for callback framework filters.
 *
 * <p>Mirrors Python's {@code test_filters.py} in
 * {@code tests/unit_tests/core/runner/callback/test_filters.py}.</p>
 */
@DisplayName("Callback Filters Tests")
class CallbackFiltersTest {

    @Test
    @DisplayName("EventFilter default continues")
    void testEventFilterDefaultContinues() {
        EventFilter filterObj = new EventFilter();
        FilterResult result = filterObj.filter("test_event", namedCallback("dummy_callback"), new Object[0], Map.of());
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("EventFilter default name")
    void testEventFilterDefaultName() {
        EventFilter filterObj = new EventFilter();
        assertEquals("EventFilter", filterObj.getName());
    }

    @Test
    @DisplayName("EventFilter custom name")
    void testEventFilterCustomName() {
        EventFilter filterObj = new EventFilter("CustomFilter");
        assertEquals("CustomFilter", filterObj.getName());
    }

    @Test
    @DisplayName("RateLimitFilter allows within limit")
    void testRateLimitFilterAllowsWithinLimit() {
        RateLimitFilter filterObj = new RateLimitFilter(3, 2.0);
        Function<Map<String, Object>, Object> callback = namedCallback("callback");

        for (int index = 0; index < 3; index++) {
            FilterResult result = filterObj.filter("test", callback, new Object[0], Map.of());
            assertEquals(FilterAction.CONTINUE, result.getAction(), "Call " + (index + 1) + " should be allowed");
        }
    }

    @Test
    @DisplayName("RateLimitFilter blocks exceeding limit")
    void testRateLimitFilterBlocksExceedingLimit() {
        RateLimitFilter filterObj = new RateLimitFilter(2, 2.0);
        Function<Map<String, Object>, Object> callback = namedCallback("callback");

        filterObj.filter("test", callback, new Object[0], Map.of());
        filterObj.filter("test", callback, new Object[0], Map.of());

        FilterResult result = filterObj.filter("test", callback, new Object[0], Map.of());
        assertEquals(FilterAction.SKIP, result.getAction());
        assertTrue(result.getReason().contains("Rate limit exceeded"));
    }

    @Test
    @DisplayName("RateLimitFilter different callbacks tracked separately")
    void testRateLimitFilterDifferentCallbacksTrackedSeparately() {
        RateLimitFilter filterObj = new RateLimitFilter(2, 2.0);
        Function<Map<String, Object>, Object> callback1 = namedCallback("callback1");
        Function<Map<String, Object>, Object> callback2 = namedCallback("callback2");

        for (int index = 0; index < 2; index++) {
            assertEquals(FilterAction.CONTINUE, filterObj.filter("test", callback1, new Object[0], Map.of()).getAction());
        }
        for (int index = 0; index < 2; index++) {
            assertEquals(FilterAction.CONTINUE, filterObj.filter("test", callback2, new Object[0], Map.of()).getAction());
        }
    }

    @Test
    @DisplayName("RateLimitFilter window expiration")
    void testRateLimitFilterWindowExpiration() throws InterruptedException {
        RateLimitFilter filterObj = new RateLimitFilter(2, 0.1);
        Function<Map<String, Object>, Object> callback = namedCallback("callback");

        filterObj.filter("test", callback, new Object[0], Map.of());
        filterObj.filter("test", callback, new Object[0], Map.of());
        Thread.sleep(150L);

        FilterResult result = filterObj.filter("test", callback, new Object[0], Map.of());
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("RateLimitFilter custom name")
    void testRateLimitFilterCustomName() {
        RateLimitFilter filterObj = new RateLimitFilter(10, 1.0, "CustomRateLimit");
        assertEquals("CustomRateLimit", filterObj.getName());
    }

    @Test
    @DisplayName("CircuitBreakerFilter closed state allows calls")
    void testCircuitBreakerFilterClosedStateAllowsCalls() {
        CircuitBreakerFilter filterObj = new CircuitBreakerFilter(3, 1.0);
        FilterResult result = filterObj.filter("test", namedCallback("callback"), new Object[0], Map.of());
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("CircuitBreakerFilter opens after threshold")
    void testCircuitBreakerFilterOpensAfterThreshold() {
        CircuitBreakerFilter filterObj = new CircuitBreakerFilter(3, 1.0);
        Function<Map<String, Object>, Object> callback = namedCallback("callback");

        for (int index = 0; index < 3; index++) {
            filterObj.recordFailure("test", callback);
        }

        FilterResult result = filterObj.filter("test", callback, new Object[0], Map.of());
        assertEquals(FilterAction.SKIP, result.getAction());
        assertTrue(result.getReason().contains("Circuit breaker open"));
    }

    @Test
    @DisplayName("CircuitBreakerFilter success resets failures")
    void testCircuitBreakerFilterSuccessResetsFailures() {
        CircuitBreakerFilter filterObj = new CircuitBreakerFilter(3, 1.0);
        Function<Map<String, Object>, Object> callback = namedCallback("callback");

        filterObj.recordFailure("test", callback);
        filterObj.recordFailure("test", callback);
        filterObj.recordSuccess("test", callback);
        filterObj.recordFailure("test", callback);
        filterObj.recordFailure("test", callback);

        FilterResult result = filterObj.filter("test", callback, new Object[0], Map.of());
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("CircuitBreakerFilter timeout allows retry")
    void testCircuitBreakerFilterTimeoutAllowsRetry() throws InterruptedException {
        CircuitBreakerFilter filterObj = new CircuitBreakerFilter(2, 0.1);
        Function<Map<String, Object>, Object> callback = namedCallback("callback");

        filterObj.recordFailure("test", callback);
        filterObj.recordFailure("test", callback);
        assertEquals(FilterAction.SKIP, filterObj.filter("test", callback, new Object[0], Map.of()).getAction());

        Thread.sleep(150L);

        FilterResult result = filterObj.filter("test", callback, new Object[0], Map.of());
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("CircuitBreakerFilter different callbacks tracked separately")
    void testCircuitBreakerFilterDifferentCallbacksTrackedSeparately() {
        CircuitBreakerFilter filterObj = new CircuitBreakerFilter(2, 1.0);
        Function<Map<String, Object>, Object> callback1 = namedCallback("callback1");
        Function<Map<String, Object>, Object> callback2 = namedCallback("callback2");

        filterObj.recordFailure("test", callback1);
        filterObj.recordFailure("test", callback1);

        assertEquals(FilterAction.SKIP, filterObj.filter("test", callback1, new Object[0], Map.of()).getAction());
        assertEquals(FilterAction.CONTINUE, filterObj.filter("test", callback2, new Object[0], Map.of()).getAction());
    }

    @Test
    @DisplayName("CircuitBreakerFilter custom name")
    void testCircuitBreakerFilterCustomName() {
        CircuitBreakerFilter filterObj = new CircuitBreakerFilter(5, 60.0, "CustomBreaker");
        assertEquals("CustomBreaker", filterObj.getName());
    }

    @Test
    @DisplayName("ValidationFilter valid args continue")
    void testValidationFilterValidArgsContinue() {
        ValidationFilter filterObj = new ValidationFilter((args, kwargs) -> (Integer) args[0] > 0);
        FilterResult result = filterObj.filter("test", namedCallback("callback"), new Object[]{10}, Map.of());
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("ValidationFilter invalid args skip")
    void testValidationFilterInvalidArgsSkip() {
        ValidationFilter filterObj = new ValidationFilter((args, kwargs) -> (Integer) args[0] > 0);
        FilterResult result = filterObj.filter("test", namedCallback("callback"), new Object[]{-5}, Map.of());
        assertEquals(FilterAction.SKIP, result.getAction());
        assertTrue(result.getReason().toLowerCase().contains("validation failed"));
    }

    @Test
    @DisplayName("ValidationFilter kwargs validation")
    void testValidationFilterKwargsValidation() {
        ValidationFilter filterObj = new ValidationFilter((args, kwargs) -> ((Integer) kwargs.getOrDefault("value", 0)) > 0);

        assertEquals(FilterAction.CONTINUE, filterObj.filter("test", namedCallback("callback"), new Object[0], Map.of("value", 10)).getAction());
        assertEquals(FilterAction.SKIP, filterObj.filter("test", namedCallback("callback"), new Object[0], Map.of("value", -5)).getAction());
    }

    @Test
    @DisplayName("ValidationFilter validator exception skips")
    void testValidationFilterValidatorExceptionSkips() {
        ValidationFilter filterObj = new ValidationFilter((args, kwargs) -> {
            throw new IllegalArgumentException("Validation error");
        });
        FilterResult result = filterObj.filter("test", namedCallback("callback"), new Object[]{"arg"}, Map.of());
        assertEquals(FilterAction.SKIP, result.getAction());
        assertTrue(result.getReason().contains("Validation error"));
    }

    @Test
    @DisplayName("LoggingFilter always continues")
    void testLoggingFilterAlwaysContinues() {
        LoggingFilter filterObj = new LoggingFilter();
        FilterResult result = filterObj.filter("test", namedCallback("callback"), new Object[]{"arg1"}, Map.of("key", "value"));
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("LoggingFilter logs execution info")
    void testLoggingFilterLogsExecutionInfo() {
        Logger logger = Logger.getLogger("test_logger");
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.INFO);
        TestLogHandler handler = new TestLogHandler();
        logger.addHandler(handler);
        try {
            LoggingFilter filterObj = new LoggingFilter(logger);
            filterObj.filter("test_event", namedCallback("my_callback"), new Object[]{"arg1"}, Map.of("key", "value"));

            String combined = String.join("\n", handler.messages());
            assertTrue(combined.contains("test_event"));
            assertTrue(combined.contains("my_callback"));
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    @DisplayName("LoggingFilter custom logger")
    void testLoggingFilterCustomLogger() {
        Logger customLogger = Logger.getLogger("custom");
        LoggingFilter filterObj = new LoggingFilter(customLogger);
        assertSame(customLogger, filterObj.getLogger());
    }

    @Test
    @DisplayName("LoggingFilter default logger")
    void testLoggingFilterDefaultLogger() {
        LoggingFilter filterObj = new LoggingFilter();
        assertNotNull(filterObj.getLogger());
    }

    @Test
    @DisplayName("AuthFilter authorized user continues")
    void testAuthFilterAuthorizedUserContinues() {
        AuthFilter filterObj = new AuthFilter("admin");
        FilterResult result = filterObj.filter("test", namedCallback("callback"), new Object[0], Map.of("user_role", "admin"));
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("AuthFilter unauthorized user skips")
    void testAuthFilterUnauthorizedUserSkips() {
        AuthFilter filterObj = new AuthFilter("admin");
        FilterResult result = filterObj.filter("test", namedCallback("callback"), new Object[0], Map.of("user_role", "guest"));
        assertEquals(FilterAction.SKIP, result.getAction());
        assertTrue(result.getReason().contains("Unauthorized"));
        assertTrue(result.getReason().contains("admin"));
        assertTrue(result.getReason().contains("guest"));
    }

    @Test
    @DisplayName("AuthFilter missing role defaults to guest")
    void testAuthFilterMissingRoleDefaultsToGuest() {
        AuthFilter filterObj = new AuthFilter("admin");
        FilterResult result = filterObj.filter("test", namedCallback("callback"), new Object[0], Map.of());
        assertEquals(FilterAction.SKIP, result.getAction());
    }

    @Test
    @DisplayName("ParamModifyFilter modifies arguments")
    void testParamModifyFilterModifiesArguments() {
        ParamModifyFilter filterObj = new ParamModifyFilter((args, kwargs) -> {
            Map<String, Object> newKwargs = new HashMap<>();
            for (Map.Entry<String, Object> entry : kwargs.entrySet()) {
                Object value = entry.getValue();
                newKwargs.put(entry.getKey(), value instanceof Integer integerValue ? integerValue * 2 : value);
            }
            return new ParamModifyFilter.Modification(args, newKwargs);
        });

        FilterResult result = filterObj.filter("test", namedCallback("callback"), new Object[0], Map.of("value", 5));
        assertEquals(FilterAction.MODIFY, result.getAction());
        assertEquals(Map.of("value", 10), result.getModifiedKwargs());
    }

    @Test
    @DisplayName("ParamModifyFilter modifies positional args")
    void testParamModifyFilterModifiesPositionalArgs() {
        ParamModifyFilter filterObj = new ParamModifyFilter((args, kwargs) -> {
            Object[] newArgs = new Object[args.length];
            for (int index = 0; index < args.length; index++) {
                Object value = args[index];
                newArgs[index] = value instanceof Integer integerValue ? integerValue * 2 : value;
            }
            return new ParamModifyFilter.Modification(newArgs, kwargs);
        });

        FilterResult result = filterObj.filter("test", namedCallback("callback"), new Object[]{5, 10}, Map.of());
        assertEquals(FilterAction.MODIFY, result.getAction());
        assertArrayEquals(new Object[]{10, 20}, result.getModifiedArgs());
    }

    @Test
    @DisplayName("ParamModifyFilter modifier exception skips")
    void testParamModifyFilterModifierExceptionSkips() {
        ParamModifyFilter filterObj = new ParamModifyFilter((args, kwargs) -> {
            throw new RuntimeException("Modifier failed");
        });

        FilterResult result = filterObj.filter("test", namedCallback("callback"), new Object[0], Map.of("value", 5));
        assertEquals(FilterAction.SKIP, result.getAction());
        assertTrue(result.getReason().toLowerCase().contains("modification failed"));
    }

    @Test
    @DisplayName("ConditionalFilter condition true continues")
    void testConditionalFilterConditionTrueContinues() {
        ConditionalFilter filterObj = new ConditionalFilter((event, callback, args, kwargs) -> true);
        FilterResult result = filterObj.filter("test", namedCallback("callback"), new Object[0], Map.of());
        assertEquals(FilterAction.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("ConditionalFilter condition false skips")
    void testConditionalFilterConditionFalseSkips() {
        ConditionalFilter filterObj = new ConditionalFilter((event, callback, args, kwargs) -> false);
        FilterResult result = filterObj.filter("test", namedCallback("callback"), new Object[0], Map.of());
        assertEquals(FilterAction.SKIP, result.getAction());
        assertTrue(result.getReason().contains("Condition not satisfied"));
    }

    @Test
    @DisplayName("ConditionalFilter condition uses event and args")
    void testConditionalFilterConditionUsesEventAndArgs() {
        Map<String, Object> received = new HashMap<>();
        Function<Map<String, Object>, Object> callback = namedCallback("my_callback");
        ConditionalFilter filterObj = new ConditionalFilter((event, actualCallback, args, kwargs) -> {
            received.put("event", event);
            received.put("callback", actualCallback);
            received.put("args", args);
            received.put("kwargs", kwargs);
            return true;
        });

        filterObj.filter("my_event", callback, new Object[]{"arg1"}, Map.of("key", "value"));

        assertEquals("my_event", received.get("event"));
        assertSame(callback, received.get("callback"));
        assertArrayEquals(new Object[]{"arg1"}, (Object[]) received.get("args"));
        assertEquals(Map.of("key", "value"), received.get("kwargs"));
    }

    @Test
    @DisplayName("ConditionalFilter custom action on false")
    void testConditionalFilterCustomActionOnFalse() {
        ConditionalFilter filterObj = new ConditionalFilter(
                (event, callback, args, kwargs) -> false,
                FilterAction.STOP
        );

        FilterResult result = filterObj.filter("test", namedCallback("callback"), new Object[0], Map.of());
        assertEquals(FilterAction.STOP, result.getAction());
    }

    @Test
    @DisplayName("ConditionalFilter condition exception skips")
    void testConditionalFilterConditionExceptionSkips() {
        ConditionalFilter filterObj = new ConditionalFilter((event, callback, args, kwargs) -> {
            throw new RuntimeException("Condition failed");
        });

        FilterResult result = filterObj.filter("test", namedCallback("callback"), new Object[0], Map.of());
        assertEquals(FilterAction.SKIP, result.getAction());
        assertTrue(result.getReason().toLowerCase().contains("evaluation failed"));
    }

    private static Function<Map<String, Object>, Object> namedCallback(String name) {
        return new NamedCallback(name);
    }

    private record NamedCallback(String name) implements Function<Map<String, Object>, Object> {

        @Override
        public Object apply(Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final class TestLogHandler extends Handler {

        private final List<String> messages = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        public List<String> messages() {
            return messages;
        }
    }
}
