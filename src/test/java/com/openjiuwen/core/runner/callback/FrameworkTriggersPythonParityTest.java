/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.core.runner.callback.test_framework_triggers} in
 * {@code tests/unit_tests/core/runner/callback/test_framework_triggers.py}.</p>
 */
class FrameworkTriggersPythonParityTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "test_trigger_single_callback",
            "test_trigger_multiple_callbacks",
            "test_trigger_nonexistent_event",
            "test_trigger_respects_enabled",
            "test_trigger_once_callback",
            "test_trigger_passes_args_and_kwargs",
            "test_trigger_applies_filters",
            "test_trigger_callback_exception_continues",
            "test_trigger_delayed_waits",
            "test_trigger_chain_basic",
            "test_trigger_chain_data_flows",
            "test_trigger_parallel_concurrent",
            "test_trigger_parallel_handles_errors",
            "test_trigger_parallel_respects_filters",
            "test_trigger_until_finds_match",
            "test_trigger_until_no_match",
            "test_trigger_until_handles_exception",
            "test_trigger_with_timeout_completes",
            "test_trigger_with_timeout_exceeds",
            "test_trigger_skip_filter_logs_debug",
            "test_trigger_callback_error_logs_error",
            "test_trigger_stop_filter_stops_processing",
            "test_circuit_breaker_records_success",
            "test_trigger_parallel_no_callbacks",
            "test_trigger_parallel_disabled_callback",
            "test_trigger_parallel_with_stop_filter",
            "test_trigger_parallel_with_timeout",
            "test_trigger_parallel_once_callback",
            "test_trigger_parallel_exception_logging",
            "test_trigger_parallel_skip_filter_logs_debug",
            "test_trigger_parallel_gather_exception_logging",
            "test_trigger_until_no_callbacks",
            "test_trigger_until_disabled_callback",
            "test_trigger_until_stop_filter",
            "test_trigger_until_skip_filter",
            "test_trigger_until_condition_satisfied_logs",
            "test_trigger_until_once_callback_condition_met",
            "test_trigger_until_once_callback_condition_not_met",
            "test_trigger_until_exception_logging",
            "test_trigger_with_timeout_logs_warning",
            "test_add_filter_to_event",
            "test_add_global_filter",
            "test_add_circuit_breaker",
            "test_modify_filter_changes_args",
            "test_modify_filter_changes_kwargs",
            "test_modify_filter_only_args",
            "test_modify_filter_only_kwargs"
    );

    @TestFactory
    Collection<DynamicTest> pythonFrameworkTriggerCases() {
        return PYTHON_TESTS.stream()
                .map(name -> DynamicTest.dynamicTest(name, () -> runPythonCase(name)))
                .toList();
    }

    private void runPythonCase(String name) throws Exception {
        switch (name) {
            case "test_trigger_single_callback" -> triggerSingleCallback();
            case "test_trigger_multiple_callbacks" -> triggerMultipleCallbacks();
            case "test_trigger_nonexistent_event" -> triggerNonexistentEvent();
            case "test_trigger_respects_enabled" -> triggerRespectsEnabled();
            case "test_trigger_once_callback" -> triggerOnceCallback();
            case "test_trigger_passes_args_and_kwargs" -> triggerPassesArgsAndKwargs();
            case "test_trigger_applies_filters" -> triggerAppliesFilters();
            case "test_trigger_callback_exception_continues" -> triggerCallbackExceptionContinues();
            case "test_trigger_delayed_waits" -> triggerDelayedWaits();
            case "test_trigger_chain_basic" -> triggerChainBasic();
            case "test_trigger_chain_data_flows" -> triggerChainDataFlows();
            case "test_trigger_parallel_concurrent" -> triggerParallelConcurrent();
            case "test_trigger_parallel_handles_errors" -> triggerParallelHandlesErrors();
            case "test_trigger_parallel_respects_filters" -> triggerParallelRespectsFilters();
            case "test_trigger_until_finds_match" -> triggerUntilFindsMatch();
            case "test_trigger_until_no_match" -> triggerUntilNoMatch();
            case "test_trigger_until_handles_exception" -> triggerUntilHandlesException();
            case "test_trigger_with_timeout_completes" -> triggerWithTimeoutCompletes();
            case "test_trigger_with_timeout_exceeds" -> triggerWithTimeoutExceeds();
            case "test_trigger_skip_filter_logs_debug" -> triggerSkipFilterLogsDebug();
            case "test_trigger_callback_error_logs_error" -> triggerCallbackErrorLogsError();
            case "test_trigger_stop_filter_stops_processing" -> triggerStopFilterStopsProcessing();
            case "test_circuit_breaker_records_success" -> circuitBreakerRecordsSuccess();
            case "test_trigger_parallel_no_callbacks" -> triggerParallelNoCallbacks();
            case "test_trigger_parallel_disabled_callback" -> triggerParallelDisabledCallback();
            case "test_trigger_parallel_with_stop_filter" -> triggerParallelWithStopFilter();
            case "test_trigger_parallel_with_timeout" -> triggerParallelWithTimeout();
            case "test_trigger_parallel_once_callback" -> triggerParallelOnceCallback();
            case "test_trigger_parallel_exception_logging" -> triggerParallelExceptionLogging();
            case "test_trigger_parallel_skip_filter_logs_debug" -> triggerParallelSkipFilterLogsDebug();
            case "test_trigger_parallel_gather_exception_logging" -> triggerParallelGatherExceptionLogging();
            case "test_trigger_until_no_callbacks" -> triggerUntilNoCallbacks();
            case "test_trigger_until_disabled_callback" -> triggerUntilDisabledCallback();
            case "test_trigger_until_stop_filter" -> triggerUntilStopFilter();
            case "test_trigger_until_skip_filter" -> triggerUntilSkipFilter();
            case "test_trigger_until_condition_satisfied_logs" -> triggerUntilConditionSatisfiedLogs();
            case "test_trigger_until_once_callback_condition_met" -> triggerUntilOnceCallbackConditionMet();
            case "test_trigger_until_once_callback_condition_not_met" -> triggerUntilOnceCallbackConditionNotMet();
            case "test_trigger_until_exception_logging" -> triggerUntilExceptionLogging();
            case "test_trigger_with_timeout_logs_warning" -> triggerWithTimeoutLogsWarning();
            case "test_add_filter_to_event" -> addFilterToEvent();
            case "test_add_global_filter" -> addGlobalFilter();
            case "test_add_circuit_breaker" -> addCircuitBreaker();
            case "test_modify_filter_changes_args" -> modifyFilterChangesArgs();
            case "test_modify_filter_changes_kwargs" -> modifyFilterChangesKwargs();
            case "test_modify_filter_only_args" -> modifyFilterOnlyArgs();
            case "test_modify_filter_only_kwargs" -> modifyFilterOnlyKwargs();
            default -> throw new IllegalArgumentException("Unhandled Python test: " + name);
        }
    }

    private void triggerSingleCallback() {
        AsyncCallbackFramework framework = framework();
        register(framework, "event", "callback", kwargs -> "got: " + kwargs.get("message"));

        List<Object> results = framework.triggerResults("event", Map.of("message", "hello"));

        assertEquals(List.of("got: hello"), results);
    }

    private void triggerMultipleCallbacks() {
        AsyncCallbackFramework framework = framework();
        register(framework, "event", "high", 10, false, null, kwargs -> "high");
        register(framework, "event", "low", 1, false, null, kwargs -> "low");

        assertEquals(List.of("high", "low"), framework.triggerResults("event"));
    }

    private void triggerNonexistentEvent() {
        assertEquals(List.of(), framework().triggerResults("nonexistent"));
    }

    private void triggerRespectsEnabled() {
        AsyncCallbackFramework framework = framework();
        AtomicInteger callCount = new AtomicInteger();
        register(framework, "event", "callback", kwargs -> {
            callCount.incrementAndGet();
            return null;
        });

        framework.triggerResults("event");
        framework.getCallbacks().get("event").get(0).setEnabled(false);
        framework.triggerResults("event");

        assertEquals(1, callCount.get());
    }

    private void triggerOnceCallback() {
        AsyncCallbackFramework framework = framework();
        AtomicInteger callCount = new AtomicInteger();
        register(framework, "event", "once_callback", 0, true, null, kwargs -> callCount.incrementAndGet());

        List<Object> result1 = framework.triggerResults("event");
        List<Object> result2 = framework.triggerResults("event");

        assertEquals(List.of(1), result1);
        assertEquals(List.of(), result2);
        assertEquals(1, callCount.get());
    }

    private void triggerPassesArgsAndKwargs() {
        AsyncCallbackFramework framework = framework();
        Map<String, Object> received = new LinkedHashMap<>();
        register(framework, "event", "callback", kwargs -> {
            received.put("args", kwargs.get("_args"));
            received.put("kwargs", pythonKwargs(kwargs));
            return null;
        });

        framework.triggerResults("event", new Object[]{"pos1", "pos2"}, Map.of("key1", "val1", "key2", "val2"));

        assertArrayEquals(new Object[]{"pos1", "pos2"}, (Object[]) received.get("args"));
        assertEquals(mapOf("key1", "val1", "key2", "val2", "session", null), received.get("kwargs"));
    }

    private void triggerAppliesFilters() {
        AsyncCallbackFramework framework = framework();
        framework.addFilter("event", new RateLimitFilter(2, 1.0));
        AtomicInteger callCount = new AtomicInteger();
        register(framework, "event", "callback", kwargs -> {
            callCount.incrementAndGet();
            return null;
        });

        framework.triggerResults("event");
        framework.triggerResults("event");
        framework.triggerResults("event");

        assertEquals(2, callCount.get());
    }

    private void triggerCallbackExceptionContinues() {
        AsyncCallbackFramework framework = framework();
        List<String> results = new ArrayList<>();
        register(framework, "event", "failing", 10, false, null, kwargs -> {
            throw new IllegalArgumentException("Error!");
        });
        register(framework, "event", "succeeding", 1, false, null, kwargs -> {
            results.add("success");
            return "success";
        });

        List<Object> actual = framework.triggerResults("event");

        assertEquals(List.of("success"), results);
        assertEquals(List.of("success"), actual);
    }

    private void triggerDelayedWaits() throws Exception {
        AsyncCallbackFramework framework = framework();
        register(framework, "event", "callback", kwargs -> "done");

        long start = System.nanoTime();
        ScheduledFuture<List<Object>> future = framework.triggerDelayed("event", 0.1, new Object[0], Map.of());
        List<Object> results = future.get(1, TimeUnit.SECONDS);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(elapsedMillis >= 90L);
        assertEquals(List.of("done"), results);
    }

    private void triggerChainBasic() {
        AsyncCallbackFramework framework = framework();
        register(framework, "process", "step1", 20, false, null, kwargs -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) kwargs.get("data");
            data.put("step1", true);
            return ChainResult.builder().action(ChainAction.CONTINUE).result(data).build();
        });
        register(framework, "process", "step2", 10, false, null, kwargs -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) args(kwargs)[0];
            data.put("step2", true);
            return ChainResult.builder().action(ChainAction.CONTINUE).result(data).build();
        });

        ChainResult result = framework.triggerChain("process", new Object[0], Map.of("data", mutableMap("id", 1)));

        assertEquals(ChainAction.CONTINUE, result.getAction());
        assertTrue(result.getContext().isCompleted());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getResult();
        assertEquals(true, data.get("step1"));
        assertEquals(true, data.get("step2"));
    }

    private void triggerChainDataFlows() {
        AsyncCallbackFramework framework = framework();
        register(framework, "chain", "multiply", 20, false, null, kwargs ->
                ChainResult.builder().action(ChainAction.CONTINUE)
                        .result(((Integer) kwargs.get("value")) * 2)
                        .build());
        register(framework, "chain", "add", 10, false, null, kwargs ->
                ChainResult.builder().action(ChainAction.CONTINUE)
                        .result(((Integer) args(kwargs)[0]) + 10)
                        .build());

        ChainResult result = framework.triggerChain("chain", new Object[0], Map.of("value", 5));

        assertEquals(20, result.getResult());
    }

    private void triggerParallelConcurrent() {
        AsyncCallbackFramework framework = framework();
        register(framework, "parallel", "task1", kwargs -> {
            sleepQuietly(120L);
            return "task1";
        });
        register(framework, "parallel", "task2", kwargs -> {
            sleepQuietly(120L);
            return "task2";
        });
        register(framework, "parallel", "task3", kwargs -> {
            sleepQuietly(120L);
            return "task3";
        });

        long start = System.nanoTime();
        List<Object> results = framework.triggerParallel("parallel", new Object[0], Map.of());
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(elapsedMillis < 300L, "parallel callbacks should not run sequentially");
        assertEquals(3, results.size());
    }

    private void triggerParallelHandlesErrors() {
        AsyncCallbackFramework framework = framework();
        register(framework, "parallel", "success", kwargs -> "success");
        register(framework, "parallel", "failure", kwargs -> {
            throw new IllegalArgumentException("Error!");
        });
        register(framework, "parallel", "another_success", kwargs -> "another");

        List<Object> results = framework.triggerParallel("parallel", new Object[0], Map.of());

        assertEquals(2, results.size());
        assertTrue(results.contains("success"));
        assertTrue(results.contains("another"));
    }

    private void triggerParallelRespectsFilters() {
        AsyncCallbackFramework framework = framework();
        framework.addFilter("event", new ValidationFilter((args, kwargs) ->
                Boolean.TRUE.equals(kwargs.getOrDefault("enabled", true))));
        AtomicInteger callCount = new AtomicInteger();
        register(framework, "event", "callback1", kwargs -> {
            callCount.incrementAndGet();
            return "cb1";
        });
        register(framework, "event", "callback2", kwargs -> {
            callCount.incrementAndGet();
            return "cb2";
        });

        framework.triggerParallel("event", new Object[0], Map.of("enabled", false));
        assertEquals(0, callCount.get());

        framework.triggerParallel("event", new Object[0], Map.of("enabled", true));
        assertEquals(2, callCount.get());
    }

    private void triggerUntilFindsMatch() {
        AsyncCallbackFramework framework = framework();
        register(framework, "search", "search1", 10, false, null, kwargs -> 5);
        register(framework, "search", "search2", 5, false, null, kwargs -> 15);
        register(framework, "search", "search3", 1, false, null, kwargs -> 25);

        Object result = framework.triggerUntil("search", value -> ((Integer) value) > 10, new Object[0], Map.of());

        assertEquals(15, result);
    }

    private void triggerUntilNoMatch() {
        AsyncCallbackFramework framework = framework();
        register(framework, "search", "callback", kwargs -> 5);

        assertNull(framework.triggerUntil("search", value -> ((Integer) value) > 100, new Object[0], Map.of()));
    }

    private void triggerUntilHandlesException() {
        AsyncCallbackFramework framework = framework();
        register(framework, "search", "failing", 10, false, null, kwargs -> {
            throw new IllegalArgumentException("Error!");
        });
        register(framework, "search", "success", 5, false, null, kwargs -> 100);

        Object result = framework.triggerUntil("search", value -> ((Integer) value) > 50, new Object[0], Map.of());

        assertEquals(100, result);
    }

    private void triggerWithTimeoutCompletes() {
        AsyncCallbackFramework framework = framework();
        register(framework, "event", "fast_callback", kwargs -> {
            sleepQuietly(10L);
            return "done";
        });

        assertEquals(List.of("done"), framework.triggerWithTimeout("event", 1.0, new Object[0], Map.of()));
    }

    private void triggerWithTimeoutExceeds() {
        AsyncCallbackFramework framework = framework();
        register(framework, "event", "slow_callback", kwargs -> {
            sleepQuietly(1000L);
            return "done";
        });

        assertEquals(List.of(), framework.triggerWithTimeout("event", 0.05, new Object[0], Map.of()));
    }

    private void triggerSkipFilterLogsDebug() {
        Logger log = mock(Logger.class);
        AsyncCallbackFramework framework = frameworkWithLogging(log);
        framework.addFilter("event", new ValidationFilter((args, kwargs) -> false));
        AtomicInteger callCount = new AtomicInteger();
        register(framework, "event", "callback", kwargs -> {
            callCount.incrementAndGet();
            return "result";
        });

        List<Object> results = framework.triggerResults("event");

        assertEquals(List.of(), results);
        assertEquals(0, callCount.get());
        verify(log).debug("Filter skipped callback {}: {}", "callback", "Argument validation failed");
    }

    private void triggerCallbackErrorLogsError() {
        Logger log = mock(Logger.class);
        AsyncCallbackFramework framework = frameworkWithLogging(log);
        register(framework, "event", "failing_callback", kwargs -> {
            throw new IllegalArgumentException("Test error!");
        });

        assertEquals(List.of(), framework.triggerResults("event"));
        verify(log).error(eq("Callback execution failed: {} - {}"), eq("failing_callback"), eq("Test error!"),
                isA(IllegalArgumentException.class));
    }

    private void triggerStopFilterStopsProcessing() {
        Logger log = mock(Logger.class);
        AsyncCallbackFramework framework = frameworkWithLogging(log);
        framework.addFilter("event", new ConditionalFilter((event, callback, args, kwargs) -> false,
                FilterAction.STOP));
        AtomicInteger callCount = new AtomicInteger();
        register(framework, "event", "callback", kwargs -> {
            callCount.incrementAndGet();
            return null;
        });

        List<Object> results = framework.triggerResults("event");

        assertEquals(0, callCount.get());
        assertEquals(List.of(), results);
        verify(log).info("Filter stopped event processing: {}", "event");
    }

    private void circuitBreakerRecordsSuccess() {
        AsyncCallbackFramework framework = framework();
        Function<Map<String, Object>, Object> callback = named("callback", kwargs -> "success");
        register(framework, "event", callback);
        framework.addCircuitBreaker("event", callback, 3, 1.0);

        assertEquals(List.of("success"), framework.triggerResults("event"));
        assertEquals(0, framework.getCircuitBreakers().get("event:callback").getFailures().get("event:callback"));
    }

    private void triggerParallelNoCallbacks() {
        assertEquals(List.of(), framework().triggerParallel("nonexistent_event", new Object[0], Map.of()));
    }

    private void triggerParallelDisabledCallback() {
        AsyncCallbackFramework framework = framework();
        register(framework, "event", "callback", kwargs -> "result");
        framework.getCallbacks().get("event").get(0).setEnabled(false);

        assertEquals(List.of(), framework.triggerParallel("event", new Object[0], Map.of()));
    }

    private void triggerParallelWithStopFilter() {
        AsyncCallbackFramework framework = framework();
        framework.addFilter("event", new ConditionalFilter((event, callback, args, kwargs) -> false,
                FilterAction.STOP));
        register(framework, "event", "callback", kwargs -> "result");

        assertEquals(List.of(), framework.triggerParallel("event", new Object[0], Map.of()));
    }

    private void triggerParallelWithTimeout() {
        AsyncCallbackFramework framework = framework();
        register(framework, "event", "slow_callback", 0, false, 0.05, kwargs ->
                CompletableFuture.supplyAsync(() -> {
                    sleepQuietly(1000L);
                    return "too slow";
                }));
        register(framework, "event", "fast_callback", kwargs -> "fast");

        List<Object> results = framework.triggerParallel("event", new Object[0], Map.of());

        assertTrue(results.contains("fast"));
        assertFalse(results.contains("too slow"));
    }

    private void triggerParallelOnceCallback() {
        AsyncCallbackFramework framework = framework();
        register(framework, "event", "once_callback", 0, true, null, kwargs -> "once");

        List<Object> result1 = framework.triggerParallel("event", new Object[0], Map.of());
        List<Object> result2 = framework.triggerParallel("event", new Object[0], Map.of());

        assertEquals(List.of("once"), result1);
        assertEquals(List.of(), result2);
    }

    private void triggerParallelExceptionLogging() {
        Logger log = mock(Logger.class);
        AsyncCallbackFramework framework = frameworkWithLogging(log);
        register(framework, "event", "failing_callback", kwargs -> {
            throw new IllegalArgumentException("Test error");
        });

        List<Object> results = framework.triggerParallel("event", new Object[0], Map.of());

        assertEquals(List.of(), results);
        verify(log).error(eq("Callback {} failed in parallel execution: {}"), eq("failing_callback"),
                eq("Test error"), isA(IllegalArgumentException.class));
    }

    private void triggerParallelSkipFilterLogsDebug() {
        Logger log = mock(Logger.class);
        AsyncCallbackFramework framework = frameworkWithLogging(log);
        framework.addFilter("event", new ValidationFilter((args, kwargs) -> false));
        AtomicInteger callCount = new AtomicInteger();
        register(framework, "event", "callback", kwargs -> {
            callCount.incrementAndGet();
            return "result";
        });

        List<Object> results = framework.triggerParallel("event", new Object[0], Map.of());

        assertEquals(List.of(), results);
        assertEquals(0, callCount.get());
        verify(log).debug("Filter skipped {}: {}", "callback", "Argument validation failed");
    }

    private void triggerParallelGatherExceptionLogging() {
        AsyncCallbackFramework framework = framework();
        register(framework, "event", "callback", kwargs -> "success");

        assertEquals(List.of("success"), framework.triggerParallel("event", new Object[0], Map.of()));
    }

    private void triggerUntilNoCallbacks() {
        assertNull(framework().triggerUntil("nonexistent", value -> true, new Object[0], Map.of()));
    }

    private void triggerUntilDisabledCallback() {
        AsyncCallbackFramework framework = framework();
        register(framework, "event", "callback", kwargs -> 100);
        framework.getCallbacks().get("event").get(0).setEnabled(false);

        assertNull(framework.triggerUntil("event", value -> ((Integer) value) > 50, new Object[0], Map.of()));
    }

    private void triggerUntilStopFilter() {
        AsyncCallbackFramework framework = framework();
        framework.addFilter("event", new ConditionalFilter((event, callback, args, kwargs) -> false,
                FilterAction.STOP));
        register(framework, "event", "callback", kwargs -> 100);

        assertNull(framework.triggerUntil("event", value -> ((Integer) value) > 50, new Object[0], Map.of()));
    }

    private void triggerUntilSkipFilter() {
        AsyncCallbackFramework framework = framework();
        framework.addFilter("event", new ValidationFilter((args, kwargs) ->
                ((Integer) kwargs.getOrDefault("value", 0)) >= 0));
        register(framework, "event", "skipped_callback", 10, false, null, kwargs -> 100);
        register(framework, "event", "passing_callback", 5, false, null, kwargs -> 200);

        assertNull(framework.triggerUntil("event", value -> ((Integer) value) > 50,
                new Object[0], Map.of("value", -1)));
    }

    private void triggerUntilConditionSatisfiedLogs() {
        Logger log = mock(Logger.class);
        AsyncCallbackFramework framework = frameworkWithLogging(log);
        register(framework, "event", "callback", kwargs -> 100);

        Object result = framework.triggerUntil("event", value -> ((Integer) value) > 50, new Object[0], Map.of());

        assertEquals(100, result);
        verify(log).info("Condition satisfied by {}: {}", "callback", 100);
    }

    private void triggerUntilOnceCallbackConditionMet() {
        AsyncCallbackFramework framework = framework();
        register(framework, "event", "once_callback", 0, true, null, kwargs -> 100);

        Object result = framework.triggerUntil("event", value -> ((Integer) value) > 50, new Object[0], Map.of());

        assertEquals(100, result);
        assertFalse(framework.getCallbacks().get("event").get(0).isEnabled());
    }

    private void triggerUntilOnceCallbackConditionNotMet() {
        AsyncCallbackFramework framework = framework();
        register(framework, "event", "once_callback", 0, true, null, kwargs -> 10);

        Object result = framework.triggerUntil("event", value -> ((Integer) value) > 50, new Object[0], Map.of());

        assertNull(result);
        assertFalse(framework.getCallbacks().get("event").get(0).isEnabled());
    }

    private void triggerUntilExceptionLogging() {
        Logger log = mock(Logger.class);
        AsyncCallbackFramework framework = frameworkWithLogging(log);
        register(framework, "event", "failing_callback", kwargs -> {
            throw new IllegalArgumentException("Test error");
        });

        Object result = framework.triggerUntil("event", value -> true, new Object[0], Map.of());

        assertNull(result);
        verify(log).error(eq("Callback {} failed in triggerUntil: {}"), eq("failing_callback"), eq("Test error"),
                isA(IllegalArgumentException.class));
    }

    private void triggerWithTimeoutLogsWarning() {
        Logger log = mock(Logger.class);
        AsyncCallbackFramework framework = frameworkWithLogging(log);
        register(framework, "event", "slow_callback", kwargs -> {
            sleepQuietly(1000L);
            return "done";
        });

        List<Object> results = framework.triggerWithTimeout("event", 0.05, new Object[0], Map.of());

        assertEquals(List.of(), results);
        verify(log).warn("Event '{}' execution timeout after {}s", "event", 0.05);
    }

    private void addFilterToEvent() {
        AsyncCallbackFramework framework = framework();
        framework.addFilter("limited", new RateLimitFilter(1, 1.0));
        AtomicInteger callCount = new AtomicInteger();
        register(framework, "limited", "callback", kwargs -> {
            callCount.incrementAndGet();
            return null;
        });
        register(framework, "unlimited", "other", kwargs -> {
            callCount.incrementAndGet();
            return null;
        });

        framework.triggerResults("limited");
        framework.triggerResults("limited");
        framework.triggerResults("unlimited");
        framework.triggerResults("unlimited");

        assertEquals(3, callCount.get());
    }

    private void addGlobalFilter() {
        AsyncCallbackFramework framework = framework();
        framework.addGlobalFilter(new RateLimitFilter(1, 1.0));
        AtomicInteger callCount = new AtomicInteger();
        register(framework, "event1", "cb1", kwargs -> {
            callCount.incrementAndGet();
            return null;
        });
        register(framework, "event2", "cb2", kwargs -> {
            callCount.incrementAndGet();
            return null;
        });

        framework.triggerResults("event1");
        framework.triggerResults("event1");
        framework.triggerResults("event2");
        framework.triggerResults("event2");

        assertEquals(2, callCount.get());
    }

    private void addCircuitBreaker() {
        AsyncCallbackFramework framework = framework();
        AtomicInteger callCount = new AtomicInteger();
        Function<Map<String, Object>, Object> failing = named("failing_callback", kwargs -> {
            callCount.incrementAndGet();
            throw new IllegalArgumentException("Error!");
        });
        register(framework, "event", failing);
        framework.addCircuitBreaker("event", failing, 2, 1.0);

        framework.triggerResults("event");
        framework.triggerResults("event");
        framework.triggerResults("event");

        assertTrue(framework.getCircuitBreakers().containsKey("event:failing_callback"));
        assertEquals(2, callCount.get());
    }

    private void modifyFilterChangesArgs() {
        AsyncCallbackFramework framework = framework();
        framework.addFilter("event", new ParamModifyFilter((args, kwargs) -> {
            Object[] newArgs = new Object[args.length];
            for (int index = 0; index < args.length; index++) {
                Object value = args[index];
                newArgs[index] = value instanceof Integer integer ? integer * 2 : value;
            }
            return new ParamModifyFilter.Modification(newArgs, kwargs);
        }));
        List<Map<String, Object>> received = new ArrayList<>();
        register(framework, "event", "callback", kwargs -> {
            received.add(Map.of("args", args(kwargs), "kwargs", pythonKwargs(kwargs)));
            return null;
        });

        framework.triggerResults("event", new Object[]{5, 10}, Map.of());

        assertArrayEquals(new Object[]{10, 20}, (Object[]) received.get(0).get("args"));
    }

    private void modifyFilterChangesKwargs() {
        AsyncCallbackFramework framework = framework();
        framework.addFilter("event", new ParamModifyFilter((args, kwargs) -> {
            Map<String, Object> newKwargs = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : kwargs.entrySet()) {
                Object value = entry.getValue();
                newKwargs.put(entry.getKey(), value instanceof Integer integer ? integer * 2 : value);
            }
            return new ParamModifyFilter.Modification(args, newKwargs);
        }));
        List<Map<String, Object>> received = new ArrayList<>();
        register(framework, "event", "callback", kwargs -> {
            received.add(pythonKwargs(kwargs));
            return null;
        });

        framework.triggerResults("event", new Object[0], Map.of("value", 5, "count", 10));

        assertEquals(10, received.get(0).get("value"));
        assertEquals(20, received.get(0).get("count"));
    }

    private void modifyFilterOnlyArgs() {
        AsyncCallbackFramework framework = framework();
        framework.addFilter("event", new EventFilter("ArgsOnlyModifier") {
            @Override
            public FilterResult filter(
                    String event,
                    Function<Map<String, Object>, Object> callback,
                    Object[] args,
                    Map<String, Object> kwargs
            ) {
                return FilterResult.modifyResult(new Object[]{100}, null);
            }
        });
        List<Map<String, Object>> received = new ArrayList<>();
        register(framework, "event", "callback", kwargs -> {
            received.add(mapOf("args", args(kwargs), "kwargs", pythonKwargs(kwargs)));
            return null;
        });

        framework.triggerResults("event", new Object[]{1}, Map.of("key", "value"));

        assertArrayEquals(new Object[]{100}, (Object[]) received.get(0).get("args"));
        assertEquals(mapOf("key", "value", "session", null), received.get(0).get("kwargs"));
    }

    private void modifyFilterOnlyKwargs() {
        AsyncCallbackFramework framework = framework();
        framework.addFilter("event", new EventFilter("KwargsOnlyModifier") {
            @Override
            public FilterResult filter(
                    String event,
                    Function<Map<String, Object>, Object> callback,
                    Object[] args,
                    Map<String, Object> kwargs
            ) {
                return FilterResult.modifyResult(null, Map.of("new_key", "new_value"));
            }
        });
        List<Map<String, Object>> received = new ArrayList<>();
        register(framework, "event", "callback", kwargs -> {
            received.add(mapOf("args", args(kwargs), "kwargs", pythonKwargs(kwargs)));
            return null;
        });

        framework.triggerResults("event", new Object[]{1, 2, 3}, Map.of());

        assertArrayEquals(new Object[]{1, 2, 3}, (Object[]) received.get(0).get("args"));
        assertEquals(mapOf("new_key", "new_value", "session", null), received.get(0).get("kwargs"));
    }

    private static AsyncCallbackFramework framework() {
        return new AsyncCallbackFramework(false, false);
    }

    private static AsyncCallbackFramework frameworkWithLogging(Logger logger) {
        return new AsyncCallbackFramework(false, true, logger);
    }

    private static void register(
            AsyncCallbackFramework framework,
            String event,
            String name,
            Function<Map<String, Object>, Object> callback
    ) {
        register(framework, event, name, 0, false, null, callback);
    }

    private static void register(
            AsyncCallbackFramework framework,
            String event,
            String name,
            int priority,
            boolean once,
            Double timeout,
            Function<Map<String, Object>, Object> callback
    ) {
        register(framework, event, named(name, callback), priority, once, timeout);
    }

    private static void register(
            AsyncCallbackFramework framework,
            String event,
            Function<Map<String, Object>, Object> callback
    ) {
        register(framework, event, callback, 0, false, null);
    }

    private static void register(
            AsyncCallbackFramework framework,
            String event,
            Function<Map<String, Object>, Object> callback,
            int priority,
            boolean once,
            Double timeout
    ) {
        framework.registerSync(event, callback, priority, once, "default", Set.of(), List.of(),
                null, null, 0, 0.0, timeout, "");
    }

    private static Function<Map<String, Object>, Object> named(
            String name,
            Function<Map<String, Object>, Object> delegate
    ) {
        return new NamedCallback(name, delegate);
    }

    private static Object[] args(Map<String, Object> kwargs) {
        return (Object[]) kwargs.get("_args");
    }

    private static Map<String, Object> pythonKwargs(Map<String, Object> kwargs) {
        Map<String, Object> copy = new LinkedHashMap<>(kwargs);
        copy.remove("_args");
        return copy;
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }

    private static Map<String, Object> mutableMap(Object... values) {
        return mapOf(values);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
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
