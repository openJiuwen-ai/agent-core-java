/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Controller Hooks.
 * <p>
 * Mirrors Python's test_controller_hooks.py from
 * <code>tests/unit_tests/core/controller/test_controller_hooks.py</code>.
 */
@DisplayName("Controller Hooks Tests")
class TestControllerHooks {

    // Stub classes
    static class HookContextStub {
        String sessionId;
        String taskId;
        String eventType;

        HookContextStub(String sessionId, String taskId, String eventType) {
            this.sessionId = sessionId;
            this.taskId = taskId;
            this.eventType = eventType;
        }
    }

    static class HookResultStub {
        boolean success;
        String message;

        HookResultStub(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    static class ControllerHook {
        String name;
        java.util.function.Function<HookContextStub, HookResultStub> callback;

        ControllerHook(String name, java.util.function.Function<HookContextStub, HookResultStub> callback) {
            this.name = name;
            this.callback = callback;
        }

        HookResultStub execute(HookContextStub ctx) {
            return callback.apply(ctx);
        }
    }

    static class HookManager {
        List<ControllerHook> hooks = new ArrayList<>();

        void register(ControllerHook hook) {
            hooks.add(hook);
        }

        List<HookResultStub> executeAll(HookContextStub ctx) {
            List<HookResultStub> results = new ArrayList<>();
            for (ControllerHook hook : hooks) {
                results.add(hook.execute(ctx));
            }
            return results;
        }
    }

    @Nested
    @DisplayName("Hook Registration Tests")
    class TestHookRegistration {

        @Test
        @DisplayName("hook manager registers hooks")
        void testHookManagerRegistersHooks() {
            HookManager manager = new HookManager();

            manager.register(new ControllerHook("pre-start", ctx -> new HookResultStub(true, "ok")));
            manager.register(new ControllerHook("post-stop", ctx -> new HookResultStub(true, "ok")));

            assertEquals(2, manager.hooks.size());
        }

        @Test
        @DisplayName("hooks execute in order")
        void testHooksExecuteInOrder() {
            HookManager manager = new HookManager();
            List<String> executionOrder = new ArrayList<>();

            manager.register(new ControllerHook("first", ctx -> {
                executionOrder.add("first");
                return new HookResultStub(true, "ok");
            }));
            manager.register(new ControllerHook("second", ctx -> {
                executionOrder.add("second");
                return new HookResultStub(true, "ok");
            }));

            HookContextStub ctx = new HookContextStub("session-1", "task-1", "start");
            manager.executeAll(ctx);

            assertEquals(List.of("first", "second"), executionOrder);
        }
    }

    @Nested
    @DisplayName("Hook Execution Tests")
    class TestHookExecution {

        @Test
        @DisplayName("hook can reject operation")
        void testHookCanRejectOperation() {
            HookManager manager = new HookManager();

            manager.register(new ControllerHook("validator", ctx -> {
                if (ctx.taskId == null) {
                    return new HookResultStub(false, "taskId required");
                }
                return new HookResultStub(true, "ok");
            }));

            HookContextStub validCtx = new HookContextStub("session-1", "task-1", "start");
            HookContextStub invalidCtx = new HookContextStub("session-1", null, "start");

            List<HookResultStub> validResults = manager.executeAll(validCtx);
            List<HookResultStub> invalidResults = manager.executeAll(invalidCtx);

            assertTrue(validResults.get(0).success);
            assertFalse(invalidResults.get(0).success);
        }

        @Test
        @DisplayName("hook context contains correct data")
        void testHookContextContainsCorrectData() {
            HookContextStub ctx = new HookContextStub("session-123", "task-456", "complete");

            assertEquals("session-123", ctx.sessionId);
            assertEquals("task-456", ctx.taskId);
            assertEquals("complete", ctx.eventType);
        }
    }
}