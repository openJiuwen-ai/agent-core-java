// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.

package com.openjiuwen.core.runner.callback;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Tests for CallbackFramework registration, triggering, filters, hooks, metrics, and chain.
 * Translated from Python test_framework_registration.py, test_runner_callback_framework.py,
 * test_framework_hooks.py, test_framework_metrics.py
 */
@DisplayName("CallbackFramework Tests")
class CallbackFrameworkTest {
    private CallbackFramework framework;

    @BeforeEach
    void setup() {
        framework = new CallbackFramework(true, true);
    }

    // ========== Registration ==========

    @Test
    @DisplayName("Register basic callback")
    void testRegisterBasic() {
        framework.register("test_event", kwargs -> "received: " + kwargs.get("message"), "callback");
        List<Map<String, Object>> callbacks = framework.listCallbacks("test_event");
        assertEquals(1, callbacks.size());
        assertEquals("callback", callbacks.get(0).get("name"));
    }

    @Test
    @DisplayName("Register with priority")
    void testRegisterWithPriority() {
        framework.register("event", kwargs -> null, 1, "low");
        framework.register("event", kwargs -> null, 10, "high");
        List<Map<String, Object>> callbacks = framework.listCallbacks("event");
        assertEquals("high", callbacks.get(0).get("name"));
        assertEquals("low", callbacks.get(1).get("name"));
    }

    @Test
    @DisplayName("Register with namespace")
    void testRegisterWithNamespace() {
        framework.register("event", kwargs -> null, 0, false, "custom", null, null, null, null, 0, 0.0, null,
                "callback");
        List<Map<String, Object>> callbacks = framework.listCallbacks("event");
        assertEquals("custom", callbacks.get(0).get("namespace"));
    }

    @Test
    @DisplayName("Register with tags")
    void testRegisterWithTags() {
        framework.register("event", kwargs -> null, 0, false, "default", Set.of("tag1", "tag2"), null, null, null, 0,
                0.0, null, "callback");
        List<Map<String, Object>> callbacks = framework.listCallbacks("event");
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) callbacks.get(0).get("tags");
        assertTrue(tags.contains("tag1"));
        assertTrue(tags.contains("tag2"));
    }

    @Test
    @DisplayName("Register with once flag")
    void testRegisterWithOnce() {
        framework.register("event", kwargs -> null, 0, true, "default", null, null, null, null, 0, 0.0, null,
                "callback");
        List<Map<String, Object>> callbacks = framework.listCallbacks("event");
        assertTrue((Boolean) callbacks.get(0).get("once"));
    }

    @Test
    @DisplayName("Register with retry settings")
    void testRegisterWithRetrySettings() {
        framework.register("event", kwargs -> null, 0, false, "default", null, null, null, null, 3, 1.0, 30.0,
                "callback");
        List<Map<String, Object>> callbacks = framework.listCallbacks("event");
        assertEquals(3, callbacks.get(0).get("max_retries"));
        assertEquals(30.0, callbacks.get(0).get("timeout"));
    }

    @Test
    @DisplayName("Unregister callback")
    void testUnregisterCallback() {
        Function<Map<String, Object>, Object> cb1 = kwargs -> "result1";
        Function<Map<String, Object>, Object> cb2 = kwargs -> "result2";
        framework.register("event", cb1, "callback1");
        framework.register("event", cb2, "callback2");
        assertEquals(2, framework.listCallbacks("event").size());
        framework.unregister("event", cb1);
        assertEquals(1, framework.listCallbacks("event").size());
        assertEquals("callback2", framework.listCallbacks("event").get(0).get("name"));
    }

    @Test
    @DisplayName("Unregister nonexistent callback does not error")
    void testUnregisterNonexistentCallback() {
        Function<Map<String, Object>, Object> cb = kwargs -> null;
        Function<Map<String, Object>, Object> other = kwargs -> null;
        framework.register("event", cb, "callback");
        assertDoesNotThrow(() -> framework.unregister("event", other));
        assertEquals(1, framework.listCallbacks("event").size());
    }

    @Test
    @DisplayName("Unregister namespace")
    void testUnregisterNamespace() {
        framework.register("event", kwargs -> null, 0, false, "ns1", null, null, null, null, 0, 0.0, null, "cb1");
        framework.register("event", kwargs -> null, 0, false, "ns1", null, null, null, null, 0, 0.0, null, "cb2");
        framework.register("event", kwargs -> null, 0, false, "ns2", null, null, null, null, 0, 0.0, null, "cb3");
        framework.unregisterNamespace("ns1");
        assertEquals(1, framework.listCallbacks("event").size());
        assertEquals("ns2", framework.listCallbacks("event").get(0).get("namespace"));
    }

    @Test
    @DisplayName("Unregister by tags")
    void testUnregisterByTags() {
        framework.register("event", kwargs -> null, 0, false, "default", Set.of("debug"), null, null, null, 0, 0.0,
                null, "cb1");
        framework.register("event", kwargs -> null, 0, false, "default", Set.of("debug", "verbose"), null, null, null,
                0, 0.0, null, "cb2");
        framework.register("event", kwargs -> null, 0, false, "default", Set.of("production"), null, null, null, 0, 0.0,
                null, "cb3");
        framework.unregisterByTags(Set.of("debug"));
        List<Map<String, Object>> callbacks = framework.listCallbacks("event");
        assertEquals(1, callbacks.size());
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) callbacks.get(0).get("tags");
        assertTrue(tags.contains("production"));
    }

    @Test
    @DisplayName("Unregister event removes all")
    void testUnregisterEvent() {
        framework.register("test_event", kwargs -> "result1", "cb1");
        framework.register("test_event", kwargs -> "result2", "cb2");
        framework.register("other_event", kwargs -> "result3", "cb3");
        assertEquals(2, framework.listCallbacks("test_event").size());
        assertEquals(1, framework.listCallbacks("other_event").size());
        framework.unregisterEvent("test_event");
        assertEquals(0, framework.listCallbacks("test_event").size());
        assertEquals(1, framework.listCallbacks("other_event").size());
    }

    // ========== Triggering ==========

    @Test
    @DisplayName("Trigger basic callback")
    void testTriggerBasic() {
        framework.register("test_event", kwargs -> "received: " + kwargs.get("message"), "handler");
        List<Object> results = framework.trigger("test_event", new Object[0], Map.of("message", "hello"));
        assertEquals(1, results.size());
        assertEquals("received: hello", results.get(0));
    }

    @Test
    @DisplayName("Trigger multiple callbacks with priority")
    void testTriggerMultipleCallbacksWithPriority() {
        List<String> executionOrder = new ArrayList<>();

        framework.register("event", kwargs -> {
            executionOrder.add("low");
            return "low_result";
        }, 1, "low_priority");

        framework.register("event", kwargs -> {
            executionOrder.add("high");
            return "high_result";
        }, 10, "high_priority");

        List<Object> results = framework.trigger("event");

        assertEquals(List.of("high_result", "low_result"), results);
        assertEquals(List.of("high", "low"), executionOrder);
    }

    @Test
    @DisplayName("Trigger with validation filter")
    void testTriggerWithValidationFilter() {
        ValidationFilter validator =
            new ValidationFilter(kwargs -> kwargs.get("value") instanceof Integer && (Integer) kwargs.get("value") > 0);

        framework.register("event", kwargs -> (Integer) kwargs.get("value") * 2, 0, false, "default", null,
                List.of(validator), null, null, 0, 0.0, null, "callback");

        // Valid call should return result
        List<Object> results = framework.trigger("event", new Object[0], Map.of("value", 10));
        assertEquals(List.of(20), results);

        // Invalid call should be filtered out
        List<Object> results2 = framework.trigger("event", new Object[0], Map.of("value", -5));
        assertTrue(results2.isEmpty());
    }

    @Test
    @DisplayName("Trigger with rate limit")
    void testTriggerWithRateLimit() {
        RateLimitFilter rateLimit = new RateLimitFilter(2, 1.0);
        int[] callCount = {0};

        framework.register("event", kwargs -> {
            callCount[0]++;
            return callCount[0];
        }, 0, false, "default", null, List.of(rateLimit), null, null, 0, 0.0, null, "callback");

        List<Object> r1 = framework.trigger("event");
        List<Object> r2 = framework.trigger("event");
        assertEquals(List.of(1), r1);
        assertEquals(List.of(2), r2);

        // Third call should be rate limited
        List<Object> r3 = framework.trigger("event");
        assertTrue(r3.isEmpty());
    }

    @Test
    @DisplayName("Trigger namespace isolation")
    void testTriggerNamespaceIsolation() {
        framework.register("event", kwargs -> "ns1_result", 0, false, "ns1", null, null, null, null, 0, 0.0, null,
                "cb1");
        framework.register("event", kwargs -> "ns2_result", 0, false, "ns2", null, null, null, null, 0, 0.0, null,
                "cb2");

        List<Object> results = framework.trigger("event");
        assertEquals(2, results.size());
        assertTrue(results.contains("ns1_result"));
        assertTrue(results.contains("ns2_result"));
    }

    // ========== Trigger Chain ==========

    @Test
    @DisplayName("Trigger chain with rollback")
    void testTriggerChain() {
        boolean[] rollbackCalled = {false};

        Function<Map<String, Object>, Object> callback =
            kwargs -> ChainResult.builder().action(ChainAction.ROLLBACK).error(new RuntimeException("fail")).build();

        framework.register("chain_event", callback, 0, false, "default", null, null, ctx -> rollbackCalled[0] = true,
                null, 0, 0.0, null, "callback");

        ChainResult result = framework.triggerChain("chain_event", null, null);
        assertEquals(ChainAction.ROLLBACK, result.getAction());
    }

    // ========== Hooks ==========

    @Test
    @DisplayName("BEFORE hook executes before callback")
    void testBeforeHookExecutesBeforeCallback() {
        List<String> executionOrder = new ArrayList<>();

        framework.addHook("event", HookType.BEFORE, hookKwargs -> executionOrder.add("before_hook"));
        framework.register("event", kwargs -> {
            executionOrder.add("callback");
            return "result";
        }, "callback");

        framework.trigger("event");

        assertEquals(List.of("before_hook", "callback"), executionOrder);
    }

    @Test
    @DisplayName("AFTER hook executes after callback")
    void testAfterHookExecutesAfterCallback() {
        List<String> executionOrder = new ArrayList<>();

        framework.register("event", kwargs -> {
            executionOrder.add("callback");
            return "result";
        }, "callback");
        framework.addHook("event", HookType.AFTER, hookKwargs -> executionOrder.add("after_hook"));

        framework.trigger("event");

        assertEquals(List.of("callback", "after_hook"), executionOrder);
    }

    @Test
    @DisplayName("AFTER hook receives results")
    void testAfterHookReceivesResults() {
        Object[] receivedResults = {null};

        framework.register("event", kwargs -> "result1", "cb1");
        framework.register("event", kwargs -> "result2", "cb2");
        framework.addHook("event", HookType.AFTER, hookKwargs -> {
            receivedResults[0] = hookKwargs.get("_results");
        });

        framework.trigger("event");

        @SuppressWarnings("unchecked")
        List<Object> results = (List<Object>) receivedResults[0];
        assertNotNull(results);
        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("ERROR hook on callback exception")
    void testErrorHookOnCallbackException() {
        Exception[] errorReceived = {null};

        framework.register("event", kwargs -> {
            throw new RuntimeException("Test error");
        }, "failing_callback");
        framework.addHook("event", HookType.ERROR, hookKwargs -> {
            errorReceived[0] = (Exception) hookKwargs.get("_error");
        });

        framework.trigger("event");

        assertNotNull(errorReceived[0]);
        assertInstanceOf(RuntimeException.class, errorReceived[0]);
        assertEquals("Test error", errorReceived[0].getMessage());
    }

    @Test
    @DisplayName("Unregister event also removes hooks")
    void testUnregisterEventRemovesHooks() {
        framework.register("test_event", kwargs -> "result", "callback");
        framework.addHook("test_event", HookType.BEFORE, hookKwargs -> {
        });
        framework.addHook("test_event", HookType.AFTER, hookKwargs -> {
        });

        framework.unregisterEvent("test_event");

        assertEquals(0, framework.listCallbacks("test_event").size());
    }

    // ========== Metrics ==========

    @Test
    @DisplayName("Metrics collects data when enabled")
    void testMetricsEnabled() {
        framework.register("event", kwargs -> "done", "callback");
        framework.trigger("event");
        Map<String, Map<String, Object>> metrics = framework.getMetrics();
        assertFalse(metrics.isEmpty());
        assertTrue(metrics.containsKey("event:callback"));
    }

    @Test
    @DisplayName("Metrics disabled no collection")
    void testMetricsDisabled() {
        CallbackFramework noMetrics = new CallbackFramework(false, true);
        noMetrics.register("event", kwargs -> "done", "callback");
        noMetrics.trigger("event");
        Map<String, Map<String, Object>> metrics = noMetrics.getMetrics();
        assertTrue(metrics.isEmpty());
    }

    @Test
    @DisplayName("Metrics tracks call count")
    void testMetricsTracksCallCount() {
        framework.register("event", kwargs -> "done", "callback");
        for (int i = 0; i < 5; i++) {
            framework.trigger("event");
        }
        Map<String, Map<String, Object>> metrics = framework.getMetrics("event", "callback");
        assertEquals(5, metrics.get("event:callback").get("call_count"));
    }

    @Test
    @DisplayName("Metrics tracks errors")
    void testMetricsTracksErrors() {
        framework.register("event", kwargs -> {
            throw new RuntimeException("Error!");
        }, "failing_callback");
        framework.trigger("event");
        framework.trigger("event");
        framework.trigger("event");
        Map<String, Map<String, Object>> metrics = framework.getMetrics();
        Map<String, Object> metric = metrics.get("event:failing_callback");
        assertEquals(3, metric.get("call_count"));
        assertEquals(3, metric.get("error_count"));
        assertEquals(1.0, (double) metric.get("error_rate"), 0.01);
    }

    @Test
    @DisplayName("Get metrics filter by event")
    void testGetMetricsFilterByEvent() {
        framework.register("event1", kwargs -> null, "cb1");
        framework.register("event2", kwargs -> null, "cb2");
        framework.trigger("event1");
        framework.trigger("event2");
        Map<String, Map<String, Object>> metrics = framework.getMetrics("event1", null);
        assertEquals(1, metrics.size());
        assertTrue(metrics.containsKey("event1:cb1"));
    }

    @Test
    @DisplayName("Get metrics filter by callback")
    void testGetMetricsFilterByCallback() {
        framework.register("event", kwargs -> null, "callback_a");
        framework.register("event", kwargs -> null, "callback_b");
        framework.trigger("event");
        Map<String, Map<String, Object>> metrics = framework.getMetrics(null, "callback_a");
        assertEquals(1, metrics.size());
        assertTrue(metrics.containsKey("event:callback_a"));
    }

    @Test
    @DisplayName("Reset metrics")
    void testResetMetrics() {
        framework.register("event", kwargs -> null, "callback");
        framework.trigger("event");
        assertFalse(framework.getMetrics().isEmpty());
        framework.resetMetrics();
        assertTrue(framework.getMetrics().isEmpty());
    }

    // ========== Parallel trigger ==========

    @Test
    @DisplayName("Trigger parallel")
    void testTriggerParallel() {
        framework.register("event", kwargs -> "result1", "cb1");
        framework.register("event", kwargs -> "result2", "cb2");
        List<Object> results = framework.triggerParallel("event", null, null);
        assertEquals(2, results.size());
        assertTrue(results.contains("result1"));
        assertTrue(results.contains("result2"));
    }

    @Test
    @DisplayName("Trigger parallel omits failed callbacks but keeps successful empty lists")
    void testTriggerParallelOmitsFailures() {
        framework.register("event", kwargs -> List.of(), "empty_result");
        framework.register("event", kwargs -> {
            throw new IllegalStateException("failed");
        }, "failed");

        List<Object> results = framework.triggerParallel("event", null, null);

        assertEquals(1, results.size());
        assertEquals(List.of(), results.get(0));
    }

    // ========== History ==========

    @Test
    @DisplayName("History disabled by default")
    void testHistoryDisabledByDefault() {
        framework.register("event", kwargs -> null, "callback");
        framework.trigger("event");
        List<Map<String, Object>> history = framework.getEventHistory(null, null);
        assertTrue(history.isEmpty());
    }

    @Test
    @DisplayName("Enable history")
    void testEnableHistory() {
        framework.enableEventHistory(true);
        framework.register("event", kwargs -> null, "callback");
        framework.trigger("event");
        List<Map<String, Object>> history = framework.getEventHistory(null, null);
        assertEquals(1, history.size());
        assertEquals("event", history.get(0).get("event"));
    }

    // ========== Statistics ==========

    @Test
    @DisplayName("Get statistics")
    void testGetStatistics() {
        framework.register("event1", kwargs -> null, "cb1");
        framework.register("event2", kwargs -> null, "cb2");
        Map<String, Object> stats = framework.getStatistics();
        assertEquals(2, stats.get("total_events"));
        assertEquals(2, stats.get("total_callbacks"));
    }

    // ========== Once-only callbacks ==========

    @Test
    @DisplayName("Once callback executes only once then disabled")
    void testOnceCallbackExecutesOnce() {
        int[] count = {0};
        framework.register("event", kwargs -> {
            count[0]++;
            return count[0];
        }, 0, true, "default", null, null, null, null, 0, 0.0, null, "once_cb");

        framework.trigger("event");
        framework.trigger("event");
        framework.trigger("event");

        assertEquals(1, count[0]);
    }
}
