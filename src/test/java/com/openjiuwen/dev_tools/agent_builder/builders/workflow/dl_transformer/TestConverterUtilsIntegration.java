/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * System tests for DL Transformer ConverterUtils module.
 * <p>
 * Mirrors Python's {@code test_converter_utils_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow.dl_transformer}.
 */
class TestConverterUtilsIntegration {

    @Nested
    class TestGenerateNodeId {

        @Test
        void generateNodeIdUniqueness() {
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                ids.add(ConverterUtils.generateNodeId("node"));
            }
            assertThat(ids).hasSize(100);
        }

        @Test
        void generateNodeIdWithPrefix() {
            String nodeId = ConverterUtils.generateNodeId("start");
            assertThat(nodeId).startsWith("start_");
        }
    }

    @Nested
    class TestExtractVariable {

        @Test
        void extractVariableValid() {
            String[] result = ConverterUtils.extractVariable("${node_start.query}");
            assertThat(result).containsExactly("node_start", "query");
        }

        @Test
        void extractVariableInvalid() {
            String[] result = ConverterUtils.extractVariable("invalid");
            assertThat(result).isNull();
        }
    }

    @Nested
    class TestConvertRefVariable {

        @Test
        void convertRefVariableValid() {
            Map<String, Object> result = ConverterUtils.convertRefVariable("${node_start.query}");
            assertThat(result.get("type")).isEqualTo("ref");
            assertThat(result.get("content")).isEqualTo(List.of("node_start", "query"));
        }

        @Test
        void convertRefVariableNested() {
            Map<String, Object> result = ConverterUtils.convertRefVariable("${node_llm.output_of_result}");
            assertThat(result.get("type")).isEqualTo("ref");
            assertThat(result.get("content")).isEqualTo(List.of("node_llm", "result", "output"));
        }

        @Test
        void convertRefVariableInvalid() {
            assertThatThrownBy(() -> ConverterUtils.convertRefVariable("invalid"))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    class TestConvertLlmParam {

        @Test
        void convertLlmParamBasic() {
            Map<String, Object> result = ConverterUtils.convertLlmParam("You are helpful", "{{query}}");

            @SuppressWarnings("unchecked")
            Map<String, Object> systemPrompt = (Map<String, Object>) result.get("systemPrompt");
            @SuppressWarnings("unchecked")
            Map<String, Object> prompt = (Map<String, Object>) result.get("prompt");

            assertThat(systemPrompt.get("type")).isEqualTo("template");
            assertThat(systemPrompt.get("content")).isEqualTo("You are helpful");
            assertThat(prompt.get("type")).isEqualTo("template");
            assertThat(prompt.get("content")).isEqualTo("{{query}}");
            assertThat(result).containsKey("mode");
        }

        @Test
        void convertLlmParamContainsModelConfig() {
            Map<String, Object> result = ConverterUtils.convertLlmParam("system", "user");

            @SuppressWarnings("unchecked")
            Map<String, String> mode = (Map<String, String>) result.get("mode");
            assertThat(mode).containsKeys("id", "name");
        }
    }

    @Nested
    class TestConvertToDict {

        record Position(double x, double y) {
        }

        @Test
        void convertToDictWithDict() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("key", "value");
            data.put("none_key", null);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) ConverterUtils.convertToDict(data);

            assertThat(result.get("key")).isEqualTo("value");
            assertThat(result).doesNotContainKey("none_key");
        }

        @Test
        void convertToDictWithList() {
            List<Object> data = new ArrayList<>();
            data.add(Map.of("key", "value"));
            data.add(null);
            data.add(Map.of("key2", "value2"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> result = (List<Map<String, Object>>) ConverterUtils.convertToDict(data);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).get("key")).isEqualTo("value");
            assertThat(result.get(1).get("key2")).isEqualTo("value2");
        }

        @Test
        void convertToDictWithNone() {
            Object result = ConverterUtils.convertToDict(null);

            assertThat(result).isEqualTo(Map.of());
        }

        @Test
        void convertToDictWithDataclassLikeRecord() {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) ConverterUtils.convertToDict(new Position(100.0, 200.0));

            assertThat(result.get("x")).isEqualTo(100.0);
            assertThat(result.get("y")).isEqualTo(200.0);
        }
    }

    @Nested
    class TestLlmModelConfig {

        @Test
        void llmModelConfigExists() {
            assertThat(ConverterUtils.LLM_MODEL_CONFIG).isNotNull();
        }

        @Test
        void llmModelConfigHasRequiredFields() {
            assertThat(ConverterUtils.LLM_MODEL_CONFIG).containsKeys("id", "name", "type");
        }
    }
}
