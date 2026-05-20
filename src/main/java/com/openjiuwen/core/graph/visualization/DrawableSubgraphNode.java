/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public DrawableSubgraphNode(String id) {
        super(id);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public DrawableSubgraphNode(String id, DrawableGraph subgraph) {
        super(id);
        this.subgraph = subgraph;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public DrawableGraph getSubgraph() {
        return subgraph;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSubgraph(DrawableGraph subgraph) {
        this.subgraph = subgraph;
    }
}
