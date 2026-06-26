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
 * Mirrors Python's {@code IntentDetectionConverter} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/intent_detection_converter.py}.
 */
class IntentDetectionConverterTest {

    @Test
    void convertBuildsIntentInputsOutputsAndEdges() {
        IntentDetectionConverter converter = new IntentDetectionConverter(nodeData(), Map.of(), new Position(0, 0));

        converter.convert();

        assertThat(converter.getNode().getId()).isEqualTo("node_intent");
        assertThat(converter.getNode().getType()).isEqualTo(NodeType.IntentDetection.getDslType());
        InputsField inputs = converter.getNode().getData().getInputs();
        assertThat(inputs.getInputParameters()).containsOnlyKeys("query");
        assertThat(castMap(inputs.getLlmParam().get("prompt"))).containsEntry("content", "Classify the user request");
        assertThat(inputs.getLlmParam()).containsEntry("model", ConverterUtils.LLM_MODEL_CONFIG);
        assertThat(inputs.getIntents()).hasSize(2);
        assertThat(inputs.getIntents().getFirst()).containsEntry("name", "sales").containsEntry("id", "intent_sales");
        assertThat(inputs.getIntents().get(1).get("name")).isEqualTo("support");
        assertThat(inputs.getIntents().get(1).get("id")).startsWith("intent_");
        assertThat(converter.getNode().getData().getOutputs().getProperties()).containsKey("classification_id");
        assertThat(converter.getNode().getData().getOutputs().getRequired()).containsExactly("classification_id");

        assertThat(converter.getEdges()).hasSize(3);
        assertThat(converter.getEdges().get(0).getSourcePortId()).isEqualTo("intent_sales");
        assertThat(converter.getEdges().get(1).getSourcePortId()).startsWith("intent_");
        assertThat(converter.getEdges().get(2).getSourcePortId()).isEqualTo("0");
    }

    @Test
    void convertIntentsSkipsDefaultBranches() {
        List<Map<String, String>> intents = IntentDetectionConverter.convertIntents(conditions());

        assertThat(intents).hasSize(2);
        assertThat(intents).noneSatisfy(intent -> assertThat(intent.get("name")).isEqualTo("default"));
    }

    private static Map<String, Object> nodeData() {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "node_intent");
        node.put("type", "IntentDetection");
        node.put("description", "intent node");
        node.put("parameters", Map.of(
                "inputs", List.of(Map.of("name", "request", "value", "${node_start.query}")),
                "conditions", conditions(),
                "configs", Map.of("prompt", "Classify the user request")
        ));
        return node;
    }

    private static List<Map<String, Object>> conditions() {
        return List.of(
                Map.of(
                        "branch", "sales_branch",
                        "expression", "${node_intent.rawOutput} contain sales",
                        "intent_id", "intent_sales",
                        "next", "node_sales"
                ),
                Map.of(
                        "branch", "support_branch",
                        "expression", "${node_intent.rawOutput} contain support",
                        "next", "node_support"
                ),
                Map.of(
                        "branch", "default",
                        "expression", "default",
                        "next", "node_default"
                )
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
