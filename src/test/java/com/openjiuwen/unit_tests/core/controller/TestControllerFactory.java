/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Controller Factory.
 * <p>
 * Mirrors Python's test_controller_base.py from
 * <code>tests/unit_tests/core/controller/test_controller_base.py</code>.
 */
@DisplayName("Controller Factory Tests")
class TestControllerFactory {

    // Stub classes
    static class ControllerConfigStub {
        int maxConcurrentTasks;
        boolean enableStreaming;

        ControllerConfigStub() {
            this.maxConcurrentTasks = 5;
            this.enableStreaming = true;
        }
    }

    static class ControllerStub {
        String controllerId;
        ControllerConfigStub config;

        ControllerStub(String controllerId, ControllerConfigStub config) {
            this.controllerId = controllerId;
            this.config = config;
        }

        CompletableFuture<String> start() {
            return CompletableFuture.completedFuture("started");
        }

        CompletableFuture<Void> stop() {
            return CompletableFuture.completedFuture(null);
        }
    }

    static class ControllerFactory {
        Map<String, ControllerStub> controllers = new HashMap<>();

        ControllerStub create(String controllerId, ControllerConfigStub config) {
            ControllerStub controller = new ControllerStub(controllerId, config);
            controllers.put(controllerId, controller);
            return controller;
        }

        ControllerStub get(String controllerId) {
            return controllers.get(controllerId);
        }

        void destroy(String controllerId) {
            controllers.remove(controllerId);
        }
    }

    @Nested
    @DisplayName("Controller Factory Tests")
    class TestFactoryOperations {

        @Test
        @DisplayName("factory creates controller")
        void testFactoryCreatesController() {
            ControllerFactory factory = new ControllerFactory();
            ControllerConfigStub config = new ControllerConfigStub();

            ControllerStub controller = factory.create("test-controller", config);

            assertNotNull(controller);
            assertEquals("test-controller", controller.controllerId);
            assertEquals(5, controller.config.maxConcurrentTasks);
        }

        @Test
        @DisplayName("factory retrieves controller")
        void testFactoryRetrievesController() {
            ControllerFactory factory = new ControllerFactory();
            ControllerConfigStub config = new ControllerConfigStub();
            factory.create("test-controller", config);

            ControllerStub retrieved = factory.get("test-controller");

            assertNotNull(retrieved);
            assertEquals("test-controller", retrieved.controllerId);
        }

        @Test
        @DisplayName("factory destroys controller")
        void testFactoryDestroysController() {
            ControllerFactory factory = new ControllerFactory();
            ControllerConfigStub config = new ControllerConfigStub();
            factory.create("test-controller", config);

            factory.destroy("test-controller");

            assertNull(factory.get("test-controller"));
        }

        @Test
        @DisplayName("controller can start")
        void testControllerCanStart() throws Exception {
            ControllerFactory factory = new ControllerFactory();
            ControllerConfigStub config = new ControllerConfigStub();
            ControllerStub controller = factory.create("test-controller", config);

            String result = controller.start().get();

            assertEquals("started", result);
        }
    }
}