/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.runner.callback;

import com.openjiuwen.core.runner.callback.CallbackFramework;
import com.openjiuwen.core.runner.callback.HookType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Callback framework hooks test cases.
 *
 * <p>Mirrors Python's {@code test_framework_hooks.py} in
 * {@code tests/unit_tests/core/runner/callback/test_framework_hooks}.</p>
 */
@DisplayName("Framework Hooks Tests")
class TestFrameworkHooks {

    private CallbackFramework framework;

    @BeforeEach
    void setUp() {
        framework = new CallbackFramework(true, false);
    }

    @Test
    @DisplayName("test_before_hook_executes_before_callback")
    void testBeforeHookExecutesBeforeCallback() {
        List<String> executionOrder = new ArrayList<>();
        framework.addHook("event", HookType.BEFORE, kwargs -> executionOrder.add("before_hook"));
        framework.on("event", kwargs -> {
            executionOrder.add("callback");
            return null;
        }, "callback");

        framework.trigger("event");

        assertEquals(List.of("before_hook", "callback"), executionOrder);
    }

    @Test
    @DisplayName("test_before_hook_receives_args")
    void testBeforeHookReceivesArgs() {
        Map<String, Object> received = new HashMap<>();
        framework.addHook("event", HookType.BEFORE, kwargs -> received.putAll(kwargs));
        framework.on("event", kwargs -> null, "callback");

        framework.trigger("event", new Object[]{"arg1"}, new HashMap<>(Map.of("key", "value")));

        assertArrayEquals(new Object[]{"arg1"}, (Object[]) received.get("_args"));
        assertEquals("value", received.get("key"));
    }

    @Test
    @DisplayName("test_multiple_before_hooks")
    void testMultipleBeforeHooks() {
        List<String> order = new ArrayList<>();
        framework.addHook("event", HookType.BEFORE, kwargs -> order.add("hook1"));
        framework.addHook("event", HookType.BEFORE, kwargs -> order.add("hook2"));
        framework.on("event", kwargs -> {
            order.add("callback");
            return null;
        }, "callback");

        framework.trigger("event");

        assertEquals(List.of("hook1", "hook2", "callback"), order);
    }

    @Test
    @DisplayName("test_after_hook_executes_after_callback")
    void testAfterHookExecutesAfterCallback() {
        List<String> executionOrder = new ArrayList<>();
        framework.on("event", kwargs -> {
            executionOrder.add("callback");
            return "result";
        }, "callback");
        framework.addHook("event", HookType.AFTER, kwargs -> executionOrder.add("after_hook"));

        framework.trigger("event");

        assertEquals(List.of("callback", "after_hook"), executionOrder);
    }

    @Test
    @DisplayName("test_after_hook_receives_results")
    void testAfterHookReceivesResults() {
        List<Object> receivedResults = new ArrayList<>();
        framework.on("event", kwargs -> "result1", "callback1");
        framework.on("event", kwargs -> "result2", "callback2");
        framework.addHook("event", HookType.AFTER, kwargs -> receivedResults.addAll((List<?>) kwargs.get("_results")));

        framework.trigger("event");

        assertEquals(List.of("result1", "result2"), receivedResults);
    }

    @Test
    @DisplayName("test_error_hook_on_callback_exception")
    void testErrorHookOnCallbackException() {
        Object[] errorReceived = new Object[1];
        framework.on("event", kwargs -> {
            throw new IllegalArgumentException("Test error");
        }, "failing_callback");
        framework.addHook("event", HookType.ERROR, kwargs -> errorReceived[0] = kwargs.get("_error"));

        framework.trigger("event");

        assertInstanceOf(IllegalArgumentException.class, errorReceived[0]);
        assertEquals("Test error", ((Exception) errorReceived[0]).getMessage());
    }

    @Test
    @DisplayName("test_error_hook_receives_original_args")
    void testErrorHookReceivesOriginalArgs() {
        Map<String, Object> received = new HashMap<>();
        framework.on("event", kwargs -> {
            throw new RuntimeException("Error!");
        }, "failing_callback");
        framework.addHook("event", HookType.ERROR, kwargs -> received.putAll(kwargs));

        framework.trigger("event", new Object[]{"arg1"}, new HashMap<>(Map.of("key", "value")));

        assertArrayEquals(new Object[]{"arg1"}, (Object[]) received.get("_args"));
        assertEquals("value", received.get("key"));
        assertInstanceOf(RuntimeException.class, received.get("_error"));
    }

    @Test
    @DisplayName("test_error_hook_called_for_each_error")
    void testErrorHookCalledForEachError() {
        int[] errorCount = {0};
        framework.register("event", kwargs -> {
            throw new IllegalArgumentException("Error 1");
        }, 10, "failing1");
        framework.register("event", kwargs -> {
            throw new IllegalArgumentException("Error 2");
        }, 5, "failing2");
        framework.addHook("event", HookType.ERROR, kwargs -> errorCount[0]++);

        framework.trigger("event");

        assertEquals(2, errorCount[0]);
    }

    @Test
    @DisplayName("test_cleanup_hook_in_trigger_generator")
    void testCleanupHookInTriggerGenerator() {
        List<String> executionOrder = new ArrayList<>();
        framework.on("stream", kwargs -> {
            executionOrder.add("generating");
            return List.of("item1", "item2");
        }, "generator");
        framework.addHook("stream", HookType.CLEANUP, kwargs -> executionOrder.add("cleanup"));

        Iterator<Object> iterator = framework.triggerGenerator("stream", new Object[0], new HashMap<>());
        while (iterator.hasNext()) {
            executionOrder.add("received: " + iterator.next());
        }

        assertTrue(executionOrder.contains("cleanup"));
        assertEquals("cleanup", executionOrder.get(executionOrder.size() - 1));
    }

    @Test
    @DisplayName("test_sync_before_hook")
    void testSyncBeforeHook() {
        boolean[] called = {false};
        framework.addHook("event", HookType.BEFORE, kwargs -> called[0] = true);
        framework.on("event", kwargs -> null, "callback");

        framework.trigger("event");

        assertTrue(called[0]);
    }

    @Test
    @DisplayName("test_sync_after_hook")
    void testSyncAfterHook() {
        List<Object> receivedResults = new ArrayList<>();
        framework.on("event", kwargs -> "result", "callback");
        framework.addHook("event", HookType.AFTER, kwargs -> receivedResults.addAll((List<?>) kwargs.get("_results")));

        framework.trigger("event");

        assertEquals(List.of("result"), receivedResults);
    }

    @Test
    @DisplayName("test_hook_exception_does_not_stop_execution")
    void testHookExceptionDoesNotStopExecution() {
        boolean[] callbackExecuted = {false};
        framework.addHook("event", HookType.BEFORE, kwargs -> {
            throw new RuntimeException("Hook failed!");
        });
        framework.on("event", kwargs -> {
            callbackExecuted[0] = true;
            return null;
        }, "callback");

        framework.trigger("event");

        assertTrue(callbackExecuted[0]);
    }

    @Test
    @DisplayName("test_after_hook_exception_logged")
    void testAfterHookExceptionLogged() {
        framework.on("event", kwargs -> "result", "callback");
        framework.addHook("event", HookType.AFTER, kwargs -> {
            throw new RuntimeException("Hook error!");
        });

        List<Object> results = framework.trigger("event");

        assertEquals(List.of("result"), results);
    }
}
