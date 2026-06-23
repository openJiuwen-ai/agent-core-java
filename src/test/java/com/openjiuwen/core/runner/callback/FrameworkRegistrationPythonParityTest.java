/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.core.runner.callback.test_framework_registration} in
 * {@code tests/unit_tests/core/runner/callback/test_framework_registration.py}.</p>
 */
class FrameworkRegistrationPythonParityTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "test_register_basic",
            "test_register_with_priority",
            "test_register_with_namespace",
            "test_register_with_tags",
            "test_register_with_once",
            "test_register_with_retry_settings",
            "test_unregister_callback",
            "test_unregister_nonexistent_callback",
            "test_unregister_namespace",
            "test_unregister_by_tags",
            "test_on_decorator_basic",
            "test_on_decorator_with_priority",
            "test_on_decorator_with_namespace_and_tags",
            "test_on_decorator_preserves_function",
            "test_emit_before_basic",
            "test_emit_before_pass_args_false",
            "test_emit_after_basic",
            "test_emit_after_custom_result_key",
            "test_emit_after_pass_args",
            "test_emit_around_basic",
            "test_emit_around_with_error_event",
            "test_emit_around_pass_args",
            "test_emit_around_pass_args_false_before",
            "test_emit_around_pass_result_false",
            "test_emit_around_pass_result_false_with_args",
            "test_emit_around_error_pass_args_false",
            "test_on_decorator_wrapper_called",
            "test_register_with_callback_filters",
            "test_sync_register_with_filters",
            "test_register_logs_message",
            "test_unregister_logs_message",
            "test_unregister_removes_from_chain",
            "test_register_with_rollback_handler",
            "test_on_decorator_with_handlers"
    );

    @TestFactory
    Collection<DynamicTest> pythonFrameworkRegistrationCases() {
        return PYTHON_TESTS.stream()
                .map(name -> DynamicTest.dynamicTest(name, () -> runPythonCase(name)))
                .toList();
    }

    private void runPythonCase(String name) {
        switch (name) {
            case "test_register_basic" -> registerBasic();
            case "test_register_with_priority" -> registerWithPriority();
            case "test_register_with_namespace" -> registerWithNamespace();
            case "test_register_with_tags" -> registerWithTags();
            case "test_register_with_once" -> registerWithOnce();
            case "test_register_with_retry_settings" -> registerWithRetrySettings();
            case "test_unregister_callback" -> unregisterCallback();
            case "test_unregister_nonexistent_callback" -> unregisterNonexistentCallback();
            case "test_unregister_namespace" -> unregisterNamespace();
            case "test_unregister_by_tags" -> unregisterByTags();
            case "test_on_decorator_basic" -> onDecoratorBasic();
            case "test_on_decorator_with_priority" -> onDecoratorWithPriority();
            case "test_on_decorator_with_namespace_and_tags" -> onDecoratorWithNamespaceAndTags();
            case "test_on_decorator_preserves_function" -> onDecoratorPreservesFunction();
            case "test_emit_before_basic" -> emitBeforeBasic();
            case "test_emit_before_pass_args_false" -> emitBeforePassArgsFalse();
            case "test_emit_after_basic" -> emitAfterBasic();
            case "test_emit_after_custom_result_key" -> emitAfterCustomResultKey();
            case "test_emit_after_pass_args" -> emitAfterPassArgs();
            case "test_emit_around_basic" -> emitAroundBasic();
            case "test_emit_around_with_error_event" -> emitAroundWithErrorEvent();
            case "test_emit_around_pass_args" -> emitAroundPassArgs();
            case "test_emit_around_pass_args_false_before" -> emitAroundPassArgsFalseBefore();
            case "test_emit_around_pass_result_false" -> emitAroundPassResultFalse();
            case "test_emit_around_pass_result_false_with_args" -> emitAroundPassResultFalseWithArgs();
            case "test_emit_around_error_pass_args_false" -> emitAroundErrorPassArgsFalse();
            case "test_on_decorator_wrapper_called" -> onDecoratorWrapperCalled();
            case "test_register_with_callback_filters" -> registerWithCallbackFilters();
            case "test_sync_register_with_filters" -> syncRegisterWithFilters();
            case "test_register_logs_message" -> registerLogsMessage();
            case "test_unregister_logs_message" -> unregisterLogsMessage();
            case "test_unregister_removes_from_chain" -> unregisterRemovesFromChain();
            case "test_register_with_rollback_handler" -> registerWithRollbackHandler();
            case "test_on_decorator_with_handlers" -> onDecoratorWithHandlers();
            default -> throw new IllegalArgumentException("Unhandled Python test: " + name);
        }
    }

    private void registerBasic() {
        AsyncCallbackFramework framework = framework();
        register(framework, "test_event", "callback", kwargs -> "received: " + args(kwargs)[0]);

        List<Map<String, Object>> callbacks = framework.listCallbacks("test_event");

        assertEquals(1, callbacks.size());
        assertEquals("callback", callbacks.get(0).get("name"));
    }

    private void registerWithPriority() {
        AsyncCallbackFramework framework = framework();

        register(framework, "event", "low", 1, false, "default", Set.of(), null, kwargs -> null);
        register(framework, "event", "high", 10, false, "default", Set.of(), null, kwargs -> null);

        List<Map<String, Object>> callbacks = framework.listCallbacks("event");
        assertEquals("high", callbacks.get(0).get("name"));
        assertEquals("low", callbacks.get(1).get("name"));
    }

    private void registerWithNamespace() {
        AsyncCallbackFramework framework = framework();

        register(framework, "event", "callback", 0, false, "custom", Set.of(), null, kwargs -> null);

        assertEquals("custom", framework.listCallbacks("event").get(0).get("namespace"));
    }

    @SuppressWarnings("unchecked")
    private void registerWithTags() {
        AsyncCallbackFramework framework = framework();

        register(framework, "event", "callback", 0, false, "default", Set.of("tag1", "tag2"), null,
                kwargs -> null);

        assertEquals(Set.of("tag1", "tag2"), Set.copyOf((List<String>) framework.listCallbacks("event")
                .get(0).get("tags")));
    }

    private void registerWithOnce() {
        AsyncCallbackFramework framework = framework();

        register(framework, "event", "callback", 0, true, "default", Set.of(), null, kwargs -> null);

        assertEquals(Boolean.TRUE, framework.listCallbacks("event").get(0).get("once"));
    }

    private void registerWithRetrySettings() {
        AsyncCallbackFramework framework = framework();

        register(framework, "event", "callback", 0, false, "default", Set.of(), 30.0, kwargs -> null,
                3, 1.0);

        Map<String, Object> callback = framework.listCallbacks("event").get(0);
        assertEquals(3, callback.get("max_retries"));
        assertEquals(30.0, callback.get("timeout"));
    }

    private void unregisterCallback() {
        AsyncCallbackFramework framework = framework();
        Function<Map<String, Object>, Object> callback1 = named("callback1", kwargs -> null);
        Function<Map<String, Object>, Object> callback2 = named("callback2", kwargs -> null);
        register(framework, "event", callback1);
        register(framework, "event", callback2);

        assertEquals(2, framework.listCallbacks("event").size());

        framework.unregister("event", callback1);

        List<Map<String, Object>> callbacks = framework.listCallbacks("event");
        assertEquals(1, callbacks.size());
        assertEquals("callback2", callbacks.get(0).get("name"));
    }

    private void unregisterNonexistentCallback() {
        AsyncCallbackFramework framework = framework();
        register(framework, "event", "callback", kwargs -> null);

        framework.unregister("event", named("other", kwargs -> null));

        assertEquals(1, framework.listCallbacks("event").size());
    }

    private void unregisterNamespace() {
        AsyncCallbackFramework framework = framework();
        register(framework, "event", "cb1", 0, false, "ns1", Set.of(), null, kwargs -> null);
        register(framework, "event", "cb2", 0, false, "ns1", Set.of(), null, kwargs -> null);
        register(framework, "event", "cb3", 0, false, "ns2", Set.of(), null, kwargs -> null);

        framework.unregisterNamespace("ns1");

        List<Map<String, Object>> callbacks = framework.listCallbacks("event");
        assertEquals(1, callbacks.size());
        assertEquals("ns2", callbacks.get(0).get("namespace"));
    }

    @SuppressWarnings("unchecked")
    private void unregisterByTags() {
        AsyncCallbackFramework framework = framework();
        register(framework, "event", "cb1", 0, false, "default", Set.of("debug"), null, kwargs -> null);
        register(framework, "event", "cb2", 0, false, "default", Set.of("debug", "verbose"), null,
                kwargs -> null);
        register(framework, "event", "cb3", 0, false, "default", Set.of("production"), null, kwargs -> null);

        framework.unregisterByTags(Set.of("debug"));

        List<Map<String, Object>> callbacks = framework.listCallbacks("event");
        assertEquals(1, callbacks.size());
        assertTrue(((List<String>) callbacks.get(0).get("tags")).contains("production"));
    }

    private void onDecoratorBasic() {
        AsyncCallbackFramework framework = framework();

        framework.on("test_event").apply(named("handler", kwargs -> "got: " + args(kwargs)[0]));

        List<Map<String, Object>> callbacks = framework.listCallbacks("test_event");
        assertEquals(1, callbacks.size());
        assertEquals("handler", callbacks.get(0).get("name"));
    }

    private void onDecoratorWithPriority() {
        AsyncCallbackFramework framework = framework();

        framework.on("event", 10, false, "default", Set.of(), List.of(), 0, 0.0, null, "")
                .apply(named("high_priority", kwargs -> null));
        framework.on("event", 1, false, "default", Set.of(), List.of(), 0, 0.0, null, "")
                .apply(named("low_priority", kwargs -> null));

        List<Map<String, Object>> callbacks = framework.listCallbacks("event");
        assertEquals(10, callbacks.get(0).get("priority"));
        assertEquals(1, callbacks.get(1).get("priority"));
    }

    private void onDecoratorWithNamespaceAndTags() {
        AsyncCallbackFramework framework = framework();

        framework.on("event", 0, false, "custom", Set.of("tag1"), List.of(), 0, 0.0, null, "")
                .apply(named("handler", kwargs -> null));

        Map<String, Object> callback = framework.listCallbacks("event").get(0);
        assertEquals("custom", callback.get("namespace"));
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) callback.get("tags");
        assertTrue(tags.contains("tag1"));
    }

    private void onDecoratorPreservesFunction() {
        AsyncCallbackFramework framework = framework();
        Function<Map<String, Object>, Object> original = named("my_handler", kwargs -> ((Integer) args(kwargs)[0]) * 2);

        Function<Map<String, Object>, Object> wrapper = framework.on("event").apply(original);

        assertEquals("my_handler", framework.listCallbacks("event").get(0).get("name"));
        assertEquals(6, wrapper.apply(kwargsWithArgs(3)));
    }

    private void emitBeforeBasic() {
        AsyncCallbackFramework framework = framework();
        List<String> callLog = new ArrayList<>();
        framework.on("processing").apply(named("on_processing", kwargs -> {
            callLog.add("event: " + args(kwargs)[0]);
            return null;
        }));
        Function<Map<String, Object>, Object> process = framework.emitBefore("processing", true, Map.of())
                .apply(named("process", kwargs -> {
                    Object data = args(kwargs)[0];
                    callLog.add("process: " + data);
                    return mapOf("processed", data);
                }));

        Object result = process.apply(kwargsWithArgs("test"));

        assertEquals(List.of("event: test", "process: test"), callLog);
        assertEquals(mapOf("processed", "test"), result);
    }

    private void emitBeforePassArgsFalse() {
        AsyncCallbackFramework framework = framework();
        List<Map<String, Object>> received = new ArrayList<>();
        framework.on("event").apply(named("handler", kwargs -> {
            received.add(mapOf("args", args(kwargs), "kwargs", pythonKwargs(kwargs)));
            return null;
        }));
        Function<Map<String, Object>, Object> wrapped = framework.emitBefore("event", false, Map.of())
                .apply(named("my_func", kwargs -> args(kwargs)[0]));

        wrapped.apply(kwargsWithArgs("secret_data"));

        assertArrayEquals(new Object[0], (Object[]) received.get(0).get("args"));
        assertEquals(mapOf("session", null), received.get(0).get("kwargs"));
    }

    private void emitAfterBasic() {
        AsyncCallbackFramework framework = framework();
        List<Object> receivedResults = new ArrayList<>();
        framework.on("data_ready").apply(named("on_ready", kwargs -> {
            receivedResults.add(kwargs.get("result"));
            return null;
        }));
        Function<Map<String, Object>, Object> process = framework.emitAfter("data_ready", "result", null, false, null,
                        Map.of())
                .apply(named("process", kwargs -> mapOf("status", "done")));

        Object result = process.apply(Map.of());

        assertEquals(mapOf("status", "done"), result);
        assertEquals(List.of(mapOf("status", "done")), receivedResults);
    }

    private void emitAfterCustomResultKey() {
        AsyncCallbackFramework framework = framework();
        List<Map<String, Object>> received = new ArrayList<>();
        framework.on("event").apply(named("handler", kwargs -> {
            received.add(pythonKwargs(kwargs));
            return null;
        }));
        Function<Map<String, Object>, Object> process = framework.emitAfter("event", "data", null, false, null,
                        Map.of())
                .apply(named("process", kwargs -> mapOf("value", 42)));

        process.apply(Map.of());

        assertEquals(mapOf("value", 42), received.get(0).get("data"));
    }

    private void emitAfterPassArgs() {
        AsyncCallbackFramework framework = framework();
        List<Map<String, Object>> received = new ArrayList<>();
        framework.on("event").apply(named("handler", kwargs -> {
            received.add(mapOf("args", args(kwargs), "kwargs", pythonKwargs(kwargs)));
            return null;
        }));
        Function<Map<String, Object>, Object> process = framework.emitAfter("event", "result", null, true, null,
                        Map.of())
                .apply(named("process", kwargs -> "result"));

        process.apply(kwargsWithArgsAndKwargs(new Object[]{"my_input"}, mapOf("extra", "custom")));

        assertArrayEquals(new Object[]{"my_input"}, (Object[]) received.get(0).get("args"));
        assertEquals("custom", ((Map<?, ?>) received.get(0).get("kwargs")).get("extra"));
        assertEquals("result", ((Map<?, ?>) received.get(0).get("kwargs")).get("result"));
    }

    private void emitAroundBasic() {
        AsyncCallbackFramework framework = framework();
        List<String> eventLog = new ArrayList<>();
        framework.on("start").apply(named("on_start", kwargs -> {
            eventLog.add("start");
            return null;
        }));
        framework.on("end").apply(named("on_end", kwargs -> {
            eventLog.add("end: " + kwargs.get("result"));
            return null;
        }));
        Function<Map<String, Object>, Object> process = framework.emitAround("start", "end", false, true, null)
                .apply(named("process", kwargs -> {
                    eventLog.add("processing");
                    return "done";
                }));

        Object result = process.apply(Map.of());

        assertEquals("done", result);
        assertEquals(List.of("start", "processing", "end: done"), eventLog);
    }

    private void emitAroundWithErrorEvent() {
        AsyncCallbackFramework framework = framework();
        List<String> errorLog = new ArrayList<>();
        framework.on("start").apply(named("on_start", kwargs -> {
            errorLog.add("started");
            return null;
        }));
        framework.on("error").apply(named("on_error", kwargs -> {
            errorLog.add("error: " + kwargs.get("error").getClass().getSimpleName());
            return null;
        }));
        Function<Map<String, Object>, Object> failingProcess = framework.emitAround("start", "end", false, true,
                        "error")
                .apply(named("failing_process", kwargs -> {
                    throw new IllegalArgumentException("Something went wrong");
                }));

        assertThrowsIllegalArgument(failingProcess);
        assertTrue(errorLog.contains("started"));
        assertTrue(errorLog.contains("error: IllegalArgumentException"));
    }

    private void emitAroundPassArgs() {
        AsyncCallbackFramework framework = framework();
        List<String> received = new ArrayList<>();
        framework.on("before").apply(named("on_before", kwargs -> {
            received.add("before: " + args(kwargs)[0] + ", " + args(kwargs)[1]);
            return null;
        }));
        framework.on("after").apply(named("on_after", kwargs -> {
            received.add("after: " + args(kwargs)[0] + ", " + args(kwargs)[1] + ", result="
                    + kwargs.get("result"));
            return null;
        }));
        Function<Map<String, Object>, Object> add = framework.emitAround("before", "after", true, true, null)
                .apply(named("add", kwargs -> ((Integer) args(kwargs)[0]) + ((Integer) args(kwargs)[1])));

        Object result = add.apply(kwargsWithArgs(3, 4));

        assertEquals(7, result);
        assertTrue(received.contains("before: 3, 4"));
        assertTrue(received.contains("after: 3, 4, result=7"));
    }

    private void emitAroundPassArgsFalseBefore() {
        AsyncCallbackFramework framework = framework();
        List<Map<String, Object>> receivedBefore = new ArrayList<>();
        List<Map<String, Object>> receivedAfter = new ArrayList<>();
        framework.on("before").apply(named("on_before", kwargs -> {
            receivedBefore.add(mapOf("args", args(kwargs), "kwargs", pythonKwargs(kwargs)));
            return null;
        }));
        framework.on("after").apply(named("on_after", kwargs -> {
            receivedAfter.add(pythonKwargs(kwargs));
            return null;
        }));
        Function<Map<String, Object>, Object> wrapped = framework.emitAround("before", "after", false, true, null)
                .apply(named("my_func", kwargs -> mapOf("processed", args(kwargs)[0])));

        wrapped.apply(kwargsWithArgs("secret"));

        assertArrayEquals(new Object[0], (Object[]) receivedBefore.get(0).get("args"));
        assertEquals(mapOf("session", null), receivedBefore.get(0).get("kwargs"));
        assertTrue(receivedAfter.get(0).containsKey("result"));
    }

    private void emitAroundPassResultFalse() {
        AsyncCallbackFramework framework = framework();
        List<Map<String, Object>> receivedAfter = new ArrayList<>();
        framework.on("before").apply(named("on_before", kwargs -> null));
        framework.on("after").apply(named("on_after", kwargs -> {
            receivedAfter.add(mapOf("args", args(kwargs), "kwargs", pythonKwargs(kwargs)));
            return null;
        }));
        Function<Map<String, Object>, Object> wrapped = framework.emitAround("before", "after", false, false, null)
                .apply(named("my_func", kwargs -> mapOf("status", "done")));

        Object result = wrapped.apply(Map.of());

        assertEquals(mapOf("status", "done"), result);
        assertArrayEquals(new Object[0], (Object[]) receivedAfter.get(0).get("args"));
        assertEquals(mapOf("session", null), receivedAfter.get(0).get("kwargs"));
    }

    private void emitAroundPassResultFalseWithArgs() {
        AsyncCallbackFramework framework = framework();
        List<Map<String, Object>> receivedAfter = new ArrayList<>();
        framework.on("before").apply(named("on_before", kwargs -> null));
        framework.on("after").apply(named("on_after", kwargs -> {
            receivedAfter.add(mapOf("x", args(kwargs)[0], "kwargs", pythonKwargs(kwargs)));
            return null;
        }));
        Function<Map<String, Object>, Object> wrapped = framework.emitAround("before", "after", true, false, null)
                .apply(named("my_func", kwargs -> ((Integer) args(kwargs)[0]) * 2));

        Object result = wrapped.apply(kwargsWithArgs(5));

        assertEquals(10, result);
        assertEquals(5, receivedAfter.get(0).get("x"));
        assertFalse(((Map<?, ?>) receivedAfter.get(0).get("kwargs")).containsKey("result"));
    }

    private void emitAroundErrorPassArgsFalse() {
        AsyncCallbackFramework framework = framework();
        List<Map<String, Object>> receivedError = new ArrayList<>();
        framework.on("start").apply(named("on_start", kwargs -> null));
        framework.on("error").apply(named("on_error", kwargs -> {
            receivedError.add(pythonKwargs(kwargs));
            return null;
        }));
        Function<Map<String, Object>, Object> failing = framework.emitAround("start", "end", false, true, "error")
                .apply(named("failing_func", kwargs -> {
                    throw new IllegalArgumentException("Failed!");
                }));

        assertThrowsIllegalArgument(() -> failing.apply(kwargsWithArgs("secret")));

        assertTrue(receivedError.get(0).containsKey("error"));
        assertInstanceOf(IllegalArgumentException.class, receivedError.get(0).get("error"));
    }

    private void onDecoratorWrapperCalled() {
        AsyncCallbackFramework framework = framework();
        Function<Map<String, Object>, Object> wrapped = framework.on("event")
                .apply(named("my_callback", kwargs -> ((Integer) args(kwargs)[0]) * 2));

        assertEquals(10, wrapped.apply(kwargsWithArgs(5)));
    }

    private void registerWithCallbackFilters() {
        AsyncCallbackFramework framework = framework();
        ValidationFilter validator = new ValidationFilter((args, kwargs) -> ((Integer) args[0]) > 0);

        register(framework, "event", "callback", 0, false, "default", Set.of(), null,
                kwargs -> args(kwargs)[0], 0, 0.0, List.of(validator));

        assertEquals(List.of(10), framework.triggerResults("event", new Object[]{10}, Map.of()));
        assertEquals(List.of(), framework.triggerResults("event", new Object[]{-5}, Map.of()));
    }

    private void syncRegisterWithFilters() {
        AsyncCallbackFramework framework = framework();
        ValidationFilter validator = new ValidationFilter((args, kwargs) -> ((Integer) args[0]) > 0);
        Function<Map<String, Object>, Object> callback = named("callback", kwargs -> args(kwargs)[0]);

        framework.registerSync("event", callback, 0, false, "default", Set.of(), List.of(validator),
                null, null, 0, 0.0, null, "");

        assertTrue(framework.getCallbackFilters().containsKey(callback));
    }

    private void registerLogsMessage() {
        AsyncCallbackFramework framework = frameworkWithLogging();

        try (CapturedLogs logs = captureFrameworkLogs(Level.INFO)) {
            register(framework, "event", "callback", kwargs -> null);
            assertTrue(logs.contains("Registered callback"));
        }
    }

    private void unregisterLogsMessage() {
        AsyncCallbackFramework framework = frameworkWithLogging();
        Function<Map<String, Object>, Object> callback = named("callback", kwargs -> null);
        register(framework, "event", callback);

        try (CapturedLogs logs = captureFrameworkLogs(Level.INFO)) {
            framework.unregister("event", callback);
            assertTrue(logs.contains("Unregistered callback"));
        }
    }

    private void unregisterRemovesFromChain() {
        AsyncCallbackFramework framework = framework();
        Function<Map<String, Object>, Object> callback = named("callback", kwargs -> "result");
        Function<Map<String, Object>, Object> rollback = named("rollback", kwargs -> null);
        register(framework, "event", callback, 0, false, "default", Set.of(), null,
                0, 0.0, List.of(), rollback, null);

        assertTrue(framework.getChains().containsKey("event"));

        framework.unregister("event", callback);

        CallbackChain chain = framework.getChains().get("event");
        if (chain != null) {
            assertFalse(chain.getCallbacks().stream().map(CallbackInfo::getCallback).toList().contains(callback));
        }
    }

    private void registerWithRollbackHandler() {
        AsyncCallbackFramework framework = framework();
        Function<Map<String, Object>, Object> callback = named("my_callback", kwargs -> ChainResult.builder()
                .action(ChainAction.ROLLBACK)
                .error(new IllegalStateException("fail"))
                .build());
        Function<Map<String, Object>, Object> rollback = named("my_rollback", kwargs -> null);

        register(framework, "chain_event", callback, 0, false, "default", Set.of(), null,
                0, 0.0, List.of(), rollback, null);

        ChainResult result = framework.triggerChain("chain_event", new Object[0], Map.of());
        assertEquals(ChainAction.ROLLBACK, result.getAction());
    }

    private void onDecoratorWithHandlers() {
        AsyncCallbackFramework framework = framework();
        List<String> handlerCalls = new ArrayList<>();
        Function<Map<String, Object>, Object> errorHandler = named("error_handler", kwargs -> {
            Exception error = (Exception) kwargs.get("_error");
            handlerCalls.add("error: " + error.getMessage());
            return "recovered";
        });
        Function<Map<String, Object>, Object> rollbackHandler = named("rollback_handler", kwargs -> {
            handlerCalls.add("rollback");
            return null;
        });

        framework.onChain("event", 0, false, "default", Set.of(), rollbackHandler, errorHandler,
                        0, 0.0, null, "")
                .apply(named("my_callback", kwargs -> {
                    throw new IllegalArgumentException("Test error");
                }));

        ChainResult result = framework.triggerChain("event", new Object[0], Map.of());
        assertEquals(ChainAction.CONTINUE, result.getAction());
        assertTrue(handlerCalls.contains("error: Test error"));
        assertFalse(handlerCalls.contains("rollback"));
    }

    private static AsyncCallbackFramework framework() {
        return new AsyncCallbackFramework(false, false);
    }

    private static AsyncCallbackFramework frameworkWithLogging() {
        return new AsyncCallbackFramework(false, true);
    }

    private static void register(
            AsyncCallbackFramework framework,
            String event,
            String name,
            Function<Map<String, Object>, Object> callback
    ) {
        register(framework, event, named(name, callback));
    }

    private static void register(
            AsyncCallbackFramework framework,
            String event,
            Function<Map<String, Object>, Object> callback
    ) {
        register(framework, event, callback, 0, false, "default", Set.of(), null, 0, 0.0, List.of(), null, null);
    }

    private static void register(
            AsyncCallbackFramework framework,
            String event,
            String name,
            int priority,
            boolean once,
            String namespace,
            Set<String> tags,
            Double timeout,
            Function<Map<String, Object>, Object> callback
    ) {
        register(framework, event, name, priority, once, namespace, tags, timeout, callback, 0, 0.0);
    }

    private static void register(
            AsyncCallbackFramework framework,
            String event,
            String name,
            int priority,
            boolean once,
            String namespace,
            Set<String> tags,
            Double timeout,
            Function<Map<String, Object>, Object> callback,
            int maxRetries,
            double retryDelay
    ) {
        register(framework, event, name, priority, once, namespace, tags, timeout, callback, maxRetries, retryDelay,
                List.of());
    }

    private static void register(
            AsyncCallbackFramework framework,
            String event,
            String name,
            int priority,
            boolean once,
            String namespace,
            Set<String> tags,
            Double timeout,
            Function<Map<String, Object>, Object> callback,
            int maxRetries,
            double retryDelay,
            List<EventFilter> filters
    ) {
        register(framework, event, named(name, callback), priority, once, namespace, tags, timeout,
                maxRetries, retryDelay, filters, null, null);
    }

    private static void register(
            AsyncCallbackFramework framework,
            String event,
            Function<Map<String, Object>, Object> callback,
            int priority,
            boolean once,
            String namespace,
            Set<String> tags,
            Double timeout,
            int maxRetries,
            double retryDelay,
            List<EventFilter> filters,
            Function<Map<String, Object>, Object> rollbackHandler,
            Function<Map<String, Object>, Object> errorHandler
    ) {
        framework.registerSync(event, callback, priority, once, namespace, tags, filters,
                rollbackHandler, errorHandler, maxRetries, retryDelay, timeout, "");
    }

    private static Map<String, Object> kwargsWithArgs(Object... args) {
        return kwargsWithArgsAndKwargs(args, Map.of());
    }

    private static Map<String, Object> kwargsWithArgsAndKwargs(Object[] args, Map<String, Object> kwargs) {
        Map<String, Object> map = new LinkedHashMap<>(kwargs);
        map.put("_args", args.clone());
        return map;
    }

    private static Object[] args(Map<String, Object> kwargs) {
        Object value = kwargs.get("_args");
        return value instanceof Object[] values ? values : new Object[0];
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

    private static void assertThrowsIllegalArgument(Function<Map<String, Object>, Object> function) {
        assertThrowsIllegalArgument(() -> function.apply(Map.of()));
    }

    private static void assertThrowsIllegalArgument(Runnable runnable) {
        try {
            runnable.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    private static CapturedLogs captureFrameworkLogs(Level level) {
        Logger logger = (Logger) LoggerFactory.getLogger(AsyncCallbackFramework.class);
        return new CapturedLogs(logger, level);
    }

    private static Function<Map<String, Object>, Object> named(
            String name,
            Function<Map<String, Object>, Object> delegate
    ) {
        return new NamedCallback(name, delegate);
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

    private static final class CapturedLogs implements AutoCloseable {

        private final Logger logger;

        private final Level previousLevel;

        private final ListAppender<ILoggingEvent> appender;

        private CapturedLogs(Logger logger, Level level) {
            this.logger = logger;
            this.previousLevel = logger.getLevel();
            this.appender = new ListAppender<>();
            this.appender.start();
            this.logger.setLevel(level);
            this.logger.addAppender(appender);
        }

        private boolean contains(String text) {
            return appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains(text));
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }
}
