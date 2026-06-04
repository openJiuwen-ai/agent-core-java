/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for DL Transformer Models module.
 * <p>
 * Mirrors Python's {@code test_models_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow.dl_transformer}.
 */
class TestModelsIntegration {

    @Test
    void testAllNodeTypesHaveDlType() {
        for (NodeType nodeType : List.of(NodeType.Start, NodeType.End, NodeType.LLM, NodeType.IntentDetection,
                NodeType.Questioner, NodeType.Code, NodeType.Plugin, NodeType.Output, NodeType.Branch)) {
            assertThat(nodeType.getDlType()).isNotNull();
        }
    }

    @Test
    void testAllNodeTypesHaveDslType() {
        for (NodeType nodeType : List.of(NodeType.Start, NodeType.End, NodeType.LLM, NodeType.IntentDetection,
                NodeType.Questioner, NodeType.Code, NodeType.Plugin, NodeType.Output, NodeType.Branch)) {
            assertThat(nodeType.getDslType()).isNotNull();
        }
    }

    @Test
    void testNodeTypeMappingConsistency() {
        assertThat(NodeType.Start.getDlType()).isEqualTo("Start");
        assertThat(NodeType.Start.getDslType()).isEqualTo("1");
        assertThat(NodeType.End.getDlType()).isEqualTo("End");
        assertThat(NodeType.End.getDslType()).isEqualTo("2");
        assertThat(NodeType.LLM.getDlType()).isEqualTo("LLM");
        assertThat(NodeType.LLM.getDslType()).isEqualTo("3");
    }

    @Test
    void testPositionCreation() {
        Position position = new Position(100.0, 200.0);
        assertThat(position.getX()).isEqualTo(100.0);
        assertThat(position.getY()).isEqualTo(200.0);
    }

    @Test
    void testPositionAttributes() {
        Position position = new Position(100.0, 200.0);
        assertThat(position.getX()).isNotNull();
        assertThat(position.getY()).isNotNull();
    }

    @Test
    void testInputVariableRefType() {
        InputVariable variable = new InputVariable("ref", List.of("node_start", "query"), Map.of());
        assertThat(variable.getType()).isEqualTo("ref");
        assertThat(variable.getContent()).isEqualTo(List.of("node_start", "query"));
    }

    @Test
    void testInputVariableConstantType() {
        InputVariable variable = new InputVariable("constant", "test value", Map.of());
        assertThat(variable.getType()).isEqualTo("constant");
        assertThat(variable.getContent()).isEqualTo("test value");
    }

    @Test
    void testOutputsFieldCreation() {
        OutputsField outputs = new OutputsField();
        assertThat(outputs.getType()).isEqualTo("object");
        assertThat(outputs.getProperties()).isNull();
    }

    @Test
    void testOutputsFieldAddProperty() {
        OutputsField outputs = new OutputsField();
        outputs.addProperty(new OutputPropertySpec(List.of("output"), "output description", 0, "string"));
        assertThat(outputs.getProperties()).containsKey("output");
        assertThat(outputs.getProperties().get("output").getType()).isEqualTo("string");
    }

    @Test
    void testOutputsFieldAddNestedProperty() {
        OutputsField outputs = new OutputsField();
        outputs.addProperty(new OutputPropertySpec(List.of("data", "name"), "name description", 0, "string"));
        assertThat(outputs.getProperties()).containsKey("data");
        assertThat(outputs.getProperties().get("data").getType()).isEqualTo("object");
    }

    @Test
    void testNodeCreation() {
        Node node = new Node("node_1", "1");
        assertThat(node.getId()).isEqualTo("node_1");
        assertThat(node.getType()).isEqualTo("1");
        assertThat(node.getMeta()).isEmpty();
        assertThat(node.getData().getTitle()).isEmpty();
    }

    @Test
    void testNodeWithMeta() {
        Node node = new Node("node_1", "1", Map.of("position", Map.of("x", 100, "y", 200)));
        @SuppressWarnings("unchecked")
        Map<String, Object> position = (Map<String, Object>) node.getMeta().get("position");
        assertThat(position).containsEntry("x", 100).containsEntry("y", 200);
    }

    @Test
    void testEdgeCreation() {
        Edge edge = new Edge("node_1", "node_2");
        assertThat(edge.getSourceNodeId()).isEqualTo("node_1");
        assertThat(edge.getTargetNodeId()).isEqualTo("node_2");
    }

    @Test
    void testEdgeWithSourcePort() {
        Edge edge = new Edge("node_1", "node_2", "output_1");
        assertThat(edge.getSourcePortId()).isEqualTo("output_1");
    }

    @Test
    void testWorkflowCreation() {
        Workflow workflow = new Workflow();
        assertThat(workflow.getNodes()).isEmpty();
        assertThat(workflow.getEdges()).isEmpty();
    }
}
