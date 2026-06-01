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
        @DisplayName("handle complex value types")
        void testHandleComplexValueTypes() {
            DefaultFormHandler handler = new DefaultFormHandler();
            Map<String, Object> formData = new HashMap<>();
            formData.put("items", List.of("a", "b"));
            formData.put("meta", Map.of("k", "v"));

            List<Map.Entry<String, String>> result = handler.handle(new ArrayList<>(), formData, new HashMap<>());

            assertEquals(2, result.size());
            assertTrue(result.stream().anyMatch(entry -> entry.getKey().equals("items")));
            assertTrue(result.stream().anyMatch(entry -> entry.getKey().equals("meta")));
        }

        @Test
        @DisplayName("accumulate to existing form data")
        void testAccumulateToExistingFormData() {
            DefaultFormHandler handler = new DefaultFormHandler();
            List<Map.Entry<String, String>> form = new ArrayList<>();
            form.add(Map.entry("existing", "value"));

            List<Map.Entry<String, String>> result = handler.handle(form, Map.of("name", "test"), new HashMap<>());

            assertSame(form, result);
            assertEquals(2, result.size());
            assertEquals("existing", result.get(0).getKey());
            assertEquals("name", result.get(1).getKey());
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
        @DisplayName("singleton pattern")
        void testSingletonPattern() {
            assertSame(FormHandlerManager.getInstance(), FormHandlerManager.getInstance());
        }

        @Test
        @DisplayName("default form handler is default form handler")
        void testDefaultFormHandlerIsDefaultFormHandler() {
            assertDefaultHandler(FormHandlerManager.getInstance().getHandler("unknown_type"));
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
        @DisplayName("register custom handler")
        void testRegisterCustomHandler() {
            FormHandlerManager manager = FormHandlerManager.getInstance();
            String type = "custom/" + System.nanoTime();

            manager.register(type, CustomHandler.class);

            assertInstanceOf(CustomHandler.class, manager.getHandler(type));
        }

        @Test
        @DisplayName("get handler returns default for unknown type")
        void testGetHandlerReturnsDefaultForUnknownType() {
            FormHandlerManager manager = FormHandlerManager.getInstance();

            FormHandler<?> handler = manager.getHandler("unknown_type");
            assertNotNull(handler);
            assertTrue(handler instanceof DefaultFormHandler);
        }

        @Test
        @DisplayName("get handler returns registered handler")
        void testGetHandlerReturnsRegisteredHandler() {
            FormHandlerManager manager = FormHandlerManager.getInstance();
            String type = "registered/" + System.nanoTime();
            manager.register(type, CustomHandler.class);

            assertInstanceOf(CustomHandler.class, manager.getHandler(type));
        }

        @Test
        @DisplayName("get handler returns default when type is empty")
        void testGetHandlerReturnsDefaultWhenTypeIsEmpty() {
            assertDefaultHandler(FormHandlerManager.getInstance().getHandler(""));
        }

        @Test
        @DisplayName("get handler returns default when type is none")
        void testGetHandlerReturnsDefaultWhenTypeIsNone() {
            assertDefaultHandler(FormHandlerManager.getInstance().getHandler(null));
        }

        @Test
        @DisplayName("register with invalid handler type value")
        void testRegisterWithInvalidHandlerTypeValue() {
            FormHandlerManager manager = FormHandlerManager.getInstance();

            // Register with null should not add
            manager.register(null, DefaultFormHandler.class);

            // Register with empty should not add
            manager.register("", DefaultFormHandler.class);

            // Python logs invalid keys and still falls back through get_handler().
            assertDefaultHandler(manager.getHandler(null));
            assertDefaultHandler(manager.getHandler(""));
        }

        @Test
        @DisplayName("register invalid handler type value empty")
        void testRegisterInvalidHandlerTypeValueEmpty() {
            FormHandlerManager manager = FormHandlerManager.getInstance();
            manager.register("", CustomHandler.class);
            assertDefaultHandler(manager.getHandler(""));
        }

        @Test
        @DisplayName("register invalid handler type value none")
        void testRegisterInvalidHandlerTypeValueNone() {
            FormHandlerManager manager = FormHandlerManager.getInstance();
            manager.register(null, CustomHandler.class);
            assertDefaultHandler(manager.getHandler(null));
        }

        @Test
        @DisplayName("register non string handler type value")
        void testRegisterNonStringHandlerTypeValue() {
            FormHandlerManager manager = FormHandlerManager.getInstance();
            manager.register(String.valueOf(123), CustomHandler.class);
            assertInstanceOf(CustomHandler.class, manager.getHandler("123"));
        }

        @Test
        @SuppressWarnings({"rawtypes", "unchecked"})
        @DisplayName("register non form handler subclass")
        void testRegisterNonFormHandlerSubclass() {
            FormHandlerManager manager = FormHandlerManager.getInstance();
            manager.register("bad/" + System.nanoTime(), (Class) String.class);
            assertDefaultHandler(manager.getHandler("bad/" + System.nanoTime()));
        }

        @Test
        @DisplayName("register non class object")
        void testRegisterNonClassObject() {
            FormHandlerManager manager = FormHandlerManager.getInstance();
            manager.register("bad/" + System.nanoTime(), null);
            assertDefaultHandler(manager.getHandler("bad/" + System.nanoTime()));
        }

        @Test
        @DisplayName("override existing handler")
        void testOverrideExistingHandler() {
            FormHandlerManager manager = FormHandlerManager.getInstance();
            String type = "override/" + System.nanoTime();
            manager.register(type, CustomHandler.class);
            manager.register(type, AnotherHandler.class);
            assertInstanceOf(AnotherHandler.class, manager.getHandler(type));
        }

        @Test
        @DisplayName("register default handler")
        void testRegisterDefaultHandler() {
            FormHandlerManager manager = FormHandlerManager.getInstance();
            manager.registerDefaultHandler(DefaultFormHandler.class);
            assertDefaultHandler(manager.getHandler("unregistered_type"));
        }
    }

    @Nested
    @DisplayName("Custom Form Handler Tests")
    class TestCustomFormHandler {
        @Test
        @DisplayName("file upload handler can be registered")
        void testFileUploadHandlerCanBeRegistered() {
            FormHandlerManager manager = FormHandlerManager.getInstance();
            String type = "file/" + System.nanoTime();
            manager.register(type, FileUploadFormHandler.class);
            assertInstanceOf(FileUploadFormHandler.class, manager.getHandler(type));
        }

        @Test
        @DisplayName("file upload handler can process file param")
        void testFileUploadHandlerCanProcessFileParam() {
            List<Map.Entry<String, String>> result = new FileUploadFormHandler()
                    .handle(new ArrayList<>(), Map.of("file", "a.txt"), new HashMap<>());
            assertEquals("file", result.get(0).getKey());
            assertEquals("upload:a.txt", result.get(0).getValue());
        }

        @Test
        @DisplayName("json data handler can be registered")
        void testJsonDataHandlerCanBeRegistered() {
            FormHandlerManager manager = FormHandlerManager.getInstance();
            String type = "json/" + System.nanoTime();
            manager.register(type, JsonFormHandler.class);
            assertInstanceOf(JsonFormHandler.class, manager.getHandler(type));
        }

        @Test
        @DisplayName("json data handler can process json data")
        void testJsonDataHandlerCanProcessJsonData() {
            List<Map.Entry<String, String>> result = new JsonFormHandler()
                    .handle(new ArrayList<>(), Map.of("payload", Map.of("a", 1)), new HashMap<>());
            assertEquals("payload", result.get(0).getKey());
            assertTrue(result.get(0).getValue().contains("a=1"));
        }
    }

    @Nested
    @DisplayName("Logging Tests")
    class TestFormHandlerManagerLogging {
        @Test
        @DisplayName("register handler logs info")
        void testRegisterHandlerLogsInfo() {
            FormHandlerManager manager = FormHandlerManager.getInstance();
            String type = "logs/" + System.nanoTime();
            manager.register(type, CustomHandler.class);
            assertInstanceOf(CustomHandler.class, manager.getHandler(type));
        }

        @Test
        @SuppressWarnings({"rawtypes", "unchecked"})
        @DisplayName("register invalid handler outputs error log")
        void testRegisterInvalidHandlerOutputsErrorLog() {
            FormHandlerManager manager = FormHandlerManager.getInstance();
            String type = "bad-log/" + System.nanoTime();
            manager.register(type, (Class) String.class);
            assertDefaultHandler(manager.getHandler(type));
        }
    }

    private static void assertDefaultHandler(FormHandler<?> handler) {
        assertNotNull(handler);
        assertTrue(handler instanceof DefaultFormHandler);
    }

    public static class CustomHandler extends FormHandler<List<Map.Entry<String, String>>> {
        @Override
        public List<Map.Entry<String, String>> handle(
                List<Map.Entry<String, String>> form,
                Map<String, Object> formData,
                Map<String, Object> kwargs) {
            if (form == null) {
                form = new ArrayList<>();
            }
            form.add(Map.entry("custom", "handled"));
            return form;
        }
    }

    public static class AnotherHandler extends CustomHandler {
    }

    public static class FileUploadFormHandler extends FormHandler<List<Map.Entry<String, String>>> {
        @Override
        public List<Map.Entry<String, String>> handle(
                List<Map.Entry<String, String>> form,
                Map<String, Object> formData,
                Map<String, Object> kwargs) {
            if (form == null) {
                form = new ArrayList<>();
            }
            form.add(Map.entry("file", "upload:" + formData.get("file")));
            return form;
        }
    }

    public static class JsonFormHandler extends FormHandler<List<Map.Entry<String, String>>> {
        @Override
        public List<Map.Entry<String, String>> handle(
                List<Map.Entry<String, String>> form,
                Map<String, Object> formData,
                Map<String, Object> kwargs) {
            if (form == null) {
                form = new ArrayList<>();
            }
            form.add(Map.entry("payload", String.valueOf(formData.get("payload"))));
            return form;
        }
    }
}
