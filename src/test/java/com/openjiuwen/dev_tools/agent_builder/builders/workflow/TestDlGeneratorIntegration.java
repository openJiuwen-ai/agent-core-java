/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for DL Generator module.
 * <p>
 * Mirrors Python's {@code test_dl_generator_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow.test_dl_generator_integration}.
 */
class TestDlGeneratorIntegration {

    @Nested
    class TestDLGeneratorIntegrationInner {

        private MockLlm mockLlm(String content) {
            return new MockLlm(content);
        }

        private DlGenerator dlGenerator(MockLlm mockLlm) {
            return new DlGenerator(mockLlm);
        }

        @Test
        void testDlGeneratorInitialization() {
            MockLlm mockLlm = mockLlm("[{\"id\":\"node_1\",\"type\":\"Start\"}]");
            DlGenerator dlGenerator = dlGenerator(mockLlm);
            assertThat(dlGenerator.getLlm()).isSameAs(mockLlm);
        }

        @Test
        void testGenerateSystemTemplateContent() {
            String messages = DlGenerator.formatGenerateSystemTemplate(
                    "test components", "test schema", "test plugins", "test examples");
            assertThat(messages).contains("test components", "test schema");
        }

        @Test
        void testRefineUserTemplateContent() {
            String messages = DlGenerator.formatRefineUserTemplate(
                    "test input", "test mermaid", "test dl");
            assertThat(messages).contains("test input", "test mermaid", "test dl");
        }
    }

    @Nested
    class TestDLGeneratorGenerate {

        @Test
        void testGenerateBasic() {
            MockLlm mockLlm = new MockLlm("[{\"id\": \"node_1\", \"type\": \"Start\"}]");
            DlGenerator dlGenerator = new DlGenerator(mockLlm);

            String result = dlGenerator.generate("create workflow", Map.of());

            assertThat(result).isNotEmpty();
            assertThat(mockLlm.called).isTrue();
        }

        @Test
        void testGenerateWithPlugins() {
            MockLlm mockLlm = new MockLlm("[{\"id\": \"node_1\", \"type\": \"Start\"}]");
            DlGenerator dlGenerator = new DlGenerator(mockLlm);

            String result = dlGenerator.generate("create workflow", Map.of("plugins", List.of(Map.of("tool_id", "tool_1"))));

            assertThat(result).isNotEmpty();
            assertThat(mockLlm.called).isTrue();
        }
    }

    @Nested
    class TestDLGeneratorRefine {

        @Test
        void testRefineBasic() {
            MockLlm mockLlm = new MockLlm("[{\"id\": \"node_1\", \"type\": \"Start\"}]");
            DlGenerator dlGenerator = new DlGenerator(mockLlm);

            String result = dlGenerator.refine(
                    "modify workflow",
                    Map.of(),
                    "[{\"id\": \"node_1\", \"type\": \"Start\"}]",
                    "graph TD\n  A --> B"
            );

            assertThat(result).isNotEmpty();
            assertThat(mockLlm.called).isTrue();
        }
    }

    @Nested
    class TestDLGeneratorLoadSchemaAndExamples {

        @Test
        void testLoadSchemaAndExamples() {
            String[] payload = DlGenerator.loadSchemaAndExamples();

            assertThat(payload).hasSize(3);
            assertThat(payload[0]).isNotEmpty();
            assertThat(payload[1]).isNotEmpty();
            assertThat(payload[2]).isNotNull();
        }
    }

    static final class MockLlm {
        private final String content;
        private boolean called;

        MockLlm(String content) {
            this.content = content;
        }

        public MockResponse invoke(Object ignored) {
            called = true;
            return new MockResponse(content);
        }
    }

    record MockResponse(String content) {
        public String getContent() {
            return content;
        }
    }
}
