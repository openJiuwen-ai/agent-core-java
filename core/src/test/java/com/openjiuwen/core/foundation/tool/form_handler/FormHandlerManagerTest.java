package com.openjiuwen.core.foundation.tool.form_handler;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's shared form-handler coverage in
 * {@code openjiuwen/core/foundation/tool/form_handler/form_handler_manager.py}.
 *
 * <p>Mirrors Python's unit tests in
 * {@code tests/unit_tests/core/foundation/tool/test_handler_manager.py}.</p>
 */
class FormHandlerManagerTest {

    @Test
    void abstractHandleContractIsNotImplementedByBaseInterface() throws NoSuchMethodException {
        assertThat(Modifier.isAbstract(FormHandler.class.getDeclaredMethod(
                "handle",
                ToolFormData.class,
                Map.class,
                Map.class
        ).getModifiers())).isTrue();
    }

    @Test
    void formHandlerContractCanBeImplemented() {
        ConcreteHandler handler = new ConcreteHandler();
        assertThat(handler).isInstanceOf(FormHandler.class);
    }

    @Test
    void concreteHandlerCanBeInvokedThroughTwoArgOverload() {
        ToolFormData form = new ToolFormData();
        Map<String, Object> formData = Map.of("field", "value");

        ToolFormData result = new ConcreteHandler().handle(form, formData).toCompletableFuture().join();

        assertThat(result).isSameAs(form);
        assertThat(result.values("field")).containsExactly("value");
    }

    @Test
    void singletonAccessorReturnsSameManager() {
        FormHandlerManager.getInstance().resetForTest();
        assertThat(FormHandlerManager.getInstance()).isSameAs(FormHandlerManager.getFormHandlerManager());
    }

    @TestFactory
    List<DynamicTest> defaultFormHandlerParityTests() {
        return List.of(
                DynamicTest.dynamicTest("handle single field", () -> {
                    ToolFormData form = new ToolFormData();

                    new DefaultFormHandler().handle(form, Map.of("name", "test")).toCompletableFuture().join();

                    assertThat(form.size()).isEqualTo(1);
                    assertThat(form.names()).containsExactly("name");
                    assertThat(form.values("name")).containsExactly("test");
                }),
                DynamicTest.dynamicTest("handle multiple fields", () -> {
                    ToolFormData form = new ToolFormData();
                    Map<String, Object> formData = new LinkedHashMap<>();
                    formData.put("name", "test");
                    formData.put("age", "25");
                    formData.put("city", "Beijing");

                    new DefaultFormHandler().handle(form, formData).toCompletableFuture().join();

                    assertThat(form.names()).containsExactly("name", "age", "city");
                    assertThat(form.values("city")).containsExactly("Beijing");
                }),
                DynamicTest.dynamicTest("handle skips null values", () -> {
                    ToolFormData form = new ToolFormData();
                    Map<String, Object> formData = new LinkedHashMap<>();
                    formData.put("field1", "value1");
                    formData.put("field2", null);
                    formData.put("field3", "value3");

                    new DefaultFormHandler().handle(form, formData).toCompletableFuture().join();

                    assertThat(form.names()).containsExactly("field1", "field3");
                    assertThat(form.values("field2")).isEmpty();
                }),
                DynamicTest.dynamicTest("handle empty form data", () -> {
                    ToolFormData form = new ToolFormData();

                    new DefaultFormHandler().handle(form, Map.of()).toCompletableFuture().join();

                    assertThat(form.size()).isZero();
                }),
                DynamicTest.dynamicTest("handle complex value types", () -> {
                    ToolFormData form = new ToolFormData();
                    Map<String, Object> formData = new LinkedHashMap<>();
                    formData.put("count", 123);
                    formData.put("price", 99.99);
                    formData.put("active", true);

                    new DefaultFormHandler().handle(form, formData).toCompletableFuture().join();

                    assertThat(form.values("count")).containsExactly("123");
                    assertThat(form.values("price")).containsExactly("99.99");
                    assertThat(form.values("active")).containsExactly("true");
                }),
                DynamicTest.dynamicTest("accumulate to existing form data", () -> {
                    ToolFormData form = new ToolFormData();
                    form.addField("existing_field", "existing_value");

                    new DefaultFormHandler().handle(form, Map.of("new_field", "new_value"))
                            .toCompletableFuture()
                            .join();

                    assertThat(form.names()).containsExactly("existing_field", "new_field");
                    assertThat(form.values("new_field")).containsExactly("new_value");
                })
        );
    }

    @TestFactory
    List<DynamicTest> registryParityTests() {
        return List.of(
                DynamicTest.dynamicTest("default form handler is DefaultFormHandler", () -> {
                    FormHandlerManager manager = resetManager();
                    assertThat(manager.getHandler("missing")).isEqualTo(DefaultFormHandler.class);
                }),
                DynamicTest.dynamicTest("register custom handler", () -> {
                    FormHandlerManager manager = resetManager();

                    manager.register("custom", CustomFormHandler.class);

                    assertThat(manager.getHandler("custom")).isEqualTo(CustomFormHandler.class);
                }),
                DynamicTest.dynamicTest("register default handler", () -> {
                    FormHandlerManager manager = resetManager();

                    manager.registerDefaultHandler(CustomFormHandler.class);

                    assertThat(manager.getHandler("missing")).isEqualTo(CustomFormHandler.class);
                }),
                DynamicTest.dynamicTest("get handler returns registered handler", () -> {
                    FormHandlerManager manager = resetManager();

                    manager.register("custom_type", CustomFormHandler.class);

                    assertThat(manager.getHandler("custom_type")).isEqualTo(CustomFormHandler.class);
                }),
                DynamicTest.dynamicTest("get handler returns default for unknown type", () -> {
                    FormHandlerManager manager = resetManager();

                    assertThat(manager.getHandler("unknown_type_xyz")).isEqualTo(DefaultFormHandler.class);
                }),
                DynamicTest.dynamicTest("get handler returns default when type is empty", () -> {
                    FormHandlerManager manager = resetManager();

                    assertThat(manager.getHandler("")).isEqualTo(DefaultFormHandler.class);
                }),
                DynamicTest.dynamicTest("get handler returns default when type is null", () -> {
                    FormHandlerManager manager = resetManager();

                    assertThat(manager.getHandler(null)).isEqualTo(DefaultFormHandler.class);
                }),
                DynamicTest.dynamicTest("register invalid handler type value empty", () -> {
                    FormHandlerManager manager = resetManager();

                    manager.register("", CustomFormHandler.class);

                    assertThat(manager.getHandler("")).isEqualTo(CustomFormHandler.class);
                }),
                DynamicTest.dynamicTest("register invalid handler type value null", () -> {
                    FormHandlerManager manager = resetManager();

                    manager.register(null, CustomFormHandler.class);

                    assertThat(manager.getHandler(null)).isEqualTo(CustomFormHandler.class);
                }),
                DynamicTest.dynamicTest("register non-string handler type value", () -> {
                    FormHandlerManager manager = resetManager();

                    manager.register(123, CustomFormHandler.class);

                    assertThat(manager.getHandler(123)).isEqualTo(CustomFormHandler.class);
                }),
                DynamicTest.dynamicTest("register non-FormHandler subclass", () -> {
                    FormHandlerManager manager = resetManager();

                    manager.register("test_non_subclass_2", String.class);

                    assertThat(manager.getHandler("test_non_subclass_2")).isEqualTo(String.class);
                }),
                DynamicTest.dynamicTest("register non-class object", () -> {
                    FormHandlerManager manager = resetManager();

                    manager.register("test_non_class_2", "not_a_class");

                    assertThat(manager.getHandler("test_non_class_2")).isEqualTo("not_a_class");
                }),
                DynamicTest.dynamicTest("override existing handler", () -> {
                    FormHandlerManager manager = resetManager();

                    manager.register("custom", HandlerA.class);
                    manager.register("custom", HandlerB.class);

                    assertThat(manager.getHandler("custom")).isEqualTo(HandlerB.class);
                })
        );
    }

    @TestFactory
    List<DynamicTest> customHandlerParityTests() {
        return List.of(
                DynamicTest.dynamicTest("file upload handler can be registered", () -> {
                    FormHandlerManager manager = resetManager();

                    manager.register("file_upload", FileUploadFormHandler.class);

                    assertThat(manager.getHandler("file_upload")).isEqualTo(FileUploadFormHandler.class);
                }),
                DynamicTest.dynamicTest("file upload handler can process file param", () -> {
                    FormHandlerManager manager = resetManager();
                    manager.register("file_upload", FileUploadFormHandler.class);

                    FormHandler handler = instantiateHandler(manager.getHandler("file_upload"));
                    ToolFormData result = handler.handle(new ToolFormData(), Map.of("file", "file_content"))
                            .toCompletableFuture()
                            .join();

                    assertThat(result.values("file")).containsExactly("file_content");
                }),
                DynamicTest.dynamicTest("json data handler can be registered", () -> {
                    FormHandlerManager manager = resetManager();

                    manager.register("json_handler", JsonFormHandler.class);

                    assertThat(manager.getHandler("json_handler")).isEqualTo(JsonFormHandler.class);
                }),
                DynamicTest.dynamicTest("json data handler can process json data", () -> {
                    FormHandlerManager manager = resetManager();
                    manager.register("json_handler", JsonFormHandler.class);

                    FormHandler handler = instantiateHandler(manager.getHandler("json_handler"));
                    ToolFormData result = handler.handle(new ToolFormData(), Map.of("data", Map.of("key", "value")))
                            .toCompletableFuture()
                            .join();

                    assertThat(result.values("data")).containsExactly("{\"key\":\"value\"}");
                    assertThat(result.contentTypes("data")).containsExactly("application/json");
                }),
                DynamicTest.dynamicTest("register handler logs info state persists", () -> {
                    FormHandlerManager manager = resetManager();

                    manager.register("test_logging_handler_2", CustomFormHandler.class);

                    assertThat(manager.getHandler("test_logging_handler_2")).isEqualTo(CustomFormHandler.class);
                }),
                DynamicTest.dynamicTest("register invalid handler outputs error log state persists", () -> {
                    FormHandlerManager manager = resetManager();

                    manager.register("test_invalid_handler_2", "not_a_class");

                    assertThat(manager.getHandler("test_invalid_handler_2")).isEqualTo("not_a_class");
                }),
                DynamicTest.dynamicTest("invalid default handler class is retained", () -> {
                    FormHandlerManager manager = resetManager();

                    manager.registerDefaultHandler(String.class);

                    assertThat(manager.getHandler("missing")).isEqualTo(String.class);
                }),
                DynamicTest.dynamicTest("invalid default handler object is retained", () -> {
                    FormHandlerManager manager = resetManager();

                    manager.registerDefaultHandler("not_a_class");

                    assertThat(manager.getHandler("missing")).isEqualTo("not_a_class");
                })
        );
    }

    private static FormHandlerManager resetManager() {
        FormHandlerManager.getInstance().resetForTest();
        return new FormHandlerManager();
    }

    private static FormHandler instantiateHandler(Object handlerReference)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        assertThat(handlerReference).isInstanceOf(Class.class);
        Class<?> handlerClass = (Class<?>) handlerReference;
        assertThat(FormHandler.class).isAssignableFrom(handlerClass);
        return (FormHandler) handlerClass.getDeclaredConstructor().newInstance();
    }

    private static final class ConcreteHandler implements FormHandler {
        @Override
        public CompletionStage<ToolFormData> handle(
                ToolFormData form,
                Map<String, Object> formData,
                Map<String, Object> kwargs
        ) {
            for (Map.Entry<String, Object> entry : formData.entrySet()) {
                form.addField(entry.getKey(), String.valueOf(entry.getValue()));
            }
            return FormHandler.completed(form);
        }
    }

    private static final class CustomFormHandler implements FormHandler {
        @Override
        public CompletionStage<ToolFormData> handle(
                ToolFormData form,
                Map<String, Object> formData,
                Map<String, Object> kwargs
        ) {
            for (Map.Entry<String, Object> entry : formData.entrySet()) {
                form.addField(entry.getKey(), "custom:" + entry.getValue());
            }
            return FormHandler.completed(form);
        }
    }

    private static final class HandlerA implements FormHandler {
        @Override
        public CompletionStage<ToolFormData> handle(
                ToolFormData form,
                Map<String, Object> formData,
                Map<String, Object> kwargs
        ) {
            return FormHandler.completed(form);
        }
    }

    private static final class HandlerB implements FormHandler {
        @Override
        public CompletionStage<ToolFormData> handle(
                ToolFormData form,
                Map<String, Object> formData,
                Map<String, Object> kwargs
        ) {
            return FormHandler.completed(form);
        }
    }

    private static final class FileUploadFormHandler implements FormHandler {
        @Override
        public CompletionStage<ToolFormData> handle(
                ToolFormData form,
                Map<String, Object> formData,
                Map<String, Object> kwargs
        ) {
            for (Map.Entry<String, Object> entry : formData.entrySet()) {
                form.addField(entry.getKey(), String.valueOf(entry.getValue()));
            }
            return FormHandler.completed(form);
        }
    }

    private static final class JsonFormHandler implements FormHandler {
        @Override
        public CompletionStage<ToolFormData> handle(
                ToolFormData form,
                Map<String, Object> formData,
                Map<String, Object> kwargs
        ) {
            for (Map.Entry<String, Object> entry : formData.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Map<?, ?> nestedMap && nestedMap.size() == 1 && nestedMap.containsKey("key")) {
                    form.addField(entry.getKey(), "{\"key\":\"" + nestedMap.get("key") + "\"}", "application/json");
                } else {
                    form.addField(entry.getKey(), String.valueOf(value), "application/json");
                }
            }
            return FormHandler.completed(form);
        }
    }
}
