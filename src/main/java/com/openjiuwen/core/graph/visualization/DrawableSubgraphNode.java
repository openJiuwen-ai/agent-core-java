/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.visualization;

/**
 * A drawable node that contains a subgraph for nested visualization.
 *
 * <p>Used for loop components and sub-workflow components that contain inner graphs.
 * Mirrors Python's {@code openjiuwen.core.graph.visualization.drawable_subgraph_node.DrawableSubgraphNode}.</p>
 */
public class DrawableSubgraphNode extends DrawableNode {

    private DrawableGraph subgraph;

    public DrawableSubgraphNode(String id) {
        super(id);
    }

    public DrawableSubgraphNode(String id, DrawableGraph subgraph) {
        super(id);
        this.subgraph = subgraph;
    }

    public DrawableGraph getSubgraph() {
        return subgraph;
    }

    public void setSubgraph(DrawableGraph subgraph) {
        this.subgraph = subgraph;
    }
}
