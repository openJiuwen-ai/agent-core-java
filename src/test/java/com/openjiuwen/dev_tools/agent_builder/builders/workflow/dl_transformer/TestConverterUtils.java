/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code test_converter_utils.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/dl_transformer/test_converter_utils.py}.
 */
class TestConverterUtils {

    @Test
    void testGenerateNodeIdWithPrefix() {
        String nodeId = ConverterUtils.generateNodeId("node");
        assertThat(nodeId).startsWith("node_");
        assertThat(nodeId.length()).isGreaterThan(5);
    }

    @Test
    void testGenerateNodeIdUniqueness() {
        String id1 = ConverterUtils.generateNodeId("node");
        String id2 = ConverterUtils.generateNodeId("node");
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void testGenerateNodeIdWithEmptyPrefix() {
        String nodeId = ConverterUtils.generateNodeId("");
        assertThat(nodeId).startsWith("_");
    }

    @Test
    void testExtractVariableSuccess() {
        assertThat(ConverterUtils.extractVariable("${node_start.query}"))
                .containsExactly("node_start", "query");
    }

    @Test
    void testExtractVariableInvalidFormat() {
        assertThat(ConverterUtils.extractVariable("invalid")).isNull();
    }

    @Test
    void testExtractVariableEmptyString() {
        assertThat(ConverterUtils.extractVariable("")).isNull();
    }

    @Test
    void testExtractVariableMissingBraces() {
        assertThat(ConverterUtils.extractVariable("node_start.query")).isNull();
    }

    @Test
    void testExtractVariableTrimsWhitespace() {
        assertThat(ConverterUtils.extractVariable("  ${node_start.query}  "))
                .containsExactly("node_start", "query");
    }

    @Test
    void testConvertRefVariableSuccess() {
        Map<String, Object> result = ConverterUtils.convertRefVariable("${node_start.query}");
        assertThat(result).containsEntry("type", "ref");
        assertThat(result).containsEntry("content", List.of("node_start", "query"));
    }

    @Test
    void testConvertRefVariableNested() {
        Map<String, Object> result = ConverterUtils.convertRefVariable("${node_llm.output_of_result}");
        assertThat(result).containsEntry("type", "ref");
        assertThat(result).containsEntry("content", List.of("node_llm", "result", "output"));
    }

    @Test
    void testConvertRefVariableInvalid() {
        assertThatThrownBy(() -> ConverterUtils.convertRefVariable("invalid"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testConvertLlmParamSuccess() {
        Map<String, Object> result = ConverterUtils.convertLlmParam("You are helpful", "{{query}}");
        assertThat(castMap(result.get("systemPrompt"))).containsEntry("type", "template");
        assertThat(castMap(result.get("systemPrompt"))).containsEntry("content", "You are helpful");
        assertThat(castMap(result.get("prompt"))).containsEntry("type", "template");
        assertThat(castMap(result.get("prompt"))).containsEntry("content", "{{query}}");
        assertThat(result).containsKey("mode");
    }

    @Test
    void testConvertLlmParamContainsModelConfig() {
        Map<String, Object> result = ConverterUtils.convertLlmParam("system", "user");
        assertThat(castMap(result.get("mode"))).containsEntry("id", "52");
        assertThat(castMap(result.get("mode"))).containsEntry("name", "siliconf-qwen3-8b");
    }

    @Test
    void testConvertToDictWithDataclassLikeBean() {
        Map<String, Object> result = castMap(ConverterUtils.convertToDict(new Position(100.0, 200.0)));
        assertThat(result).containsEntry("x", 100.0).containsEntry("y", 200.0);
    }

    @Test
    void testConvertToDictWithDict() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", "value");
        data.put("none_key", null);
        Map<String, Object> result = castMap(ConverterUtils.convertToDict(data));
        assertThat(result).containsEntry("key", "value");
        assertThat(result).doesNotContainKey("none_key");
    }

    @Test
    void testConvertToDictWithList() {
        List<Object> data = new ArrayList<>();
        data.add(Map.of("key", "value"));
        data.add(null);
        data.add(Map.of("key2", "value2"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) ConverterUtils.convertToDict(data);
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("key", "value");
        assertThat(result.get(1)).containsEntry("key2", "value2");
    }

    @Test
    void testConvertToDictWithNone() {
        assertThat(castMap(ConverterUtils.convertToDict(null))).isEmpty();
    }

    @Test
    void testConvertToDictRemovesNoneValues() {
        InputsField inputs = new InputsField(null, null);
        Map<String, Object> result = castMap(ConverterUtils.convertToDict(inputs));
        assertThat(result).doesNotContainKeys("llmParam", "code");
    }

    @Test
    void testLlmModelConfigExists() {
        assertThat(ConverterUtils.LLM_MODEL_CONFIG).isNotNull();
    }

    @Test
    void testLlmModelConfigHasRequiredFields() {
        assertThat(ConverterUtils.LLM_MODEL_CONFIG).containsKeys("id", "name", "type");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
