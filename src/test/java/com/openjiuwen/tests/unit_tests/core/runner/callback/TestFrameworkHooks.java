/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.runner.callback;

import com.openjiuwen.core.runner.callback.CallbackFramework;
import com.openjiuwen.core.runner.callback.HookType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

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
        framework = new CallbackFramework();
    }

    @Nested
    @DisplayName("Before Hook")
    class BeforeHookTests {

        @Test
        @DisplayName("test_before_hook_executes_before_callback - BEFORE hook executes first")
        void testBeforeHookExecutesBeforeCallback() {
            List<String> executionOrder = new ArrayList<>();

            Consumer<Map<String, Object>> beforeHook = (ctx) -> executionOrder.add("before_hook");

            Function<Map<String, Object>, Object> callback = (ctx) -> {
                executionOrder.add("callback");
                return "result";
            };

            framework.on("event", callback);
            framework.addHook("event", HookType.BEFORE, beforeHook);

            Map<String, Object> context = new HashMap<>();
            framework.trigger("event", context);

            assertThat(executionOrder).containsExactly("before_hook", "callback");
        }

        @Test
        @DisplayName("test_before_hook_receives_args - BEFORE hook receives trigger arguments")
        void testBeforeHookReceivesArgs() {
            Map<String, Object> received = new HashMap<>();

            Consumer<Map<String, Object>> beforeHook = (ctx) -> received.putAll(ctx);

            Function<Map<String, Object>, Object> callback = (ctx) -> "result";

            framework.on("event", callback);
            framework.addHook("event", HookType.BEFORE, beforeHook);

            Map<String, Object> context = new HashMap<>();
            context.put("key", "value");
            framework.trigger("event", context);

            assertThat(received.get("key")).isEqualTo("value");
        }
    }

    @Nested
    @DisplayName("After Hook")
    class AfterHookTests {

        @Test
        @DisplayName("test_after_hook_executes_after_callback - AFTER hook executes last")
        void testAfterHookExecutesAfterCallback() {
            List<String> executionOrder = new ArrayList<>();

            Consumer<Map<String, Object>> afterHook = (ctx) -> executionOrder.add("after_hook");

            Function<Map<String, Object>, Object> callback = (ctx) -> {
                executionOrder.add("callback");
                return "result";
            };

            framework.on("event", callback);
            framework.addHook("event", HookType.AFTER, afterHook);

            Map<String, Object> context = new HashMap<>();
            framework.trigger("event", context);

            assertThat(executionOrder).containsExactly("callback", "after_hook");
        }
    }

    @Nested
    @DisplayName("Multiple Hooks")
    class MultipleHooksTests {

        @Test
        @DisplayName("test_multiple_before_hooks - multiple BEFORE hooks execute in order")
        void testMultipleBeforeHooks() {
            List<String> order = new ArrayList<>();

            Consumer<Map<String, Object>> hook1 = (ctx) -> order.add("hook1");
            Consumer<Map<String, Object>> hook2 = (ctx) -> order.add("hook2");

            Function<Map<String, Object>, Object> callback = (ctx) -> {
                order.add("callback");
                return "result";
            };

            framework.on("event", callback);
            framework.addHook("event", HookType.BEFORE, hook1);
            framework.addHook("event", HookType.BEFORE, hook2);

            Map<String, Object> context = new HashMap<>();
            framework.trigger("event", context);

            assertThat(order).containsExactly("hook1", "hook2", "callback");
        }
    }
}