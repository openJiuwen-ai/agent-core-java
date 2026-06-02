/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.runner.callback;

import com.openjiuwen.core.runner.callback.CallbackChain;
import com.openjiuwen.core.runner.callback.CallbackFramework;
import com.openjiuwen.core.runner.callback.CallbackInfo;
import com.openjiuwen.core.runner.callback.ChainAction;
import com.openjiuwen.core.runner.callback.ChainResult;
import com.openjiuwen.core.runner.callback.ValidationFilter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Framework registration test cases.
 *
 * <p>Mirrors Python's {@code test_framework_registration.py} in
 * {@code tests/unit_tests/core/runner/callback/test_framework_registration}.</p>
 */
@DisplayName("Framework Registration Tests")
class TestFrameworkRegistration {

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
    @DisplayName("test_register_basic")
    void testRegisterBasic() {
        framework.register("test_event", kwargs -> "received: " + kwargs.get("message"), "callback");

        List<Map<String, Object>> callbacks = framework.listCallbacks("test_event");

        assertEquals(1, callbacks.size());
        assertEquals("callback", callbacks.get(0).get("name"));
    }

    @Test
    @DisplayName("test_register_with_priority")
    void testRegisterWithPriority() {
        framework.register("event", kwargs -> null, 1, "low");
        framework.register("event", kwargs -> null, 10, "high");

        List<Map<String, Object>> callbacks = framework.listCallbacks("event");

        assertEquals("high", callbacks.get(0).get("name"));
        assertEquals("low", callbacks.get(1).get("name"));
    }

    @Test
    @DisplayName("test_register_with_namespace")
    void testRegisterWithNamespace() {
        framework.register("event", kwargs -> null, 0, false, "custom", null,
                null, null, null, 0, 0.0, null, "callback");

        assertEquals("custom", framework.listCallbacks("event").get(0).get("namespace"));
    }

    @Test
    @DisplayName("test_register_with_tags")
    void testRegisterWithTags() {
        framework.register("event", kwargs -> null, 0, false, "default", Set.of("tag1", "tag2"),
                null, null, null, 0, 0.0, null, "callback");

        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) framework.listCallbacks("event").get(0).get("tags");

        assertTrue(tags.containsAll(List.of("tag1", "tag2")));
    }

    @Test
    @DisplayName("test_register_with_once")
    void testRegisterWithOnce() {
        framework.register("event", kwargs -> null, 0, true, "default", null,
                null, null, null, 0, 0.0, null, "callback");

        assertEquals(true, framework.listCallbacks("event").get(0).get("once"));
    }

    @Test
    @DisplayName("test_register_with_retry_settings")
    void testRegisterWithRetrySettings() {
        framework.register("event", kwargs -> null, 0, false, "default", null,
                null, null, null, 3, 1.0, 30.0, "callback");

        Map<String, Object> callback = framework.listCallbacks("event").get(0);
        assertEquals(3, callback.get("max_retries"));
        assertEquals(30.0, callback.get("timeout"));
    }

    @Test
    @DisplayName("test_unregister_callback")
    void testUnregisterCallback() {
        Function<Map<String, Object>, Object> callback1 = kwargs -> null;
        Function<Map<String, Object>, Object> callback2 = kwargs -> null;
        framework.register("event", callback1, "callback1");
        framework.register("event", callback2, "callback2");

        framework.unregister("event", callback1);

        List<Map<String, Object>> callbacks = framework.listCallbacks("event");
        assertEquals(1, callbacks.size());
        assertEquals("callback2", callbacks.get(0).get("name"));
    }

    @Test
    @DisplayName("test_unregister_nonexistent_callback")
    void testUnregisterNonexistentCallback() {
        Function<Map<String, Object>, Object> callback = kwargs -> null;
        Function<Map<String, Object>, Object> other = kwargs -> null;
        framework.register("event", callback, "callback");

        framework.unregister("event", other);

        assertEquals(1, framework.listCallbacks("event").size());
    }

    @Test
    @DisplayName("test_unregister_namespace")
    void testUnregisterNamespace() {
        framework.register("event", kwargs -> null, 0, false, "ns1", null,
                null, null, null, 0, 0.0, null, "cb1");
        framework.register("event", kwargs -> null, 0, false, "ns1", null,
                null, null, null, 0, 0.0, null, "cb2");
        framework.register("event", kwargs -> null, 0, false, "ns2", null,
                null, null, null, 0, 0.0, null, "cb3");

        framework.unregisterNamespace("ns1");

        List<Map<String, Object>> callbacks = framework.listCallbacks("event");
        assertEquals(1, callbacks.size());
        assertEquals("ns2", callbacks.get(0).get("namespace"));
    }

    @Test
    @DisplayName("test_unregister_by_tags")
    void testUnregisterByTags() {
        framework.register("event", kwargs -> null, 0, false, "default", Set.of("debug"),
                null, null, null, 0, 0.0, null, "cb1");
        framework.register("event", kwargs -> null, 0, false, "default", Set.of("debug", "verbose"),
                null, null, null, 0, 0.0, null, "cb2");
        framework.register("event", kwargs -> null, 0, false, "default", Set.of("production"),
                null, null, null, 0, 0.0, null, "cb3");

        framework.unregisterByTags(Set.of("debug"));

        List<Map<String, Object>> callbacks = framework.listCallbacks("event");
        assertEquals(1, callbacks.size());
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) callbacks.get(0).get("tags");
        assertTrue(tags.contains("production"));
    }

    @Test
    @DisplayName("test_on_decorator_basic")
    void testOnDecoratorBasic() {
        framework.on("test_event", kwargs -> "got: " + kwargs.get("message"), "handler");

        assertEquals("handler", framework.listCallbacks("test_event").get(0).get("name"));
    }

    @Test
    @DisplayName("test_on_decorator_with_priority")
    void testOnDecoratorWithPriority() {
        framework.on("event", kwargs -> null, 10, false, "default", null,
                null, null, null, 0, 0.0, null, "high_priority");
        framework.on("event", kwargs -> null, 1, false, "default", null,
                null, null, null, 0, 0.0, null, "low_priority");

        List<Map<String, Object>> callbacks = framework.listCallbacks("event");

        assertEquals(10, callbacks.get(0).get("priority"));
        assertEquals(1, callbacks.get(1).get("priority"));
    }

    @Test
    @DisplayName("test_on_decorator_with_namespace_and_tags")
    void testOnDecoratorWithNamespaceAndTags() {
        framework.on("event", kwargs -> null, 0, false, "custom", Set.of("tag1"),
                null, null, null, 0, 0.0, null, "handler");

        Map<String, Object> callback = framework.listCallbacks("event").get(0);
        assertEquals("custom", callback.get("namespace"));
        assertTrue(((List<?>) callback.get("tags")).contains("tag1"));
    }

    @Test
    @DisplayName("test_on_decorator_preserves_function")
    void testOnDecoratorPreservesFunction() {
        Function<Map<String, Object>, Object> myHandler = kwargs -> (int) argsFrom(kwargs)[0] * 2;

        CallbackInfo info = framework.on("event", myHandler, "my_handler");

        assertSame(myHandler, info.getCallback());
        assertEquals(10, myHandler.apply(new HashMap<>(Map.of("_args", new Object[]{5}))));
    }

    @Test
    @DisplayName("test_emit_before_basic")
    void testEmitBeforeBasic() {
        List<String> callLog = new ArrayList<>();
        framework.on("processing", kwargs -> {
            callLog.add("event: " + argsFrom(kwargs)[0]);
            return null;
        }, "on_processing");
        Function<Map<String, Object>, Object> process = kwargs -> {
            callLog.add("process: " + argsFrom(kwargs)[0]);
            return Map.of("processed", argsFrom(kwargs)[0]);
        };

        Object result = framework.triggerOnCall("processing", process, false, true)
                .apply(new HashMap<>(Map.of("_args", new Object[]{"test"})));

        assertEquals(List.of("event: test", "process: test"), callLog);
        assertEquals(Map.of("processed", "test"), result);
    }

    @Test
    @DisplayName("test_emit_before_pass_args_false")
    void testEmitBeforePassArgsFalse() {
        List<Object[]> receivedArgs = new ArrayList<>();
        framework.on("event", kwargs -> {
            receivedArgs.add(argsFrom(kwargs));
            return null;
        }, "handler");
        Function<Map<String, Object>, Object> myFunc = kwargs -> argsFrom(kwargs)[0];

        framework.triggerOnCall("event", myFunc, false, false)
                .apply(new HashMap<>(Map.of("_args", new Object[]{"secret_data"})));

        assertEquals(0, receivedArgs.get(0).length);
    }

    @Test
    @DisplayName("test_emit_after_basic")
    void testEmitAfterBasic() {
        List<Object> receivedResults = new ArrayList<>();
        framework.on("data_ready", kwargs -> {
            receivedResults.add(kwargs.get("result"));
            return null;
        }, "on_ready");

        Object result = framework.emits("data_ready", kwargs -> Map.of("status", "done"), "result", false)
                .apply(new HashMap<>());

        assertEquals(Map.of("status", "done"), result);
        assertEquals(List.of(Map.of("status", "done")), receivedResults);
    }

    @Test
    @DisplayName("test_emit_after_custom_result_key")
    void testEmitAfterCustomResultKey() {
        List<Map<String, Object>> received = new ArrayList<>();
        framework.on("event", kwargs -> {
            received.add(new HashMap<>(kwargs));
            return null;
        }, "handler");

        framework.emits("event", kwargs -> Map.of("value", 42), "data", false).apply(new HashMap<>());

        assertEquals(Map.of("value", 42), received.get(0).get("data"));
    }

    @Test
    @DisplayName("test_emit_after_pass_args")
    void testEmitAfterPassArgs() {
        List<Map<String, Object>> received = new ArrayList<>();
        framework.on("event", kwargs -> {
            received.add(new HashMap<>(kwargs));
            return null;
        }, "handler");
        Function<Map<String, Object>, Object> process = kwargs -> "result";
        Map<String, Object> call = new HashMap<>(Map.of("_args", new Object[]{"my_input"}, "extra", "custom"));

        framework.emits("event", process, "result", true).apply(call);

        assertEquals("my_input", ((Object[]) received.get(0).get("_args"))[0]);
        assertEquals("custom", received.get(0).get("extra"));
        assertEquals("result", received.get(0).get("result"));
    }

    @Test
    @DisplayName("test_emit_around_basic")
    void testEmitAroundBasic() {
        List<String> eventLog = new ArrayList<>();
        framework.on("start", kwargs -> {
            eventLog.add("start");
            return null;
        }, "on_start");
        framework.on("end", kwargs -> {
            eventLog.add("end: " + kwargs.get("result"));
            return null;
        }, "on_end");
        Function<Map<String, Object>, Object> process = kwargs -> {
            eventLog.add("processing");
            return "done";
        };

        Object result = framework.emitAround("start", "end", process, false, true, null).apply(new HashMap<>());

        assertEquals("done", result);
        assertEquals(List.of("start", "processing", "end: done"), eventLog);
    }

    @Test
    @DisplayName("test_emit_around_with_error_event")
    void testEmitAroundWithErrorEvent() {
        List<String> errorLog = new ArrayList<>();
        framework.on("start", kwargs -> {
            errorLog.add("started");
            return null;
        }, "on_start");
        framework.on("error", kwargs -> {
            errorLog.add("error: " + kwargs.get("error").getClass().getSimpleName());
            return null;
        }, "on_error");
        Function<Map<String, Object>, Object> failingProcess = kwargs -> {
            throw new IllegalArgumentException("Something went wrong");
        };

        assertThrows(IllegalArgumentException.class,
                () -> framework.emitAround("start", "end", failingProcess, false, true, "error").apply(new HashMap<>()));

        assertTrue(errorLog.contains("started"));
        assertTrue(errorLog.contains("error: IllegalArgumentException"));
    }

    @Test
    @DisplayName("test_emit_around_pass_args")
    void testEmitAroundPassArgs() {
        List<String> received = new ArrayList<>();
        framework.on("before", kwargs -> {
            received.add("before: " + argsFrom(kwargs)[0] + ", " + argsFrom(kwargs)[1]);
            return null;
        }, "on_before");
        framework.on("after", kwargs -> {
            received.add("after: " + argsFrom(kwargs)[0] + ", " + argsFrom(kwargs)[1] + ", result=" + kwargs.get("result"));
            return null;
        }, "on_after");

        Object result = framework.emitAround("before", "after",
                        kwargs -> (int) argsFrom(kwargs)[0] + (int) argsFrom(kwargs)[1],
                        true, true, null)
                .apply(new HashMap<>(Map.of("_args", new Object[]{3, 4})));

        assertEquals(7, result);
        assertTrue(received.contains("before: 3, 4"));
        assertTrue(received.contains("after: 3, 4, result=7"));
    }

    @Test
    @DisplayName("test_emit_around_pass_args_false_before")
    void testEmitAroundPassArgsFalseBefore() {
        List<Object[]> receivedBefore = new ArrayList<>();
        List<Map<String, Object>> receivedAfter = new ArrayList<>();
        framework.on("before", kwargs -> {
            receivedBefore.add(argsFrom(kwargs));
            return null;
        }, "on_before");
        framework.on("after", kwargs -> {
            receivedAfter.add(new HashMap<>(kwargs));
            return null;
        }, "on_after");

        framework.emitAround("before", "after", kwargs -> Map.of("processed", argsFrom(kwargs)[0]), false, true, null)
                .apply(new HashMap<>(Map.of("_args", new Object[]{"secret"})));

        assertEquals(0, receivedBefore.get(0).length);
        assertTrue(receivedAfter.get(0).containsKey("result"));
    }

    @Test
    @DisplayName("test_emit_around_pass_result_false")
    void testEmitAroundPassResultFalse() {
        List<Map<String, Object>> receivedAfter = new ArrayList<>();
        framework.on("before", kwargs -> null, "on_before");
        framework.on("after", kwargs -> {
            receivedAfter.add(new HashMap<>(kwargs));
            return null;
        }, "on_after");

        Object result = framework.emitAround("before", "after",
                        kwargs -> Map.of("status", "done"), false, false, null)
                .apply(new HashMap<>());

        assertEquals(Map.of("status", "done"), result);
        assertFalse(receivedAfter.get(0).containsKey("result"));
    }

    @Test
    @DisplayName("test_emit_around_pass_result_false_with_args")
    void testEmitAroundPassResultFalseWithArgs() {
        List<Map<String, Object>> receivedAfter = new ArrayList<>();
        framework.on("before", kwargs -> null, "on_before");
        framework.on("after", kwargs -> {
            receivedAfter.add(new HashMap<>(kwargs));
            return null;
        }, "on_after");

        Object result = framework.emitAround("before", "after",
                        kwargs -> (int) argsFrom(kwargs)[0] * 2, true, false, null)
                .apply(new HashMap<>(Map.of("_args", new Object[]{5})));

        assertEquals(10, result);
        assertEquals(5, ((Object[]) receivedAfter.get(0).get("_args"))[0]);
        assertFalse(receivedAfter.get(0).containsKey("result"));
    }

    @Test
    @DisplayName("test_emit_around_error_pass_args_false")
    void testEmitAroundErrorPassArgsFalse() {
        List<Map<String, Object>> receivedError = new ArrayList<>();
        framework.on("start", kwargs -> null, "on_start");
        framework.on("error", kwargs -> {
            receivedError.add(new HashMap<>(kwargs));
            return null;
        }, "on_error");

        assertThrows(IllegalArgumentException.class,
                () -> framework.emitAround("start", "end", kwargs -> {
                    throw new IllegalArgumentException("Failed!");
                }, false, true, "error").apply(new HashMap<>(Map.of("_args", new Object[]{"secret"}))));

        assertTrue(receivedError.get(0).get("error") instanceof IllegalArgumentException);
        assertEquals(0, argsFrom(receivedError.get(0)).length);
    }

    @Test
    @DisplayName("test_on_decorator_wrapper_called")
    void testOnDecoratorWrapperCalled() {
        Function<Map<String, Object>, Object> myCallback = kwargs -> (int) argsFrom(kwargs)[0] * 2;
        framework.on("event", myCallback, "my_callback");

        assertEquals(10, myCallback.apply(new HashMap<>(Map.of("_args", new Object[]{5}))));
    }

    @Test
    @DisplayName("test_register_with_callback_filters")
    void testRegisterWithCallbackFilters() {
        ValidationFilter validator = new ValidationFilter(kwargs -> (int) kwargs.get("value") > 0);
        Function<Map<String, Object>, Object> callback = kwargs -> kwargs.get("value");
        framework.register("event", callback, 0, false, "default", null,
                List.of(validator), null, null, 0, 0.0, null, "callback");

        assertEquals(List.of(10), framework.trigger("event", new HashMap<>(Map.of("value", 10))));
        assertTrue(framework.trigger("event", new HashMap<>(Map.of("value", -5))).isEmpty());
    }

    @Test
    @DisplayName("test_sync_register_with_filters")
    void testSyncRegisterWithFilters() {
        ValidationFilter validator = new ValidationFilter(kwargs -> true);
        Function<Map<String, Object>, Object> callback = kwargs -> null;

        framework.registerSync("event", callback, 0, false, "default", null,
                List.of(validator), null, null, 0, 0.0, null, "callback");

        assertTrue(framework.getCallbackFilters().containsKey(callback));
    }

    @Test
    @DisplayName("test_register_logs_message")
    void testRegisterLogsMessage() {
        CallbackFramework loggingFramework = new CallbackFramework(true, true);

        loggingFramework.register("event", kwargs -> null, "callback");

        assertEquals(1, loggingFramework.listCallbacks("event").size());
    }

    @Test
    @DisplayName("test_unregister_logs_message")
    void testUnregisterLogsMessage() {
        CallbackFramework loggingFramework = new CallbackFramework(true, true);
        Function<Map<String, Object>, Object> callback = kwargs -> null;
        loggingFramework.register("event", callback, "callback");

        loggingFramework.unregister("event", callback);

        assertTrue(loggingFramework.listCallbacks("event").isEmpty());
    }

    @Test
    @DisplayName("test_unregister_removes_from_chain")
    void testUnregisterRemovesFromChain() {
        Function<Map<String, Object>, Object> callback = kwargs -> "result";
        framework.register("event", callback, 0, false, "default", null,
                null, ctx -> {}, null, 0, 0.0, null, "callback");
        assertTrue(framework.getChains().containsKey("event"));

        framework.unregister("event", callback);

        CallbackChain chain = framework.getChains().get("event");
        assertTrue(chain == null || chain.getCallbacks().stream().noneMatch(ci -> ci.getCallback() == callback));
    }

    @Test
    @DisplayName("test_register_with_rollback_handler")
    void testRegisterWithRollbackHandler() {
        Function<Map<String, Object>, Object> myCallback = kwargs ->
                ChainResult.builder().action(ChainAction.ROLLBACK).error(new RuntimeException("fail")).build();
        framework.register("chain_event", myCallback, 0, false, "default", null,
                null, ctx -> {}, null, 0, 0.0, null, "my_callback");

        ChainResult result = framework.triggerChain("chain_event", new Object[0], new HashMap<>());

        assertEquals(ChainAction.ROLLBACK, result.getAction());
    }

    @Test
    @DisplayName("test_on_decorator_with_handlers")
    void testOnDecoratorWithHandlers() {
        List<String> handlerCalls = new ArrayList<>();
        Function<Map<String, Object>, Object> myCallback = kwargs -> {
            throw new IllegalArgumentException("Test error");
        };
        framework.register("event", myCallback, 0, false, "default", null,
                null,
                ctx -> handlerCalls.add("rollback"),
                context -> {
                    handlerCalls.add("error: " + context.exception().getMessage());
                    return "recovered";
                },
                0, 0.0, null, "my_callback");

        ChainResult result = framework.triggerChain("event", new Object[0], new HashMap<>());

        assertEquals(ChainAction.CONTINUE, result.getAction());
        assertTrue(handlerCalls.contains("error: Test error"));
    }
}
