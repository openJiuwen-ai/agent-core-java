/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.visualization;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.workflow.BranchRouter;
import com.openjiuwen.core.workflow.ComponentComposable;
import com.openjiuwen.core.workflow.component.AdvancedLoopComponent;
import com.openjiuwen.core.workflow.component.BranchComponent;
import com.openjiuwen.core.workflow.component.LoopComponent;
import com.openjiuwen.core.workflow.component.SubWorkflowComponent;
import com.openjiuwen.core.workflow.component.llm.IntentDetectionComponentImpl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds a drawable graph representation of a workflow for visualization purposes.
 *
 * <p>Supports adding nodes (including loop, sub-workflow, and branch components),
 * setting start/end/break nodes, adding edges (regular and conditional), and
 * exporting to Mermaid diagram syntax.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.core.graph.visualization.drawable.Drawable}.</p>
 */
public class Drawable {

    private final DrawableGraph graph;
    private final Set<String> loopNodes;

    public Drawable() {
        this.graph = new DrawableGraph();
        this.loopNodes = new HashSet<>();
    }

    /**
     * Convert a component to a DrawableNode and save it to the graph.
     *
     * <p>Handles special component types:
     * <ul>
     *   <li>{@link LoopComponent} / {@link AdvancedLoopComponent} — creates a subgraph node with the loop's inner graph</li>
     *   <li>{@link SubWorkflowComponent} — creates a subgraph node with the sub-workflow's graph</li>
     *   <li>{@link BranchComponent} / {@link IntentDetectionComponent} — creates a node and adds conditional edge</li>
     *   <li>Other — creates a plain node</li>
     * </ul>
     *
     * @param nodeId    the node identifier
     * @param component the component to convert
     */
    public void addNode(String nodeId, ComponentComposable component) {
        if (component instanceof LoopComponent loopComp) {
            DrawableGraph subgraph = loopComp.getLoopGroup().getDrawable().getGraph();
            fillEndNodesIfEmpty(subgraph);
            graph.getNodes().put(nodeId, new DrawableSubgraphNode(nodeId, subgraph));
            loopNodes.add(nodeId);
            addEdge(nodeId, nodeId, false, false, null);
        } else if (component instanceof AdvancedLoopComponent advLoopComp) {
            DrawableGraph subgraph = advLoopComp.getBody().getDrawable().getGraph();
            fillEndNodesIfEmpty(subgraph);
            graph.getNodes().put(nodeId, new DrawableSubgraphNode(nodeId, subgraph));
            loopNodes.add(nodeId);
            addEdge(nodeId, nodeId, false, false, null);
        } else if (component instanceof SubWorkflowComponent subWorkflowComp) {
            DrawableGraph subgraph = subWorkflowComp.getSubWorkflowInternal().getDrawable().getGraph();
            graph.getNodes().put(nodeId, new DrawableSubgraphNode(nodeId, subgraph));
        } else if (component instanceof BranchComponent branchComp) {
            graph.getNodes().put(nodeId, new DrawableNode(nodeId));
            addEdge(nodeId, null, true, false, branchComp.router());
        } else if (component instanceof IntentDetectionComponentImpl intentComp) {
            graph.getNodes().put(nodeId, new DrawableNode(nodeId));
            addEdge(nodeId, null, true, false, intentComp.router());
        } else {
            graph.getNodes().put(nodeId, new DrawableNode(nodeId));
        }
    }

    /**
     * Adds a plain (non-component) node to the graph.
     *
     * @param nodeId the node identifier
     */
    public void addSimpleNode(String nodeId) {
        graph.getNodes().put(nodeId, new DrawableNode(nodeId));
    }

    /**
     * Sets the specified node as a start node.
     *
     * @param nodeId the node identifier
     * @throws RuntimeException if the node does not exist in the graph
     */
    public void setStartNode(String nodeId) {
        if (!graph.getNodes().containsKey(nodeId)) {
            throw ErrorHelper.buildError(StatusCode.DRAWABLE_GRAPH_START_NODE_INVALID,
                    "node_id", nodeId,
                    "reason", "node '" + nodeId + "' does not exist in the graph");
        }
        graph.getStartNodes().add(graph.getNodes().get(nodeId));
    }

    /**
     * Sets the specified node as an end node.
     *
     * @param nodeId the node identifier
     * @throws RuntimeException if the node does not exist in the graph
     */
    public void setEndNode(String nodeId) {
        if (!graph.getNodes().containsKey(nodeId)) {
            throw ErrorHelper.buildError(StatusCode.DRAWABLE_GRAPH_END_NODE_INVALID,
                    "node_id", nodeId,
                    "reason", "node '" + nodeId + "' does not exist in the graph");
        }
        graph.getEndNodes().add(graph.getNodes().get(nodeId));
    }

    /**
     * Sets the specified node as a break node.
     *
     * @param nodeId the node identifier
     * @throws RuntimeException if the node does not exist in the graph
     */
    public void setBreakNode(String nodeId) {
        if (!graph.getNodes().containsKey(nodeId)) {
            throw ErrorHelper.buildError(StatusCode.DRAWABLE_GRAPH_BREAK_NODE_INVALID,
                    "node_id", nodeId,
                    "reason", "node '" + nodeId + "' does not exist in the graph");
        }
        graph.getBreakNodes().add(graph.getNodes().get(nodeId));
    }

    /**
     * Adds an edge to the graph.
     *
     * <p>For loop nodes, the edge is always conditional.
     * For conditional edges with a {@link BranchRouter}, targets are extracted
     * from the router's drawable branch information.</p>
     *
     * @param source      the source node id
     * @param target      the target node id (may be null for conditional edges)
     * @param conditional whether this is a conditional (dotted) edge
     * @param streaming   whether this is a streaming (thick) edge
     * @param data        optional edge data (may be a {@link BranchRouter} for conditional edges)
     */
    public void addEdge(String source, String target, boolean conditional, boolean streaming, Object data) {
        // Loop nodes always get a conditional self-edge
        if (loopNodes.contains(source)) {
            graph.getEdges().add(new DrawableEdge(source, target, null, true, false));
            return;
        }

        if (!conditional) {
            graph.getEdges().add(new DrawableEdge(source, target, data, false, streaming));
            return;
        }

        // Handle conditional edges: extract targets from router/function
        List<String> branchDatas = null;
        List<String> targets;

        if (data instanceof BranchRouter branchRouter) {
            DrawableBranchRouter drawableBranch = branchRouter.getDrawableBranchRouter();
            targets = drawableBranch.getTargets();
            branchDatas = drawableBranch.getDatas();
        } else {
            targets = getTargetsFromCallable(data);
        }

        for (int i = 0; i < targets.size(); i++) {
            String t = targets.get(i);
            Object edgeData = (branchDatas != null) ? branchDatas.get(i) : null;
            graph.getEdges().add(new DrawableEdge(source, t, edgeData, true, streaming));
        }
    }

    /**
     * Convenience method: add a simple edge from source to target.
     *
     * @param source the source node id
     * @param target the target node id
     */
    public void addEdge(String source, String target) {
        addEdge(source, target, false, false, null);
    }

    /**
     * Convert the graph to Mermaid flowchart syntax.
     *
     * @param title           the diagram title
     * @param expandSubgraph  depth of subgraph expansion (0 or false = no expansion, true = full expansion)
     * @param enableAnimation whether to enable animation properties on streaming links
     * @return the Mermaid flowchart syntax string
     */
    public String toMermaid(String title, int expandSubgraph, boolean enableAnimation) {
        return new MermaidDiagram().toMermaid(graph, title, expandSubgraph, enableAnimation);
    }

    /**
     * Convert the graph to Mermaid flowchart syntax with default settings.
     *
     * @return the Mermaid flowchart syntax string
     */
    public String toMermaid() {
        return toMermaid("", 0, false);
    }

    /**
     * Convert the graph to Mermaid syntax and render it as PNG bytes via the mermaid.ink service.
     * <p>
     * Mirrors Python's {@code Drawable.to_mermaid_png()}.
     *
     * @param title          the diagram title
     * @param expandSubgraph depth of subgraph expansion
     * @return PNG image bytes, or empty array if rendering fails
     */
    public byte[] toMermaidPng(String title, int expandSubgraph) {
        String mermaidSyntax = toMermaid(title, expandSubgraph, false);
        if (mermaidSyntax == null || mermaidSyntax.isEmpty()) {
            return new byte[0];
        }
        return MermaidRenderer.renderPng(mermaidSyntax);
    }

    /**
     * Convert the graph to Mermaid syntax and render it as SVG bytes via the mermaid.ink service.
     * <p>
     * Mirrors Python's {@code Drawable.to_mermaid_svg()}.
     *
     * @param title          the diagram title
     * @param expandSubgraph depth of subgraph expansion
     * @return SVG image bytes, or empty array if rendering fails
     */
    public byte[] toMermaidSvg(String title, int expandSubgraph) {
        String mermaidSyntax = toMermaid(title, expandSubgraph, true);
        if (mermaidSyntax == null || mermaidSyntax.isEmpty()) {
            return new byte[0];
        }
        return MermaidRenderer.renderSvg(mermaidSyntax);
    }

    /**
     * Gets the underlying drawable graph.
     *
     * @return the drawable graph
     */
    public DrawableGraph getGraph() {
        return graph;
    }

    // ==================== Private helpers ====================

    /**
     * If the subgraph has no end nodes, discover them by finding nodes with zero out-degree.
     */
    private void fillEndNodesIfEmpty(DrawableGraph subgraph) {
        if (!subgraph.getEndNodes().isEmpty()) {
            return;
        }
        // Calculate out-degrees
        java.util.Map<String, Integer> outDegrees = new java.util.LinkedHashMap<>();
        for (String nodeId : subgraph.getNodes().keySet()) {
            outDegrees.put(nodeId, 0);
        }
        for (DrawableEdge edge : subgraph.getEdges()) {
            outDegrees.merge(edge.getSource(), 1, Integer::sum);
        }
        // Nodes with zero out-degree are end nodes
        for (java.util.Map.Entry<String, Integer> entry : outDegrees.entrySet()) {
            if (entry.getValue() == 0 && subgraph.getNodes().containsKey(entry.getKey())) {
                subgraph.getEndNodes().add(subgraph.getNodes().get(entry.getKey()));
            }
        }
    }

    /**
     * Attempt to extract target node names from a callable's return type annotation.
     *
     * <p>In Python, this uses {@code get_type_hints} to inspect {@code Literal} return types.
     * In Java, reflection-based extraction of return type literals is not directly available,
     * so this returns an empty list. Subclasses or callers may override this behavior.</p>
     *
     * @param data the callable data
     * @return list of target names extracted from the return type, or empty list
     */
    private List<String> getTargetsFromCallable(Object data) {
        // Java doesn't have Literal type hints like Python.
        // If data implements a TargetProvider interface, use it
        if (data instanceof TargetProvider provider) {
            return provider.getTargets();
        }
        return List.of();
    }

    /**
     * Optional interface for callables that can provide their target node names.
     *
     * <p>This replaces Python's runtime type-hint inspection of {@code Literal} return types.</p>
     */
    public interface TargetProvider {
        /**
         * Gets the list of possible target node names.
         *
         * @return the target names
         */
        List<String> getTargets();
    }
}
