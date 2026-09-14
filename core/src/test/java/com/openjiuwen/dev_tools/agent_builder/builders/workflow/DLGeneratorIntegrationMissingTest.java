/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's DL generator integration tests in
 * {@code tests/system_tests/dev_tools/agent_builder/builders/workflow/test_dl_generator_integration.py}.
 */
class DLGeneratorIntegrationMissingTest {

    @Nested
    class TestDLGeneratorIntegration {

        @Test
        void dlGeneratorInitializationStoresLlm() throws ReflectiveOperationException {
            Model mockLlm = modelReturning("[{\"id\":\"node_1\",\"type\":\"Start\"}]");

            DLGenerator generator = new DLGenerator(mockLlm);

            assertThat(fieldValue(generator, "llm")).isSameAs(mockLlm);
        }

        @Test
        void generateSystemTemplateContent() {
            List<BaseMessage> messages = DLGenerator.DL_GENERATE_SYSTEM_TEMPLATE.format(Map.of(
                    "components", "test components",
                    "schema", "test schema",
                    "plugins", "test plugins",
                    "examples", "test examples"
            )).toMessages();

            assertThat(messages).isNotEmpty();
            assertThat(messages.get(0).getContentAsString()).contains("test components");
            assertThat(messages.get(0).getContentAsString()).contains("test schema");
        }

        @Test
        void refineUserTemplateContent() {
            List<BaseMessage> messages = DLGenerator.DL_REFINE_USER_TEMPLATE.format(Map.of(
                    "user_input", "test input",
                    "exist_mermaid", "test mermaid",
                    "exist_dl", "test dl"
            )).toMessages();

            assertThat(messages).isNotEmpty();
            assertThat(messages.get(0).getContentAsString()).contains("test input");
            assertThat(messages.get(0).getContentAsString()).contains("test mermaid");
            assertThat(messages.get(0).getContentAsString()).contains("test dl");
        }
    }

    @Nested
    class TestDLGeneratorGenerate {

        @Test
        void generateBasicCallsLlmAndReturnsContent() {
            AtomicReference<List<BaseMessage>> captured = new AtomicReference<>();
            DLGenerator generator = new DLGenerator(modelCapturing(captured, "[{\"id\":\"node_1\",\"type\":\"Start\"}]"));

            String result = generator.generate("create workflow", Map.of());

            assertThat(result).isNotNull();
            assertThat(captured.get()).isNotNull();
        }

        @Test
        void generateWithPluginsReturnsContent() {
            DLGenerator generator = new DLGenerator(modelReturning("[{\"id\":\"node_1\",\"type\":\"Start\"}]"));

            String result = generator.generate(
                    "create workflow",
                    Map.of("plugins", List.of(Map.of("tool_id", "tool_1"))));

            assertThat(result).isNotNull();
        }
    }

    @Nested
    class TestDLGeneratorRefine {

        @Test
        void refineBasicCallsLlmAndReturnsContent() {
            AtomicReference<List<BaseMessage>> captured = new AtomicReference<>();
            DLGenerator generator = new DLGenerator(modelCapturing(captured, "[{\"id\":\"node_1\",\"type\":\"Start\"}]"));

            String result = generator.refine(
                    "modify workflow",
                    Map.of(),
                    "[{\"id\":\"node_1\",\"type\":\"Start\"}]",
                    "graph TD\n  A --> B");

            assertThat(result).isNotNull();
            assertThat(captured.get()).isNotNull();
        }
    }

    @Nested
    class TestDLGeneratorLoadSchemaAndExamples {

        @Test
        void loadSchemaAndExamplesReturnsNonEmptyStrings() {
            DLGenerator.SchemaExamples result = DLGenerator.loadSchemaAndExamples();

            assertThat(result.componentsInfo()).isNotEmpty();
            assertThat(result.schemaInfo()).isNotEmpty();
            assertThat(result.examples()).isNotEmpty();
        }
    }

    private static Model modelReturning(String content) {
        return modelCapturing(new AtomicReference<>(), content);
    }

    private static Model modelCapturing(AtomicReference<List<BaseMessage>> captured, String content) {
        return new Model((messages, modelConfig, modelClientConfig, options) -> {
            captured.set(List.copyOf(messages));
            return CompletableFuture.completedFuture(new AssistantMessage(content));
        });
    }

    private static Object fieldValue(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
