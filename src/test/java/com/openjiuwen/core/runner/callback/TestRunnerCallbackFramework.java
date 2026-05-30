/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.runner.callback;

import com.openjiuwen.core.runner.Runner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for callback framework usage through Runner.
 *
 * <p>Mirrors Python's tests/unit_tests/core/runner/callback/test_runner_callback_framework.py</p>
 */
@DisplayName("RunnerCallbackFramework Tests")
class TestRunnerCallbackFramework {

    private CallbackFramework framework;

    @AfterEach
    void cleanupCallbacks() {
        framework = Runner.callbackFramework();
        List<String> eventsToClean = new ArrayList<>(framework.getCallbacks().keySet());
        for (String event : eventsToClean) {
            framework.unregisterEvent(event);
        }
    }

    @Nested
    @DisplayName("RunnerCallbackFramework tests")
    class FrameworkTests {

        @Test
        @DisplayName("test runner callback framework property")
        void testRunnerCallbackFrameworkProperty() {
            framework = Runner.callbackFramework();
            assertNotNull(framework);
            assertTrue(hasMethod(framework, "register"));
            assertTrue(hasMethod(framework, "trigger"));
            assertTrue(hasMethod(framework, "on"));
        }

        @Test
        @DisplayName("test runner callback framework register and trigger")
        void testRunnerCallbackFrameworkRegisterAndTrigger() {
            framework = Runner.callbackFramework();
            List<String> callLog = new ArrayList<>();

            Function<Map<String, Object>, Object> handler = kwargs -> {
                String message = (String) kwargs.get("message");
                callLog.add("received: " + message);
                return "processed: " + message;
            };

            framework.on("test_event", handler, "handler");

            List<Object> results = framework.trigger("test_event", new Object[0], Map.of("message", "hello"));
            assertEquals(1, results.size());
            assertEquals("processed: hello", results.get(0));
            assertEquals(List.of("received: hello"), callLog);
        }

        @Test
        @DisplayName("test runner callback framework multiple callbacks")
        void testRunnerCallbackFrameworkMultipleCallbacks() {
            framework = Runner.callbackFramework();
            List<String> executionOrder = new ArrayList<>();

            Function<Map<String, Object>, Object> lowPriority = kwargs -> {
                executionOrder.add("low");
                return "low_result";
            };

            Function<Map<String, Object>, Object> highPriority = kwargs -> {
                executionOrder.add("high");
                return "high_result";
            };

            framework.register("event", lowPriority, 1, "low_priority");
            framework.register("event", highPriority, 10, "high_priority");

            List<Object> results = framework.trigger("event");
            assertEquals(List.of("high_result", "low_result"), results);
            assertEquals(List.of("high", "low"), executionOrder);
        }

        @Test
        @DisplayName("test runner callback framework with filters")
        void testRunnerCallbackFrameworkWithFilters() {
            framework = Runner.callbackFramework();
            ValidationFilter validator = new ValidationFilter(kwargs -> {
                Integer value = (Integer) kwargs.get("value");
                return value != null && value > 0;
            });

            Function<Map<String, Object>, Object> callback = kwargs -> {
                Integer value = (Integer) kwargs.get("value");
                return value * 2;
            };

            framework.register("event", callback, 0, false, "default", null,
                    List.of(validator), null, null, 0, 0.0, null, "callback");

            List<Object> results = framework.trigger("event", new Object[0], Map.of("value", 10));
            assertEquals(List.of(20), results);

            results = framework.trigger("event", new Object[0], Map.of("value", -5));
            assertEquals(List.of(), results);
        }

        @Test
        @DisplayName("test runner callback framework rate limit")
        void testRunnerCallbackFrameworkRateLimit() {
            framework = Runner.callbackFramework();
            RateLimitFilter rateLimit = new RateLimitFilter(2, 1.0);
            AtomicInteger callCount = new AtomicInteger(0);

            Function<Map<String, Object>, Object> callback = kwargs -> {
                return callCount.incrementAndGet();
            };

            framework.register("event", callback, 0, false, "default", null,
                    List.of(rateLimit), null, null, 0, 0.0, null, "callback");

            List<Object> results1 = framework.trigger("event");
            List<Object> results2 = framework.trigger("event");
            assertEquals(List.of(1), results1);
            assertEquals(List.of(2), results2);

            List<Object> results3 = framework.trigger("event");
            assertEquals(List.of(), results3);
        }

        @Test
        @DisplayName("test runner callback framework decorators")
        void testRunnerCallbackFrameworkDecorators() {
            framework = Runner.callbackFramework();
            List<String> eventLog = new ArrayList<>();

            Function<Map<String, Object>, Object> beforeHandler = kwargs -> {
                eventLog.add("before");
                return null;
            };

            framework.on("before_event", beforeHandler, "before_handler");

            Function<Map<String, Object>, Object> processWrapper = kwargs -> {
                String data = kwargs.containsKey("_args") && kwargs.get("_args") instanceof Object[]
                        ? (String) ((Object[]) kwargs.get("_args"))[0]
                        : (String) kwargs.get("data");
                eventLog.add("process: " + data);
                return Map.of("result", data);
            };

            framework.register("before_event", beforeHandler, 0, "before_handler");
            List<Object> results = framework.trigger("before_event", new Object[]{"test"}, new HashMap<>());

            assertTrue(eventLog.contains("before") || eventLog.contains("process: test"));
        }

        @Test
        @DisplayName("test runner callback framework chain")
        void testRunnerCallbackFrameworkChain() {
            framework = Runner.callbackFramework();
            AtomicBoolean rollbackCalled = new AtomicBoolean(false);

            Function<Map<String, Object>, Object> callback = kwargs -> {
                return new ChainResult(ChainAction.ROLLBACK, null, null, new Exception("fail"));
            };

            Consumer<ChainContext> rollbackHandler = context -> {
                rollbackCalled.set(true);
            };

            framework.register("chain_event", callback, 0, false, "default", null,
                    null, rollbackHandler, null, 0, 0.0, null, "callback");

            ChainResult result = framework.triggerChain("chain_event", new Object[0], new HashMap<>());
            assertEquals(ChainAction.ROLLBACK, result.getAction());
        }

        @Test
        @DisplayName("test runner callback framework hooks")
        void testRunnerCallbackFrameworkHooks() {
            framework = Runner.callbackFramework();
            List<String> executionOrder = new ArrayList<>();

            Consumer<Map<String, Object>> beforeHook = kwargs -> {
                executionOrder.add("before_hook");
            };

            Function<Map<String, Object>, Object> callback = kwargs -> {
                executionOrder.add("callback");
                return "result";
            };

            Consumer<Map<String, Object>> afterHook = kwargs -> {
                executionOrder.add("after_hook");
            };

            framework.on("event", callback, "callback");
            framework.addHook("event", HookType.BEFORE, beforeHook);
            framework.addHook("event", HookType.AFTER, afterHook);

            List<Object> results = framework.trigger("event");
            assertEquals(List.of("result"), results);
            assertEquals(List.of("before_hook", "callback", "after_hook"), executionOrder);
        }

        @Test
        @DisplayName("test runner callback framework namespace")
        void testRunnerCallbackFrameworkNamespace() {
            framework = Runner.callbackFramework();

            Function<Map<String, Object>, Object> callback1 = kwargs -> "ns1_result";
            Function<Map<String, Object>, Object> callback2 = kwargs -> "ns2_result";

            framework.register("event", callback1, 0, false, "ns1", null, null, null, null, 0, 0.0, null, "callback1");
            framework.register("event", callback2, 0, false, "ns2", null, null, null, null, 0, 0.0, null, "callback2");

            List<Object> results = framework.trigger("event");
            assertEquals(2, results.size());
            assertTrue(results.contains("ns1_result"));
            assertTrue(results.contains("ns2_result"));
        }

        @Test
        @DisplayName("test runner callback framework unregister")
        void testRunnerCallbackFrameworkUnregister() {
            framework = Runner.callbackFramework();

            Function<Map<String, Object>, Object> callback1 = kwargs -> "result1";
            Function<Map<String, Object>, Object> callback2 = kwargs -> "result2";

            framework.register("event", callback1, "callback1");
            framework.register("event", callback2, "callback2");

            assertEquals(2, framework.listCallbacks("event").size());

            framework.unregister("event", callback1);
            List<Map<String, Object>> callbacks = framework.listCallbacks("event");
            assertEquals(1, callbacks.size());
            assertEquals("callback2", callbacks.get(0).get("name"));
        }

        @Test
        @DisplayName("test runner callback framework unregister decorator callback")
        void testRunnerCallbackFrameworkUnregisterDecoratorCallback() {
            framework = Runner.callbackFramework();

            Function<Map<String, Object>, Object> callback1 = kwargs -> "result1";
            Function<Map<String, Object>, Object> callback2 = kwargs -> "result2";

            framework.on("event", callback1, "callback1");
            framework.on("event", callback2, "callback2");

            assertEquals(2, framework.listCallbacks("event").size());

            framework.unregister("event", callback1);
            List<Map<String, Object>> callbacks = framework.listCallbacks("event");
            assertEquals(1, callbacks.size());
            assertEquals("callback2", callbacks.get(0).get("name"));

            framework.unregister("event", callback2);
            callbacks = framework.listCallbacks("event");
            assertEquals(0, callbacks.size());

            Function<Map<String, Object>, Object> callback3 = kwargs -> "result3";
            Function<Map<String, Object>, Object> callback4 = kwargs -> "result4";

            framework.on("test_event", callback3, "callback3");
            framework.on("test_event", callback4, "callback4");

            assertEquals(2, framework.listCallbacks("test_event").size());

            framework.unregister("test_event", callback3);
            callbacks = framework.listCallbacks("test_event");
            assertEquals(1, callbacks.size());
            assertEquals("callback4", callbacks.get(0).get("name"));

            framework.unregister("test_event", callback4);
            callbacks = framework.listCallbacks("test_event");
            assertEquals(0, callbacks.size());
        }

        @Test
        @DisplayName("test runner callback framework unregister event")
        void testRunnerCallbackFrameworkUnregisterEvent() {
            framework = Runner.callbackFramework();

            Function<Map<String, Object>, Object> callback1 = kwargs -> "result1";
            Function<Map<String, Object>, Object> callback2 = kwargs -> "result2";
            Function<Map<String, Object>, Object> callback3 = kwargs -> "result3";

            framework.on("test_event", callback1, "callback1");
            framework.on("test_event", callback2, "callback2");
            framework.on("other_event", callback3, "callback3");

            assertEquals(2, framework.listCallbacks("test_event").size());
            assertEquals(1, framework.listCallbacks("other_event").size());

            framework.unregisterEvent("test_event");

            assertEquals(0, framework.listCallbacks("test_event").size());
            assertEquals(1, framework.listCallbacks("other_event").size());

            List<Object> results = framework.trigger("test_event");
            assertEquals(List.of(), results);

            results = framework.trigger("other_event");
            assertEquals(List.of("result3"), results);
        }

        @Test
        @DisplayName("test runner callback framework unregister event with filters")
        void testRunnerCallbackFrameworkUnregisterEventWithFilters() {
            framework = Runner.callbackFramework();
            ValidationFilter validator = new ValidationFilter(kwargs -> {
                Integer value = (Integer) kwargs.get("value");
                return value != null && value > 0;
            });

            Function<Map<String, Object>, Object> callback = kwargs -> {
                Integer value = (Integer) kwargs.get("value");
                return value * 2;
            };

            framework.register("test_event", callback, 0, false, "default", null,
                    List.of(validator), null, null, 0, 0.0, null, "callback");
            framework.addFilter("test_event", validator);

            List<Object> results = framework.trigger("test_event", new Object[0], Map.of("value", 10));
            assertEquals(List.of(20), results);

            framework.unregisterEvent("test_event");

            assertFalse(framework.getCallbacks().containsKey("test_event"));
            assertEquals(0, framework.listCallbacks("test_event").size());
        }

        @Test
        @DisplayName("test runner callback framework unregister event with chain")
        void testRunnerCallbackFrameworkUnregisterEventWithChain() {
            framework = Runner.callbackFramework();

            Function<Map<String, Object>, Object> callback = kwargs -> {
                return new ChainResult(ChainAction.CONTINUE, null, null, null);
            };

            Consumer<ChainContext> rollbackHandler = context -> {};

            framework.register("chain_event", callback, 0, false, "default", null,
                    null, rollbackHandler, null, 0, 0.0, null, "callback");

            assertTrue(framework.getChains().containsKey("chain_event") || 
                       framework.listCallbacks("chain_event").size() > 0);

            framework.unregisterEvent("chain_event");

            assertFalse(framework.getChains().containsKey("chain_event"));
            assertEquals(0, framework.listCallbacks("chain_event").size());
        }

        @Test
        @DisplayName("test runner callback framework unregister event with hooks")
        void testRunnerCallbackFrameworkUnregisterEventWithHooks() {
            framework = Runner.callbackFramework();

            Function<Map<String, Object>, Object> callback = kwargs -> "result";

            Consumer<Map<String, Object>> beforeHook = kwargs -> {};
            Consumer<Map<String, Object>> afterHook = kwargs -> {};

            framework.on("test_event", callback, "callback");
            framework.addHook("test_event", HookType.BEFORE, beforeHook);
            framework.addHook("test_event", HookType.AFTER, afterHook);

            List<Object> results = framework.trigger("test_event");
            assertTrue(results.contains("result"));

            framework.unregisterEvent("test_event");
            assertEquals(0, framework.listCallbacks("test_event").size());
        }

        @Test
        @DisplayName("test runner callback framework unregister nonexistent event")
        void testRunnerCallbackFrameworkUnregisterNonexistentEvent() {
            framework = Runner.callbackFramework();

            framework.unregisterEvent("nonexistent_event");

            assertEquals(0, framework.listCallbacks("nonexistent_event").size());
        }

        @Test
        @DisplayName("test runner callback framework tags")
        void testRunnerCallbackFrameworkTags() {
            framework = Runner.callbackFramework();

            Function<Map<String, Object>, Object> debugCallback = kwargs -> "debug_result";
            Function<Map<String, Object>, Object> prodCallback = kwargs -> "prod_result";

            Set<String> debugTags = new HashSet<>();
            debugTags.add("debug");
            debugTags.add("test");

            Set<String> prodTags = new HashSet<>();
            prodTags.add("production");

            framework.register("event", debugCallback, 0, false, "default", debugTags, null, null, null, 0, 0.0, null, "debug_callback");
            framework.register("event", prodCallback, 0, false, "default", prodTags, null, null, null, 0, 0.0, null, "prod_callback");

            List<Map<String, Object>> callbacks = framework.listCallbacks("event");
            assertEquals(2, callbacks.size());

            Set<String> tagsSet = new HashSet<>();
            for (Map<String, Object> cb : callbacks) {
                Object tagsObj = cb.get("tags");
                if (tagsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> tagList = (List<String>) tagsObj;
                    tagsSet.addAll(tagList);
                }
            }
            assertTrue(tagsSet.contains("debug"));
            assertTrue(tagsSet.contains("test"));
            assertTrue(tagsSet.contains("production"));
        }

        @Test
        @DisplayName("test runner callback framework emit around")
        void testRunnerCallbackFrameworkEmitAround() {
            framework = Runner.callbackFramework();
            List<String> eventLog = new ArrayList<>();

            Function<Map<String, Object>, Object> onStart = kwargs -> {
                eventLog.add("start");
                return null;
            };

            Function<Map<String, Object>, Object> onEnd = kwargs -> {
                Object result = kwargs.get("result");
                eventLog.add("end: " + result);
                return null;
            };

            framework.on("start", onStart, "on_start");
            framework.on("end", onEnd, "on_end");

            framework.trigger("start");
            eventLog.add("processing");
            framework.trigger("end", new Object[0], Map.of("result", "done"));

            assertTrue(eventLog.contains("start"));
            assertTrue(eventLog.contains("processing"));
        }

        @Test
        @DisplayName("test runner callback framework emits")
        void testRunnerCallbackFrameworkEmits() {
            framework = Runner.callbackFramework();
            List<Object> receivedResults = new ArrayList<>();

            Function<Map<String, Object>, Object> onReady = kwargs -> {
                Object result = kwargs.get("result");
                receivedResults.add(result);
                return null;
            };

            framework.on("data_ready", onReady, "on_ready");

            Map<String, Object> processResult = Map.of("status", "done");
            framework.trigger("data_ready", new Object[0], Map.of("result", processResult));

            assertEquals(List.of(Map.of("status", "done")), receivedResults);
        }

        @Test
        @DisplayName("test runner callback framework error handling")
        void testRunnerCallbackFrameworkErrorHandling() {
            framework = Runner.callbackFramework();
            AtomicReference<Exception> errorReceived = new AtomicReference<>();

            Function<CallbackChain.ExceptionContext, Object> errorHandler = context -> {
                errorReceived.set(context.exception());
                return "recovered";
            };

            Function<Map<String, Object>, Object> failingCallback = kwargs -> {
                throw new IllegalArgumentException("Test error");
            };

            framework.register("event", failingCallback, 0, false, "default", null,
                    null, null, errorHandler, 0, 0.0, null, "failing_callback");

            ChainResult result = framework.triggerChain("event", new Object[0], new HashMap<>());
            assertEquals(ChainAction.CONTINUE, result.getAction());
            assertNotNull(errorReceived.get());
            assertEquals("Test error", errorReceived.get().getMessage());
        }
    }

    private boolean hasMethod(Object obj, String methodName) {
        for (var method : obj.getClass().getMethods()) {
            if (method.getName().equals(methodName)) {
                return true;
            }
        }
        return false;
    }
}
