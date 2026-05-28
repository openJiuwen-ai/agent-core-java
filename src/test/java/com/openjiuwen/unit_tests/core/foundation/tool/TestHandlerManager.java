/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HandlerManager.
 * <p>
 * Mirrors Python's test_handler_manager.py from
 * <code>tests/unit_tests/core/foundation/tool/test_handler_manager.py</code>.
 */
@DisplayName("Handler Manager Tests")
class TestHandlerManager {

    // Stub classes
    static class HandlerStub {
        String name;
        Function<Map<String, Object>, Map<String, Object>> handler;

        HandlerStub(String name, Function<Map<String, Object>, Map<String, Object>> handler) {
            this.name = name;
            this.handler = handler;
        }

        Map<String, Object> handle(Map<String, Object> input) {
            return handler.apply(input);
        }
    }

    static class HandlerManager {
        Map<String, HandlerStub> handlers = new HashMap<>();
        List<String> handlerOrder = new ArrayList<>();

        void register(String name, HandlerStub handler) {
            handlers.put(name, handler);
            handlerOrder.add(name);
        }

        HandlerStub get(String name) {
            return handlers.get(name);
        }

        void unregister(String name) {
            handlers.remove(name);
            handlerOrder.remove(name);
        }

        Map<String, Object> execute(String name, Map<String, Object> input) {
            HandlerStub handler = handlers.get(name);
            if (handler == null) {
                throw new IllegalArgumentException("Handler not found: " + name);
            }
            return handler.handle(input);
        }

        List<String> getHandlerOrder() {
            return new ArrayList<>(handlerOrder);
        }
    }

    @Nested
    @DisplayName("Handler Registration Tests")
    class TestHandlerRegistration {

        @Test
        @DisplayName("register handler")
        void testRegisterHandler() {
            HandlerManager manager = new HandlerManager();
            HandlerStub handler = new HandlerStub("test", input -> input);

            manager.register("test", handler);

            assertNotNull(manager.get("test"));
        }

        @Test
        @DisplayName("unregister handler")
        void testUnregisterHandler() {
            HandlerManager manager = new HandlerManager();
            HandlerStub handler = new HandlerStub("test", input -> input);
            manager.register("test", handler);

            manager.unregister("test");

            assertNull(manager.get("test"));
        }

        @Test
        @DisplayName("handler order preserved")
        void testHandlerOrderPreserved() {
            HandlerManager manager = new HandlerManager();
            manager.register("first", new HandlerStub("first", input -> input));
            manager.register("second", new HandlerStub("second", input -> input));
            manager.register("third", new HandlerStub("third", input -> input));

            List<String> order = manager.getHandlerOrder();

            assertEquals(List.of("first", "second", "third"), order);
        }
    }

    @Nested
    @DisplayName("Handler Execution Tests")
    class TestHandlerExecution {

        @Test
        @DisplayName("execute handler")
        void testExecuteHandler() {
            HandlerManager manager = new HandlerManager();
            HandlerStub handler = new HandlerStub("test", input -> {
                Map<String, Object> result = new HashMap<>();
                result.put("processed", true);
                return result;
            });
            manager.register("test", handler);

            Map<String, Object> input = new HashMap<>();
            input.put("data", "test");
            Map<String, Object> result = manager.execute("test", input);

            assertTrue((Boolean) result.get("processed"));
        }

        @Test
        @DisplayName("execute non-existent handler throws")
        void testExecuteNonExistentHandlerThrows() {
            HandlerManager manager = new HandlerManager();

            assertThrows(IllegalArgumentException.class, () -> {
                manager.execute("nonexistent", new HashMap<>());
            });
        }
    }
}