/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code LLMConverter} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/llm_converter.py}.
 */
class LLMConverterTest {

    @Test
    void convertForcesJsonWhenMultipleOutputsUseTextLikeFormat() {
        LLMConverter converter = new LLMConverter(nodeData("markdown", outputs("answer", "reason")), Map.of(), new Position(0, 0));

        converter.convert();

        assertThat(converter.getNode().getId()).isEqualTo("node_llm");
        assertThat(converter.getNode().getType()).isEqualTo(NodeType.LLM.getDslType());
        assertThat(converter.getNode().getData().getOutputFormat()).isEqualTo("json");
        assertThat(castMap(converter.getNode().getData().getInputs().getLlmParam().get("response_format")))
                .containsEntry("type", "json");
        assertThat(converter.getNode().getData().getInputs().getInputParameters()).containsKey("query");
        assertThat(converter.getNode().getData().getOutputs().getProperties()).containsKeys("answer", "reason");
        assertThat(converter.getEdges()).hasSize(1);
        assertThat(converter.getEdges().getFirst().getTargetNodeId()).isEqualTo("node_end");
    }

    @Test
    void convertFallsBackToTextForSingleOutputInvalidFormat() {
        LLMConverter converter = new LLMConverter(nodeData("xml", outputs("answer")), Map.of(), new Position(0, 0));

        converter.convert();

        assertThat(converter.getNode().getData().getOutputFormat()).isEqualTo("text");
        assertThat(castMap(converter.getNode().getData().getInputs().getLlmParam().get("response_format")))
                .containsEntry("type", "text");
    }

    private static Map<String, Object> nodeData(String outputFormat, List<Map<String, Object>> outputs) {
        Map<String, Object> configs = new LinkedHashMap<>();
        configs.put("system_prompt", "You are helpful");
        configs.put("user_prompt", "{{query}}");
        configs.put("output_format", outputFormat);

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "node_llm");
        node.put("type", "LLM");
        node.put("description", "llm node");
        node.put("parameters", Map.of(
                "inputs", List.of(Map.of("name", "query", "value", "${node_start.query}")),
                "outputs", outputs,
                "configs", configs
        ));
        node.put("next", "node_end");
        return node;
    }

    private static List<Map<String, Object>> outputs(String... names) {
        return java.util.Arrays.stream(names)
                .map(name -> Map.<String, Object>of("name", name, "description", name + " output", "type", "string"))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
