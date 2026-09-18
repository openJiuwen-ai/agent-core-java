/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.graph.visualization;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DrawableSubgraphNodeTest {

    @Test
    void defaultConstructorPreservesDataclassDefaults() {
        DrawableSubgraphNode node = new DrawableSubgraphNode("subgraph-node");

        assertThat(node.getId()).isEqualTo("subgraph-node");
        assertThat(node.getName()).isNull();
        assertThat(node.getMetadata()).isNull();
        assertThat(node.getSubgraph()).isNull();
    }

    @Test
    void fullConstructorCarriesInheritedFieldsAndSubgraph() {
        DrawableGraph nested = new DrawableGraph(Map.of(), List.of(), List.of(), List.of(), List.of());
        DrawableSubgraphNode node = new DrawableSubgraphNode(
                "subgraph-node",
                "Loop",
                Map.of("level", 2),
                nested
        );

        assertThat(node.getId()).isEqualTo("subgraph-node");
        assertThat(node.getName()).isEqualTo("Loop");
        assertThat(node.getMetadata()).containsEntry("level", 2);
        assertThat(node.getSubgraph()).isSameAs(nested);
    }

    @Test
    void setterUpdatesSubgraphReference() {
        DrawableSubgraphNode node = new DrawableSubgraphNode("subgraph-node");
        DrawableGraph nested = new DrawableGraph(Map.of(), List.of(), List.of(), List.of(), List.of());

        node.setSubgraph(nested);

        assertThat(node.getSubgraph()).isSameAs(nested);
    }
}
