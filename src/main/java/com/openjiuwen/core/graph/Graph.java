/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.session.BaseSession;

import java.util.List;
import java.util.Map;

/**
 * Abstract graph definition with node/edge management and compilation.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.base.Graph}.
 */
public abstract class Graph {

    /**
     * Set the start node for the graph.
     *
     * @param nodeId the start node identifier
     * @return this graph
     */
    public Graph startNode(String nodeId) {
        return this;
    }

    /**
     * Set the end node for the graph.
     *
     * @param nodeId the end node identifier
     * @return this graph
     */
    public Graph endNode(String nodeId) {
        return this;
    }

    /**
     * Add a node to the graph.
     *
     * @param nodeId     the node identifier
     * @param node       the executable for this node
     * @param waitForAll if true, this node must wait for all predecessors (barrier semantics)
     * @return this graph
     */
    public abstract Graph addNode(String nodeId, Executable<?, ?> node, boolean waitForAll);

    /**
     * Add a node to the graph (no barrier, default).
     *
     * @param nodeId the node identifier
     * @param node   the executable for this node
     * @return this graph
     */
    public Graph addNode(String nodeId, Executable<?, ?> node) {
        return addNode(nodeId, node, false);
    }

    /**
     * Add an edge from one or more source nodes to a target node.
     *
     * @param sourceNodeId single string or list of strings for source nodes
     * @param targetNodeId the target node identifier
     * @return this graph
     */
    public abstract Graph addEdge(Object sourceNodeId, String targetNodeId);

    /**
     * Add conditional edges from a source node using a router.
     *
     * @param sourceNodeId the source node identifier
     * @param router       router function for conditional branching
     * @return this graph
     */
    public abstract Graph addConditionalEdges(String sourceNodeId, Object router);

    /**
     * Compile the graph into an executable form.
     *
     * @param session execution session
     * @return an executable graph
     */
    public abstract ExecutableGraph<?, ?> compile(BaseSession session);

    /**
     * Compile the graph into an executable form with additional keyword arguments.
     *
     * @param session execution session
     * @param kwargs  additional arguments (e.g. "context" for ModelContext)
     * @return an executable graph
     */
    public ExecutableGraph<?, ?> compile(BaseSession session, Map<String, Object> kwargs) {
        return compile(session);
    }

    /**
     * Get the nodes in this graph.
     *
     * @return a map of node IDs to executables
     */
    public abstract Map<String, Executable<?, ?>> getNodes();
}
