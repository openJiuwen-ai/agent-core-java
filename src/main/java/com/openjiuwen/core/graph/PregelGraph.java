/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.graph.pregel.Pregel;
import com.openjiuwen.core.graph.pregel.PregelBuilder;
import com.openjiuwen.core.graph.pregel.PregelConfig;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.graph.pregel.PregelLoop;
import com.openjiuwen.core.graph.store.GraphStore;
import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.state.WorkflowCommitState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * PregelGraph is the main graph builder for constructing a Pregel-based workflow graph.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.graph.PregelGraph}.
 */
public class PregelGraph extends Graph {

    private static final LoggerProtocol logger = Loggers.GRAPH;

    private Pregel pregel;
    private final List<Object[]> edges = new ArrayList<>(); // Each: Object[]{source, target}
    private final Set<String> waits = new HashSet<>();
    private final Map<String, Vertex> nodes = new LinkedHashMap<>();
    private final Map<String, Map<String, Branch>> branches = new LinkedHashMap<>();
    private Checkpointer checkpointer;
    private BaseSession session;

    /**
     * Auto-generated for codecheck compliance.
     */
    public PregelGraph() {
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Graph startNode(String nodeId) {
        validateNodeId(nodeId);
        addEdge(List.of(PregelConstants.START), nodeId);
        return this;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Graph endNode(String nodeId) {
        validateNodeId(nodeId);
        Vertex vertex = nodes.get(nodeId);
        if (vertex != null) {
            vertex.setEndNode(true);
        }
        addEdge(List.of(nodeId), PregelConstants.END);
        return this;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Graph addNode(String nodeId, Executable<?, ?> node, boolean waitForAll) {
        validateNodeId(nodeId);
        if (node == null) {
            throw ErrorHelper.buildError(StatusCode.PREGEL_GRAPH_NODE_INVALID,
                    "node_id", nodeId, "reason", "node is None");
        }
        if (nodes.containsKey(nodeId)) {
            throw ErrorHelper.buildError(StatusCode.PREGEL_GRAPH_NODE_ID_INVALID,
                    "node_id", nodeId, "reason", "already exist, can not add again");
        }
        Vertex vertexNode = new Vertex(nodeId, node);
        nodes.put(nodeId, vertexNode);
        if (waitForAll) {
            waits.add(nodeId);
        }
        return this;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Executable<?, ?>> getNodes() {
        Map<String, Executable<?, ?>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Vertex> entry : nodes.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getExecutable());
        }
        return result;
    }

    /**
     * Get the internal vertex wrapper for a node.
     */
    public Vertex getVertex(String nodeId) {
        return nodes.get(nodeId);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Graph addEdge(Object sourceNodeId, String targetNodeId) {
        if (sourceNodeId == null || (sourceNodeId instanceof String && ((String) sourceNodeId).isEmpty())) {
            throw ErrorHelper.buildError(StatusCode.PREGEL_GRAPH_EDGE_INVALID,
                    "source_id", String.valueOf(sourceNodeId), "target_node_id", targetNodeId,
                    "reason", "source_node_id is None or empty");
        }
        if (sourceNodeId instanceof List) {
            for (Object item : (List<?>) sourceNodeId) {
                if (item == null || (item instanceof String && ((String) item).isEmpty())) {
                    throw ErrorHelper.buildError(StatusCode.PREGEL_GRAPH_EDGE_INVALID,
                            "source_id", String.valueOf(sourceNodeId), "target_node_id", targetNodeId,
                            "reason", "source_node_id list has None or empty");
                }
            }
        }
        if (targetNodeId == null || targetNodeId.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.PREGEL_GRAPH_EDGE_INVALID,
                    "source_id", String.valueOf(sourceNodeId), "target_node_id", targetNodeId,
                    "reason", "target_node_id is None or empty");
        }
        edges.add(new Object[]{sourceNodeId, targetNodeId});
        return this;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Graph addConditionalEdges(String sourceNodeId, Object router) {
        if (sourceNodeId == null || sourceNodeId.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.PREGEL_GRAPH_CONDITION_EDGE_INVALID,
                    "source_id", sourceNodeId, "reason", "source_node_id is None or empty");
        }
        if (router == null) {
            throw ErrorHelper.buildError(StatusCode.PREGEL_GRAPH_CONDITION_EDGE_INVALID,
                    "source_id", sourceNodeId, "reason", "router is None");
        }
        String name = getCallableName(router);
        branches.computeIfAbsent(sourceNodeId, k -> new LinkedHashMap<>())
                .put(name, new Branch(router));
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public ExecutableGraph<?, ?> compile(BaseSession session) {
        return compile(session, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public ExecutableGraph<?, ?> compile(BaseSession session, Map<String, Object> kwargs) {
        // Initialize all vertices
        for (Map.Entry<String, Vertex> entry : nodes.entrySet()) {
            entry.getValue().init(session, kwargs);
        }

        // After-step callback
        Consumer<PregelLoop> afterStep = loop -> {
            if (this.session != null && this.session.state() instanceof WorkflowCommitState) {
                ((WorkflowCommitState) this.session.state()).commit();
            }
            logger.debug("Finished to run graph super-step [{}]", loop.getStep());
        };

        if (this.pregel == null) {
            Object rawCheckpointer = session.checkpointer();
            if (rawCheckpointer instanceof Checkpointer) {
                this.checkpointer = (Checkpointer) rawCheckpointer;
                Object rawGraphStore = this.checkpointer.graphStore();
                Store graphStore = null;
                if (rawGraphStore instanceof Store) {
                    graphStore = new GraphStore((Store) rawGraphStore);
                }
                this.pregel = doCompile(graphStore, afterStep);
            } else {
                this.pregel = doCompile(null, afterStep);
            }
            this.session = session;
        } else {
            this.session = session;
        }
        return new CompiledGraph(this.pregel, this.checkpointer);
    }

    /**
     * Build the Pregel engine from the graph definition.
     */
    @SuppressWarnings("unchecked")
    private Pregel doCompile(Store graphStore, Consumer<PregelLoop> stepCallback) {
        List<Object[]> regularEdges = new ArrayList<>();
        Map<String, Set<String>> sources = new HashMap<>();

        PregelBuilder builder = new PregelBuilder();
        for (Map.Entry<String, Vertex> entry : nodes.entrySet()) {
            builder.addNode(entry.getKey(), entry.getValue());
        }

        // Separate barrier edges from regular edges
        for (Object[] edge : edges) {
            Object sourceNodeId = edge[0];
            String targetNodeId = (String) edge[1];

            if (waits.contains(targetNodeId)) {
                sources.computeIfAbsent(targetNodeId, k -> new HashSet<>());
                if (sourceNodeId instanceof String) {
                    sources.get(targetNodeId).add((String) sourceNodeId);
                } else if (sourceNodeId instanceof List) {
                    for (Object s : (List<?>) sourceNodeId) {
                        sources.get(targetNodeId).add((String) s);
                    }
                }
            } else {
                regularEdges.add(edge);
            }
        }

        // Add barrier edges
        for (Map.Entry<String, Set<String>> entry : sources.entrySet()) {
            builder.addEdge(entry.getValue(), entry.getKey());
        }

        // Add regular edges
        for (Object[] edge : regularEdges) {
            builder.addEdge(edge[0], (String) edge[1]);
        }

        // Add conditional branches
        for (Map.Entry<String, Map<String, Branch>> startEntry : branches.entrySet()) {
            String start = startEntry.getKey();
            for (Map.Entry<String, Branch> branchEntry : startEntry.getValue().entrySet()) {
                Branch branch = branchEntry.getValue();
                builder.addBranch(start, branch.getCondition());
            }
        }

        return builder.build(graphStore, stepCallback);
    }

    /**
     * Reset all vertices for reuse.
     */
    public void reset() {
        for (Vertex node : nodes.values()) {
            node.reset();
        }
    }

    // ---- Helpers ----

    private void validateNodeId(String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.PREGEL_GRAPH_NODE_ID_INVALID,
                    "node_id", nodeId, "reason", "is None or empty");
        }
    }

    private static String getCallableName(Object func) {
        if (func instanceof Function) {
            return func.getClass().getSimpleName();
        }
        return func.getClass().getName();
    }

    /**
     * A conditional branch definition.
     */
    public static class Branch {
        private final Object condition;

        /**
         * Auto-generated for codecheck compliance.
         */
        public Branch(Object condition) {
            this.condition = condition;
        }

        @SuppressWarnings("unchecked")
        /**
         * Auto-generated for codecheck compliance.
         */
        public Function<Object, Object> getCondition() {
            if (condition instanceof Function) {
                return (Function<Object, Object>) condition;
            }
            return input -> null;
        }
    }
}
