/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.DlTransformModels.DlEdge;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.DlTransformModels.DlGraph;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.DlTransformModels.DlNode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for DL Transformer Models module.
 * <p>
 * Mirrors Python's {@code test_models_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow.dl_transformer}.
 */
class TestModelsIntegration {

    @Nested
    class TestDlNodeIntegration {

        @Test
        void nodeCreation() {
            DlNode node = new DlNode("node_1", "Start");
            assertThat(node.getId()).isEqualTo("node_1");
            assertThat(node.getType()).isEqualTo("Start");
        }

        @Test
        void nodeProperties() {
            DlNode node = new DlNode("node_1", "Start");
            node.setProperty("description", "Test node");
            assertThat(node.getProperties()).containsEntry("description", "Test node");
        }
    }

    @Nested
    class TestDlEdgeIntegration {

        @Test
        void edgeCreation() {
            DlEdge edge = new DlEdge("node_1", "node_2", "default");
            assertThat(edge.getSource()).isEqualTo("node_1");
            assertThat(edge.getTarget()).isEqualTo("node_2");
            assertThat(edge.getLabel()).isEqualTo("default");
        }
    }

    @Nested
    class TestDlGraphIntegration {

        @Test
        void graphCreation() {
            DlGraph graph = new DlGraph();
            assertThat(graph.getNodes()).isEmpty();
            assertThat(graph.getEdges()).isEmpty();
        }

        @Test
        void graphAddNode() {
            DlGraph graph = new DlGraph();
            graph.addNode(new DlNode("node_1", "Start"));
            assertThat(graph.getNodes()).hasSize(1);
            assertThat(graph.getNodes().get(0).getId()).isEqualTo("node_1");
        }

        @Test
        void graphAddEdge() {
            DlGraph graph = new DlGraph();
            graph.addEdge(new DlEdge("node_1", "node_2", "default"));
            assertThat(graph.getEdges()).hasSize(1);
        }
    }
}
