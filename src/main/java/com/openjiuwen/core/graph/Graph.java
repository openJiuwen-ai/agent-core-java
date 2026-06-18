/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.session.BaseSession;

import java.util.Map;

/**
 * Mirrors Python's {@code Graph} in
 * {@code openjiuwen/core/graph/base.py}.
 */
public class Graph {

    /**
     * Python's base implementation is {@code pass}.
     *
     * @param nodeId node identifier
     * @return null, matching Python's implicit {@code None}
     */
    public Graph startNode(String nodeId) {
        return null;
    }

    /**
     * Python's base implementation is {@code pass}.
     *
     * @param nodeId node identifier
     * @return null, matching Python's implicit {@code None}
     */
    public Graph endNode(String nodeId) {
        return null;
    }

    /**
     * Python's base implementation is {@code pass}.
     *
     * @param nodeId node identifier
     * @param node executable node
     * @param waitForAll whether to wait for all upstream nodes
     * @return null, matching Python's implicit {@code None}
     */
    public Graph addNode(String nodeId, Executable<?, ?> node, boolean waitForAll) {
        return null;
    }

    /**
     * Default overload matching Python's keyword default {@code wait_for_all=False}.
     *
     * @param nodeId node identifier
     * @param node executable node
     * @return null, matching Python's implicit {@code None}
     */
    public Graph addNode(String nodeId, Executable<?, ?> node) {
        return addNode(nodeId, node, false);
    }

    /**
     * Python accepts either a string source node or a list of string source nodes.
     *
     * @param sourceNodeId source node id or list of source node ids
     * @param targetNodeId target node identifier
     * @return null, matching Python's implicit {@code None}
     */
    public Graph addEdge(Object sourceNodeId, String targetNodeId) {
        return null;
    }

    /**
     * Python accepts any router object.
     *
     * @param sourceNodeId source node identifier
     * @param router router object
     * @return null, matching Python's implicit {@code None}
     */
    public Graph addConditionalEdges(String sourceNodeId, Object router) {
        return null;
    }

    /**
     * Python's base implementation is {@code pass}.
     *
     * @param session execution session
     * @param kwargs keyword-style options
     * @return null, matching Python's implicit {@code None}
     */
    public ExecutableGraph<?, ?> compile(BaseSession session, Map<String, Object> kwargs) {
        return null;
    }

    /**
     * Compile without keyword-style options.
     *
     * @param session execution session
     * @return null, matching Python's implicit {@code None}
     */
    public ExecutableGraph<?, ?> compile(BaseSession session) {
        return compile(session, Map.of());
    }

    /**
     * Python's base implementation is {@code pass}.
     *
     * @return null, matching Python's implicit {@code None}
     */
    public Map<String, ?> getNodes() {
        return null;
    }
}
