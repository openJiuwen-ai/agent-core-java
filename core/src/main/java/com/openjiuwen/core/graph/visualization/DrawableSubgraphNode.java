/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.graph.visualization;

import java.util.Map;

/**
 * Mirrors Python's {@code DrawableSubgraphNode} in
 * {@code openjiuwen/core/graph/visualization/drawable_subgraph_node.py}.
 */
public class DrawableSubgraphNode extends DrawableNode {

    private DrawableGraph subgraph;

    public DrawableSubgraphNode(String id) {
        this(id, null, null, null);
    }

    public DrawableSubgraphNode(String id, DrawableGraph subgraph) {
        this(id, null, null, subgraph);
    }

    public DrawableSubgraphNode(String id, String name, Map<String, Object> metadata, DrawableGraph subgraph) {
        super(id, name, metadata);
        this.subgraph = subgraph;
    }

    public DrawableGraph getSubgraph() {
        return subgraph;
    }

    public void setSubgraph(DrawableGraph subgraph) {
        this.subgraph = subgraph;
    }
}
