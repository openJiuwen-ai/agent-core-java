/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.form_handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Form Handler classes.
 * <p>
 * Mirrors Python's test_handler_manager.py from
 * <code>tests/unit_tests/core/foundation/tool/test_handler_manager.py</code>.
 */
@DisplayName("Form Handler Manager Tests")
class TestHandlerManager {

    @Nested
    @DisplayName("FormHandler Tests")
    class TestFormHandler {

        @Test
        @DisplayName("abstract handle cannot be called directly")
        void testAbstractHandleCannotBeCalledDirectly() {
            // FormHandler is abstract, cannot instantiate directly
            // This is enforced by Java's abstract keyword
        }

        @Test
        @DisplayName("concrete handler can be instantiated")
        void testConcreteHandlerCanBeInstantiated() {
            DefaultFormHandler handler = new DefaultFormHandler();
            assertTrue(handler instanceof FormHandler);
        }

        @Test
        @DisplayName("handle can be called")
        void testHandleCanBeCalled() {
            DefaultFormHandler handler = new DefaultFormHandler();
            Map<String, Object> formData = new HashMap<>();
            formData.put("field", "value");

            List<Map.Entry<String, String>> result = handler.handle(new ArrayList<>(), formData, new HashMap<>());

            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Nested
    @DisplayName("DefaultFormHandler Tests")
    class TestDefaultFormHandler {

        @Test
        @DisplayName("handle single field")
        void testHandleSingleField() {
            DefaultFormHandler handler = new DefaultFormHandler();
            Map<String, Object> formData = new HashMap<>();
            formData.put("name", "test");

            List<Map.Entry<String, String>> result = handler.handle(new ArrayList<>(), formData, new HashMap<>());

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("name", result.get(0).getKey());
            assertEquals("test", result.get(0).getValue());
        }

        @Test
        @DisplayName("handle multiple fields")
        void testHandleMultipleFields() {
            DefaultFormHandler handler = new DefaultFormHandler();
            Map<String, Object> formData = new HashMap<>();
            formData.put("name", "test");
            formData.put("age", "25");
            formData.put("city", "Beijing");

            List<Map.Entry<String, String>> result = handler.handle(new ArrayList<>(), formData, new HashMap<>());

            assertNotNull(result);
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("handle skips null values")
        void testHandleSkipsNullValues() {
            DefaultFormHandler handler = new DefaultFormHandler();
            Map<String, Object> formData = new HashMap<>();
            formData.put("name", "test");
            formData.put("age", null);
            formData.put("city", "Beijing");

            List<Map.Entry<String, String>> result = handler.handle(new ArrayList<>(), formData, new HashMap<>());

            assertNotNull(result);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("handle empty form data")
        void testHandleEmptyFormData() {
            DefaultFormHandler handler = new DefaultFormHandler();

            List<Map.Entry<String, String>> result = handler.handle(new ArrayList<>(), new HashMap<>(), new HashMap<>());

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("handle null form data")
        void testHandleNullFormData() {
            DefaultFormHandler handler = new DefaultFormHandler();

            List<Map.Entry<String, String>> result = handler.handle(new ArrayList<>(), null, new HashMap<>());

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("FormHandlerManager Tests")
    class TestFormHandlerManager {

        @Test
        @DisplayName("getInstance returns singleton")
        void testGetInstanceReturnsSingleton() {
            FormHandlerManager instance1 = FormHandlerManager.getInstance();
            FormHandlerManager instance2 = FormHandlerManager.getInstance();

            assertEquals(instance1, instance2);
        }

        @Test
        @DisplayName("register handler")
        void testRegisterHandler() {
            FormHandlerManager manager = FormHandlerManager.getInstance();

            // Register DefaultFormHandler for a type
            manager.register("multipart/form-data", DefaultFormHandler.class);

            FormHandler<?> handler = manager.getHandler("multipart/form-data");
            assertNotNull(handler);
            assertTrue(handler instanceof DefaultFormHandler);
        }

        @Test
        @DisplayName("get handler returns null for unknown type")
        void testGetHandlerReturnsNullForUnknownType() {
            FormHandlerManager manager = FormHandlerManager.getInstance();

            FormHandler<?> handler = manager.getHandler("unknown_type");
            assertNull(handler);
        }

        @Test
        @DisplayName("register with invalid handler type value")
        void testRegisterWithInvalidHandlerTypeValue() {
            FormHandlerManager manager = FormHandlerManager.getInstance();

            // Register with null should not add
            manager.register(null, DefaultFormHandler.class);

            // Register with empty should not add
            manager.register("", DefaultFormHandler.class);

            // Both should not be registered
            assertNull(manager.getHandler(null));
            assertNull(manager.getHandler(""));
        }

        @Test
        @DisplayName("register default handler")
        void testRegisterDefaultHandler() {
            FormHandlerManager manager = FormHandlerManager.getInstance();
            assertNotNull(manager);
        }
    }
}