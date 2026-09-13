/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.visualization;

/**
 * A drawable node that contains a subgraph for nested visualization.
 * <p>
 * Used for loop components and sub-workflow components that contain inner graphs.
 * Mirrors Python's {@code openjiuwen.core.graph.visualization.drawable_subgraph_node.DrawableSubgraphNode}.
 * </p>
 * 
 * @since 0.1.7
 */
public class DrawableSubgraphNode extends DrawableNode {
    private DrawableGraph subgraph;

    /**
     * DrawableSubgraphNode.
     * 
     * @param id id
     * @since 0.1.7
     */
    public DrawableSubgraphNode(String id) {
        super(id);
    }

    /**
     * DrawableSubgraphNode.
     * 
     * @param id id
     * @param subgraph subgraph
     * @since 0.1.7
     */
    public DrawableSubgraphNode(String id, DrawableGraph subgraph) {
        super(id);
        this.subgraph = subgraph;
    }

    /**
     * getSubgraph.
     * 
     * @return the result
     * @since 0.1.7
     */
    public DrawableGraph getSubgraph() {
        return subgraph;
    }

    /**
     * setSubgraph.
     * 
     * @param subgraph subgraph
     * @since 0.1.7
     */
    public void setSubgraph(DrawableGraph subgraph) {
        this.subgraph = subgraph;
    }
}
