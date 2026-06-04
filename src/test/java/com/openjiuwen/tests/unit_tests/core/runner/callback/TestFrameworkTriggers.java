/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.runner.callback;

import com.openjiuwen.core.runner.callback.CallbackFramework;
import com.openjiuwen.core.runner.callback.CallbackInfo;
import com.openjiuwen.core.runner.callback.ChainAction;
import com.openjiuwen.core.runner.callback.ChainResult;
import com.openjiuwen.core.runner.callback.ConditionalFilter;
import com.openjiuwen.core.runner.callback.EventFilter;
import com.openjiuwen.core.runner.callback.FilterAction;
import com.openjiuwen.core.runner.callback.FilterResult;
import com.openjiuwen.core.runner.callback.ParamModifyFilter;
import com.openjiuwen.core.runner.callback.RateLimitFilter;
import com.openjiuwen.core.runner.callback.ValidationFilter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Framework triggers test cases.
 *
 * <p>Mirrors Python's {@code test_framework_triggers.py} in
 * {@code tests/unit_tests/core/runner/callback/test_framework_triggers}.</p>
 */
@DisplayName("Framework Triggers Tests")
class TestFrameworkTriggers {

    private CallbackFramework framework;

    @BeforeEach
    void setUp() {
        framework = new CallbackFramework(true, false);
    }

    private static Object[] argsFrom(Map<String, Object> kwargs) {
        Object raw = kwargs.get("_args");
        return raw instanceof Object[] args ? args : new Object[0];
    }

    @Test
    @DisplayName("test_trigger_single_callback")
    void testTriggerSingleCallback() {
        framework.on("event", kwargs -> "got: " + kwargs.get("message"), "callback");

        List<Object> results = framework.trigger("event", new HashMap<>(Map.of("message", "hello")));

        assertEquals(List.of("got: hello"), results);
    }

    @Test
    @DisplayName("test_trigger_multiple_callbacks")
    void testTriggerMultipleCallbacks() {
        framework.register("event", kwargs -> "high", 10, "high");
        framework.register("event", kwargs -> "low", 1, "low");

        assertEquals(List.of("high", "low"), framework.trigger("event"));
    }

    @Test
    @DisplayName("test_trigger_nonexistent_event")
    void testTriggerNonexistentEvent() {
        assertTrue(framework.trigger("nonexistent").isEmpty());
    }

    @Test
    @DisplayName("test_trigger_respects_enabled")
    void testTriggerRespectsEnabled() {
        int[] callCount = {0};
        CallbackInfo info = framework.on("event", kwargs -> {
            callCount[0]++;
            return null;
        }, "callback");

        framework.trigger("event");
        info.setEnabled(false);
        framework.trigger("event");

        assertEquals(1, callCount[0]);
    }

    @Test
    @DisplayName("test_trigger_once_callback")
    void testTriggerOnceCallback() {
        int[] callCount = {0};
        framework.register("event", kwargs -> ++callCount[0], 0, true, "default", null,
                null, null, null, 0, 0.0, null, "once_callback");

        List<Object> result1 = framework.trigger("event");
        List<Object> result2 = framework.trigger("event");

        assertEquals(List.of(1), result1);
        assertTrue(result2.isEmpty());
        assertEquals(1, callCount[0]);
    }

    @Test
    @DisplayName("test_trigger_passes_args_and_kwargs")
    void testTriggerPassesArgsAndKwargs() {
        Map<String, Object> received = new HashMap<>();
        framework.on("event", kwargs -> {
            received.put("args", argsFrom(kwargs));
            received.put("key1", kwargs.get("key1"));
            received.put("key2", kwargs.get("key2"));
            return null;
        }, "callback");

        framework.trigger("event", new Object[]{"pos1", "pos2"},
                new HashMap<>(Map.of("key1", "val1", "key2", "val2")));

        assertArrayEquals(new Object[]{"pos1", "pos2"}, (Object[]) received.get("args"));
        assertEquals("val1", received.get("key1"));
        assertEquals("val2", received.get("key2"));
    }

    @Test
    @DisplayName("test_trigger_applies_filters")
    void testTriggerAppliesFilters() {
        RateLimitFilter rateLimiter = new RateLimitFilter(2, 1.0);
        framework.addFilter("event", rateLimiter);
        int[] callCount = {0};
        framework.on("event", kwargs -> {
            callCount[0]++;
            return null;
        }, "callback");

        framework.trigger("event");
        framework.trigger("event");
        framework.trigger("event");

        assertEquals(2, callCount[0]);
    }

    @Test
    @DisplayName("test_trigger_callback_exception_continues")
    void testTriggerCallbackExceptionContinues() {
        List<String> results = new ArrayList<>();
        framework.register("event", kwargs -> {
            throw new IllegalArgumentException("Error!");
        }, 10, "failing");
        framework.register("event", kwargs -> {
            results.add("success");
            return "success";
        }, 1, "succeeding");

        List<Object> triggerResults = framework.trigger("event");

        assertEquals(List.of("success"), results);
        assertEquals(List.of("success"), triggerResults);
    }

    @Test
    @DisplayName("test_trigger_delayed_waits")
    void testTriggerDelayedWaits() throws Exception {
        framework.on("event", kwargs -> "done", "callback");
        long start = System.nanoTime();

        ScheduledFuture<List<Object>> future = framework.triggerDelayed("event", 0.05, new Object[0], new HashMap<>());
        List<Object> results = future.get();
        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;

        assertTrue(elapsedMs >= 40);
        assertEquals(List.of("done"), results);
    }

    @Test
    @DisplayName("test_trigger_chain_basic")
    void testTriggerChainBasic() {
        framework.register("process", kwargs -> {
            Map<String, Object> data = new HashMap<>((Map<String, Object>) kwargs.get("data"));
            data.put("step1", true);
            return ChainResult.builder().action(ChainAction.CONTINUE).result(data).build();
        }, 20, "step1");
        framework.register("process", kwargs -> {
            Map<String, Object> data = new HashMap<>((Map<String, Object>) kwargs.get("_last_result"));
            data.put("step2", true);
            return ChainResult.builder().action(ChainAction.CONTINUE).result(data).build();
        }, 10, "step2");

        ChainResult result = framework.triggerChain("process", new Object[0], new HashMap<>(Map.of("data", Map.of("id", 1))));

        assertEquals(ChainAction.CONTINUE, result.getAction());
        assertTrue(result.getContext().isCompleted());
        assertEquals(true, ((Map<?, ?>) result.getResult()).get("step1"));
        assertEquals(true, ((Map<?, ?>) result.getResult()).get("step2"));
    }

    @Test
    @DisplayName("test_trigger_chain_data_flows")
    void testTriggerChainDataFlows() {
        framework.register("chain", kwargs -> ChainResult.builder()
                .action(ChainAction.CONTINUE).result((int) kwargs.get("value") * 2).build(), 20, "multiply");
        framework.register("chain", kwargs -> ChainResult.builder()
                .action(ChainAction.CONTINUE).result((int) kwargs.get("_last_result") + 10).build(), 10, "add");

        ChainResult result = framework.triggerChain("chain", new Object[0], new HashMap<>(Map.of("value", 5)));

        assertEquals(20, result.getResult());
    }

    @Test
    @DisplayName("test_trigger_parallel_concurrent")
    void testTriggerParallelConcurrent() {
        framework.on("parallel", kwargs -> sleepAndReturn("task1", 100), "task1");
        framework.on("parallel", kwargs -> sleepAndReturn("task2", 100), "task2");
        framework.on("parallel", kwargs -> sleepAndReturn("task3", 100), "task3");

        long start = System.nanoTime();
        List<Object> results = framework.triggerParallel("parallel", new Object[0], new HashMap<>());
        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;

        assertTrue(elapsedMs < 500);
        assertEquals(3, results.size());
    }

    @Test
    @DisplayName("test_trigger_parallel_handles_errors")
    void testTriggerParallelHandlesErrors() {
        framework.on("parallel", kwargs -> "success", "success");
        framework.on("parallel", kwargs -> {
            throw new IllegalArgumentException("Error!");
        }, "failure");
        framework.on("parallel", kwargs -> "another", "another_success");

        List<Object> results = framework.triggerParallel("parallel", new Object[0], new HashMap<>());

        assertEquals(2, results.size());
        assertTrue(results.containsAll(List.of("success", "another")));
    }

    @Test
    @DisplayName("test_trigger_parallel_respects_filters")
    void testTriggerParallelRespectsFilters() {
        ValidationFilter validation = new ValidationFilter(kwargs -> (Boolean) kwargs.getOrDefault("enabled", true));
        framework.addFilter("event", validation);
        AtomicInteger callCount = new AtomicInteger();
        framework.on("event", kwargs -> {
            callCount.incrementAndGet();
            return "cb1";
        }, "callback1");
        framework.on("event", kwargs -> {
            callCount.incrementAndGet();
            return "cb2";
        }, "callback2");

        framework.triggerParallel("event", new Object[0], new HashMap<>(Map.of("enabled", false)));
        framework.triggerParallel("event", new Object[0], new HashMap<>(Map.of("enabled", true)));

        assertEquals(2, callCount.get());
    }

    @Test
    @DisplayName("test_trigger_until_finds_match")
    void testTriggerUntilFindsMatch() {
        framework.register("search", kwargs -> 5, 10, "search1");
        framework.register("search", kwargs -> 15, 5, "search2");
        framework.register("search", kwargs -> 25, 1, "search3");

        Object result = framework.triggerUntil("search", x -> (int) x > 10, new Object[0], new HashMap<>());

        assertEquals(15, result);
    }

    @Test
    @DisplayName("test_trigger_until_no_match")
    void testTriggerUntilNoMatch() {
        framework.on("search", kwargs -> 5, "callback");

        assertEquals(null, framework.triggerUntil("search", x -> (int) x > 100, new Object[0], new HashMap<>()));
    }

    @Test
    @DisplayName("test_trigger_until_handles_exception")
    void testTriggerUntilHandlesException() {
        framework.register("search", kwargs -> {
            throw new IllegalArgumentException("Error!");
        }, 10, "failing");
        framework.register("search", kwargs -> 100, 5, "success");

        Object result = framework.triggerUntil("search", x -> (int) x > 50, new Object[0], new HashMap<>());

        assertEquals(100, result);
    }

    @Test
    @DisplayName("test_trigger_with_timeout_completes")
    void testTriggerWithTimeoutCompletes() {
        framework.on("event", kwargs -> sleepAndReturn("done", 10), "fast_callback");

        assertEquals(List.of("done"), framework.triggerWithTimeout("event", 1.0, new Object[0], new HashMap<>()));
    }

    @Test
    @DisplayName("test_trigger_with_timeout_exceeds")
    void testTriggerWithTimeoutExceeds() {
        framework.on("event", kwargs -> sleepAndReturn("done", 300), "slow_callback");

        assertTrue(framework.triggerWithTimeout("event", 0.02, new Object[0], new HashMap<>()).isEmpty());
    }

    @Test
    @DisplayName("test_trigger_skip_filter_logs_debug")
    void testTriggerSkipFilterLogsDebug() {
        framework.addFilter("event", new ValidationFilter(kwargs -> false));
        framework.on("event", kwargs -> "result", "callback");

        assertTrue(framework.trigger("event").isEmpty());
    }

    @Test
    @DisplayName("test_trigger_callback_error_logs_error")
    void testTriggerCallbackErrorLogsError() {
        framework.on("event", kwargs -> {
            throw new IllegalArgumentException("Test error!");
        }, "failing_callback");

        assertTrue(framework.trigger("event").isEmpty());
        assertEquals(1, framework.getMetrics().get("event:failing_callback").get("error_count"));
    }

    @Test
    @DisplayName("test_trigger_stop_filter_stops_processing")
    void testTriggerStopFilterStopsProcessing() {
        ConditionalFilter stopFilter = new ConditionalFilter((event, callback, args, kwargs) -> false,
                FilterAction.STOP, "StopFilter");
        framework.addFilter("event", stopFilter);
        int[] callCount = {0};
        framework.on("event", kwargs -> {
            callCount[0]++;
            return "result";
        }, "callback");

        List<Object> results = framework.trigger("event");

        assertEquals(0, callCount[0]);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("test_circuit_breaker_records_success")
    void testCircuitBreakerRecordsSuccess() {
        CallbackInfo callback = framework.on("event", kwargs -> "success", "callback");
        framework.addCircuitBreaker("event", callback, 3, 60.0);

        List<Object> results = framework.trigger("event");

        assertEquals(List.of("success"), results);
        assertEquals(0, framework.getCircuitBreakers().get("event:callback").getFailures().get("event:callback"));
    }

    @Test
    @DisplayName("test_trigger_parallel_no_callbacks")
    void testTriggerParallelNoCallbacks() {
        assertTrue(framework.triggerParallel("nonexistent_event", new Object[0], new HashMap<>()).isEmpty());
    }

    @Test
    @DisplayName("test_trigger_parallel_disabled_callback")
    void testTriggerParallelDisabledCallback() {
        CallbackInfo info = framework.on("event", kwargs -> "result", "callback");
        info.setEnabled(false);

        assertTrue(framework.triggerParallel("event", new Object[0], new HashMap<>()).isEmpty());
    }

    @Test
    @DisplayName("test_trigger_parallel_with_stop_filter")
    void testTriggerParallelWithStopFilter() {
        framework.addFilter("event", new ConditionalFilter((event, callback, args, kwargs) -> false,
                FilterAction.STOP, "StopFilter"));
        framework.on("event", kwargs -> "result", "callback");

        assertTrue(framework.triggerParallel("event", new Object[0], new HashMap<>()).isEmpty());
    }

    @Test
    @DisplayName("test_trigger_parallel_with_timeout")
    void testTriggerParallelWithTimeout() {
        framework.register("event", kwargs -> sleepAndReturn("too slow", 200), 0, false, "default", null,
                null, null, null, 0, 0.0, 0.02, "slow_callback");
        framework.on("event", kwargs -> "fast", "fast_callback");

        List<Object> results = framework.triggerParallel("event", new Object[0], new HashMap<>());

        assertTrue(results.contains("fast"));
        assertFalse(results.contains("too slow"));
    }

    @Test
    @DisplayName("test_trigger_parallel_once_callback")
    void testTriggerParallelOnceCallback() {
        framework.register("event", kwargs -> "once", 0, true, "default", null,
                null, null, null, 0, 0.0, null, "once_callback");

        List<Object> results1 = framework.triggerParallel("event", new Object[0], new HashMap<>());
        List<Object> results2 = framework.triggerParallel("event", new Object[0], new HashMap<>());

        assertEquals(List.of("once"), results1);
        assertTrue(results2.isEmpty());
    }

    @Test
    @DisplayName("test_trigger_parallel_exception_logging")
    void testTriggerParallelExceptionLogging() {
        framework.on("event", kwargs -> {
            throw new IllegalArgumentException("Test error");
        }, "failing_callback");

        assertTrue(framework.triggerParallel("event", new Object[0], new HashMap<>()).isEmpty());
    }

    @Test
    @DisplayName("test_trigger_parallel_skip_filter_logs_debug")
    void testTriggerParallelSkipFilterLogsDebug() {
        framework.addFilter("event", new ValidationFilter(kwargs -> false));
        framework.on("event", kwargs -> "result", "callback");

        assertTrue(framework.triggerParallel("event", new Object[0], new HashMap<>()).isEmpty());
    }

    @Test
    @DisplayName("test_trigger_parallel_gather_exception_logging")
    void testTriggerParallelGatherExceptionLogging() {
        framework.on("event", kwargs -> "success", "callback");

        assertEquals(List.of("success"), framework.triggerParallel("event", new Object[0], new HashMap<>()));
    }

    @Test
    @DisplayName("test_trigger_until_no_callbacks")
    void testTriggerUntilNoCallbacks() {
        assertEquals(null, framework.triggerUntil("nonexistent", x -> true, new Object[0], new HashMap<>()));
    }

    @Test
    @DisplayName("test_trigger_until_disabled_callback")
    void testTriggerUntilDisabledCallback() {
        CallbackInfo info = framework.on("event", kwargs -> 100, "callback");
        info.setEnabled(false);

        assertEquals(null, framework.triggerUntil("event", x -> (int) x > 50, new Object[0], new HashMap<>()));
    }

    @Test
    @DisplayName("test_trigger_until_stop_filter")
    void testTriggerUntilStopFilter() {
        framework.addFilter("event", new ConditionalFilter((event, callback, args, kwargs) -> false,
                FilterAction.STOP, "StopFilter"));
        framework.on("event", kwargs -> 100, "callback");

        assertEquals(null, framework.triggerUntil("event", x -> (int) x > 50, new Object[0], new HashMap<>()));
    }

    @Test
    @DisplayName("test_trigger_until_skip_filter")
    void testTriggerUntilSkipFilter() {
        framework.addFilter("event", new ValidationFilter(kwargs -> (int) kwargs.get("value") >= 0));
        framework.register("event", kwargs -> 100, 10, "skipped_callback");
        framework.register("event", kwargs -> 200, 5, "passing_callback");

        Object result = framework.triggerUntil("event", x -> (int) x > 50, new Object[0], new HashMap<>(Map.of("value", -1)));

        assertEquals(null, result);
    }

    @Test
    @DisplayName("test_trigger_until_condition_satisfied_logs")
    void testTriggerUntilConditionSatisfiedLogs() {
        framework.on("event", kwargs -> 100, "callback");

        Object result = framework.triggerUntil("event", x -> (int) x > 50, new Object[0], new HashMap<>());

        assertEquals(100, result);
    }

    @Test
    @DisplayName("test_trigger_until_once_callback_condition_met")
    void testTriggerUntilOnceCallbackConditionMet() {
        CallbackInfo info = framework.register("event", kwargs -> 100, 0, true, "default", null,
                null, null, null, 0, 0.0, null, "once_callback");

        Object result = framework.triggerUntil("event", x -> (int) x > 50, new Object[0], new HashMap<>());

        assertEquals(100, result);
        assertFalse(info.isEnabled());
    }

    @Test
    @DisplayName("test_trigger_until_once_callback_condition_not_met")
    void testTriggerUntilOnceCallbackConditionNotMet() {
        CallbackInfo info = framework.register("event", kwargs -> 10, 0, true, "default", null,
                null, null, null, 0, 0.0, null, "once_callback");

        Object result = framework.triggerUntil("event", x -> (int) x > 50, new Object[0], new HashMap<>());

        assertEquals(null, result);
        assertFalse(info.isEnabled());
    }

    @Test
    @DisplayName("test_trigger_until_exception_logging")
    void testTriggerUntilExceptionLogging() {
        framework.on("event", kwargs -> {
            throw new IllegalArgumentException("Test error");
        }, "failing_callback");

        assertEquals(null, framework.triggerUntil("event", x -> true, new Object[0], new HashMap<>()));
    }

    @Test
    @DisplayName("test_trigger_with_timeout_logs_warning")
    void testTriggerWithTimeoutLogsWarning() {
        framework.on("event", kwargs -> sleepAndReturn("done", 300), "slow_callback");

        assertTrue(framework.triggerWithTimeout("event", 0.02, new Object[0], new HashMap<>()).isEmpty());
    }

    @Test
    @DisplayName("test_add_filter_to_event")
    void testAddFilterToEvent() {
        framework.addFilter("limited", new RateLimitFilter(1, 1.0));
        int[] callCount = {0};
        framework.on("limited", kwargs -> {
            callCount[0]++;
            return null;
        }, "callback");
        framework.on("unlimited", kwargs -> {
            callCount[0]++;
            return null;
        }, "other");

        framework.trigger("limited");
        framework.trigger("limited");
        framework.trigger("unlimited");
        framework.trigger("unlimited");

        assertEquals(3, callCount[0]);
    }

    @Test
    @DisplayName("test_add_global_filter")
    void testAddGlobalFilter() {
        framework.addGlobalFilter(new RateLimitFilter(1, 1.0));
        int[] callCount = {0};
        framework.on("event1", kwargs -> {
            callCount[0]++;
            return null;
        }, "cb1");
        framework.on("event2", kwargs -> {
            callCount[0]++;
            return null;
        }, "cb2");

        framework.trigger("event1");
        framework.trigger("event1");
        framework.trigger("event2");
        framework.trigger("event2");

        assertEquals(2, callCount[0]);
    }

    @Test
    @DisplayName("test_add_circuit_breaker")
    void testAddCircuitBreaker() {
        int[] callCount = {0};
        CallbackInfo failing = framework.on("event", kwargs -> {
            callCount[0]++;
            throw new IllegalArgumentException("Error!");
        }, "failing_callback");
        framework.addCircuitBreaker("event", failing, 2, 60.0);

        framework.trigger("event");
        framework.trigger("event");
        framework.trigger("event");

        assertTrue(framework.getCircuitBreakers().containsKey("event:failing_callback"));
        assertEquals(2, callCount[0]);
    }

    @Test
    @DisplayName("test_modify_filter_changes_args")
    void testModifyFilterChangesArgs() {
        framework.addFilter("event", new ParamModifyFilter((args, kwargs) ->
                new Object[]{new Object[]{(int) args[0] * 2, (int) args[1] * 2}, kwargs}));
        List<Object[]> received = new ArrayList<>();
        framework.on("event", kwargs -> {
            received.add(argsFrom(kwargs));
            return null;
        }, "callback");

        framework.trigger("event", new Object[]{5, 10}, new HashMap<>());

        assertArrayEquals(new Object[]{10, 20}, received.get(0));
    }

    @Test
    @DisplayName("test_modify_filter_changes_kwargs")
    void testModifyFilterChangesKwargs() {
        framework.addFilter("event", new ParamModifyFilter((args, kwargs) -> {
            Map<String, Object> modified = new HashMap<>();
            kwargs.forEach((key, value) -> modified.put(key, value instanceof Integer ? (Integer) value * 2 : value));
            return new Object[]{args, modified};
        }));
        List<Map<String, Object>> received = new ArrayList<>();
        framework.on("event", kwargs -> {
            received.add(new HashMap<>(kwargs));
            return null;
        }, "callback");

        framework.trigger("event", new Object[0], new HashMap<>(Map.of("value", 5, "count", 10)));

        assertEquals(10, received.get(0).get("value"));
        assertEquals(20, received.get(0).get("count"));
    }

    @Test
    @DisplayName("test_modify_filter_only_args")
    void testModifyFilterOnlyArgs() {
        framework.addFilter("event", new EventFilter() {
            @Override
            public FilterResult filter(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs) {
                return FilterResult.modifyResult(new Object[]{100}, null);
            }
        });
        List<Object[]> receivedArgs = new ArrayList<>();
        List<Map<String, Object>> receivedKwargs = new ArrayList<>();
        framework.on("event", kwargs -> {
            receivedArgs.add(argsFrom(kwargs));
            receivedKwargs.add(new HashMap<>(kwargs));
            return null;
        }, "callback");

        framework.trigger("event", new Object[]{1}, new HashMap<>(Map.of("key", "value")));

        assertArrayEquals(new Object[]{100}, receivedArgs.get(0));
        assertEquals("value", receivedKwargs.get(0).get("key"));
    }

    @Test
    @DisplayName("test_modify_filter_only_kwargs")
    void testModifyFilterOnlyKwargs() {
        framework.addFilter("event", new EventFilter() {
            @Override
            public FilterResult filter(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs) {
                return FilterResult.modifyResult(null, new HashMap<>(Map.of("new_key", "new_value")));
            }
        });
        List<Object[]> receivedArgs = new ArrayList<>();
        List<Map<String, Object>> receivedKwargs = new ArrayList<>();
        framework.on("event", kwargs -> {
            receivedArgs.add(argsFrom(kwargs));
            receivedKwargs.add(new HashMap<>(kwargs));
            return null;
        }, "callback");

        framework.trigger("event", new Object[]{1, 2, 3}, new HashMap<>());

        assertArrayEquals(new Object[]{1, 2, 3}, receivedArgs.get(0));
        assertEquals("new_value", receivedKwargs.get(0).get("new_key"));
    }

    private static String sleepAndReturn(String value, long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return value;
    }
}
