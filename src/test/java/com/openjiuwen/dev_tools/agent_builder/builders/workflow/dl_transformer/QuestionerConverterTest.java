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
 * Mirrors Python's {@code QuestionerConverter} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/questioner_converter.py}.
 */
class QuestionerConverterTest {

    @Test
    void convertAddsDefaultInputAndQuestionerOutputs() {
        QuestionerConverter converter = new QuestionerConverter(nodeData(List.of(), outputs("answer")), Map.of(), new Position(0, 0));

        converter.convert();

        assertThat(converter.getNode().getId()).isEqualTo("node_questioner");
        assertThat(converter.getNode().getType()).isEqualTo(NodeType.Questioner.getDslType());
        assertThat(converter.getNode().getData().getInputs().getInputParameters()).containsKey("input");
        assertThat(converter.getNode().getData().getInputs().getInputParameters().get("input").getType())
                .isEqualTo(SourceType.ref.getValue());
        assertThat(converter.getNode().getData().getInputs().getInputParameters().get("input").getContent())
                .isEqualTo("node_start.query");
        assertThat(converter.getNode().getData().getInputs().getLlmParam().get("systemPrompt"))
                .isEqualTo(converter.getNode().getData().getInputs().getSystemPrompt());
        assertThat(converter.getNode().getData().getInputs().getHistoryEnable()).isFalse();
        assertThat(converter.getNode().getData().getInputs().getMaxResponse()).isEqualTo(3);

        OutputsField outputs = converter.getNode().getData().getOutputs();
        assertThat(outputs.getProperties()).containsKeys("answer", "user_response", "output");
        assertThat(outputs.getProperties().get("user_response").getDescription()).isEqualTo("用户响应输出变量");
        assertThat(outputs.getProperties().get("output").getDescription()).isEqualTo("输出变量");
        assertThat(outputs.getRequired()).containsExactly("answer", "user_response");
        assertThat(converter.getEdges()).hasSize(1);
        assertThat(converter.getEdges().getFirst().getTargetNodeId()).isEqualTo("node_end");
    }

    @Test
    void convertKeepsProvidedInputAndExistingOutputAliases() {
        List<Map<String, Object>> inputs = List.of(Map.of("name", "query", "value", "${node_start.query}"));
        List<Map<String, Object>> outputSpecs = List.of(
                Map.of("name", "output", "description", "main output", "type", "string"),
                Map.of("name", "user_response", "description", "user response", "type", "string")
        );
        QuestionerConverter converter = new QuestionerConverter(nodeData(inputs, outputSpecs), Map.of(), new Position(0, 0));

        converter.convert();

        assertThat(converter.getNode().getData().getInputs().getInputParameters()).containsKey("query");
        assertThat(converter.getNode().getData().getInputs().getInputParameters()).doesNotContainKey("input");
        assertThat(converter.getNode().getData().getOutputs().getProperties()).containsOnlyKeys("output", "user_response");
        assertThat(converter.getNode().getData().getOutputs().getRequired()).containsExactly("user_response");
    }

    private static Map<String, Object> nodeData(List<Map<String, Object>> inputs, List<Map<String, Object>> outputs) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "node_questioner");
        node.put("type", "Questioner");
        node.put("description", "questioner node");
        node.put("parameters", Map.of(
                "inputs", inputs,
                "outputs", outputs,
                "configs", Map.of("prompt", "Ask for missing fields")
        ));
        node.put("next", "node_end");
        return node;
    }

    private static List<Map<String, Object>> outputs(String name) {
        return List.of(Map.of("name", name, "description", name + " output", "type", "string"));
    }
}
