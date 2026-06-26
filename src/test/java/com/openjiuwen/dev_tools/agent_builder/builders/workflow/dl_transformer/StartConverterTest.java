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
 * Mirrors Python's {@code StartConverter} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/start_converter.py}.
 */
class StartConverterTest {

    @Test
    void convertBuildsOutputsRequiredAndInheritedEdge() {
        StartConverter converter = new StartConverter(nodeData(outputs(), true), Map.of(), new Position(5, 6));

        converter.convert();

        assertThat(converter.getNode().getId()).isEqualTo("node_start");
        assertThat(converter.getNode().getType()).isEqualTo(NodeType.Start.getDslType());
        assertThat(converter.getNode().getData().getOutputs().getProperties()).containsKeys("query", "profile");
        assertThat(converter.getNode().getData().getOutputs().getProperties().get("query").getDescription())
                .isEqualTo("user query");
        assertThat(converter.getNode().getData().getOutputs().getRequired()).containsExactly("query", "profile");
        assertThat(converter.getEdges()).hasSize(1);
        assertThat(converter.getEdges().getFirst().getTargetNodeId()).isEqualTo("node_next");
    }

    @Test
    void convertLeavesRequiredUnsetWhenThereAreNoOutputProperties() {
        StartConverter converter = new StartConverter(nodeData(List.of(), false), Map.of(), new Position(0, 0));

        converter.convert();

        assertThat(converter.getNode().getData().getOutputs().getProperties()).isNull();
        assertThat(converter.getNode().getData().getOutputs().getRequired()).isNull();
        assertThat(converter.getEdges()).isEmpty();
    }

    private static Map<String, Object> nodeData(List<Map<String, Object>> outputs, boolean withNext) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "node_start");
        node.put("type", "Start");
        node.put("description", "start node");
        node.put("parameters", Map.of("outputs", outputs));
        if (withNext) {
            node.put("next", "node_next");
        }
        return node;
    }

    private static List<Map<String, Object>> outputs() {
        return List.of(
                Map.of("name", "query", "description", "user query", "type", "string"),
                Map.of("name", "profile", "description", "profile object", "type", "object")
        );
    }
}
