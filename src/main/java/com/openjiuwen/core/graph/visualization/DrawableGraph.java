/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.visualization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Container for a drawable graph representation used in visualization.
 *
 * <p>Holds the nodes, edges, start/end/break nodes for a graph.
 * Mirrors Python's {@code openjiuwen.core.graph.visualization.drawable_graph.DrawableGraph}.</p>
 */
public class DrawableGraph {

    private final Map<String, DrawableNode> nodes;
    private final List<DrawableEdge> edges;
    private final List<DrawableNode> startNodes;
    private final List<DrawableNode> endNodes;
    private List<DrawableNode> breakNodes;

    public DrawableGraph() {
        this.nodes = new LinkedHashMap<>();
        this.edges = new ArrayList<>();
        this.startNodes = new ArrayList<>();
        this.endNodes = new ArrayList<>();
        this.breakNodes = new ArrayList<>();
    }

    public DrawableGraph(Map<String, DrawableNode> nodes, List<DrawableEdge> edges,
                         List<DrawableNode> startNodes, List<DrawableNode> endNodes,
                         List<DrawableNode> breakNodes) {
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
