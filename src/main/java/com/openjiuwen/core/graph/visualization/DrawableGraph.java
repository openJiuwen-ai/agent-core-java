/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.visualization;

import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code DrawableGraph} in
 * {@code openjiuwen/core/graph/visualization/drawable_graph.py}.
 */
public class DrawableGraph {

    private final Map<String, DrawableNode> nodes;
    private final List<DrawableEdge> edges;
    private final List<DrawableNode> startNodes;
    private final List<DrawableNode> endNodes;
    private List<DrawableNode> breakNodes;

    public DrawableGraph(
            Map<String, DrawableNode> nodes,
            List<DrawableEdge> edges,
            List<DrawableNode> startNodes,
            List<DrawableNode> endNodes,
            List<DrawableNode> breakNodes
    ) {
        this.nodes = nodes;
        this.edges = edges;
        this.startNodes = startNodes;
        this.endNodes = endNodes;
        this.breakNodes = breakNodes;
    }

    public Map<String, DrawableNode> getNodes() {
        return nodes;
    }

    public List<DrawableEdge> getEdges() {
        return edges;
    }

    public List<DrawableNode> getStartNodes() {
        return startNodes;
    }

    public List<DrawableNode> getEndNodes() {
        return endNodes;
    }

    public List<DrawableNode> getBreakNodes() {
        return breakNodes;
    }

    public void setBreakNodes(List<DrawableNode> breakNodes) {
        this.breakNodes = breakNodes;
    }
}
