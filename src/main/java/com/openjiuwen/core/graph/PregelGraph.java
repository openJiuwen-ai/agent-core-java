/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.graph.pregel.Pregel;
import com.openjiuwen.core.graph.pregel.PregelBuilder;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.graph.pregel.PregelLoop;
import com.openjiuwen.core.graph.store.GraphStore;
import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.session.BaseSession;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * Mirrors Python's {@code PregelGraph} in
 * {@code openjiuwen/core/graph/graph.py}.
 */
public class PregelGraph extends Graph {

    private Pregel pregel;
    private final List<Edge> edges = new ArrayList<>();
    private final Set<String> waits = new LinkedHashSet<>();
    private final Map<String, Vertex> nodes = new LinkedHashMap<>();
    private final Map<String, Map<String, Branch>> branches = new LinkedHashMap<>();
    private final Map<String, Set<String>> branchTargets = new LinkedHashMap<>();
    private CompiledGraph.GraphCheckpointer checkpointer;
    private BaseSession session;

    @Override
    public PregelGraph startNode(String nodeId) {
        validateNodeId(nodeId);
        addEdge(List.of(PregelConstants.START), nodeId);
        return this;
    }

    @Override
    public PregelGraph endNode(String nodeId) {
        validateNodeId(nodeId);
        Vertex vertex = nodes.get(nodeId);
        if (vertex != null) {
            vertex.setEndNode(true);
        }
        addEdge(List.of(nodeId), PregelConstants.END);
        return this;
    }

    @Override
    public PregelGraph addNode(String nodeId, Executable<?, ?> node, boolean waitForAll) {
        validateNodeId(nodeId);
        if (node == null) {
            throw ErrorHelper.buildError(StatusCode.PREGEL_GRAPH_NODE_INVALID,
                    "node_id", nodeId, "reason", "node is None");
        }
        if (nodes.containsKey(nodeId)) {
            throw ErrorHelper.buildError(StatusCode.PREGEL_GRAPH_NODE_ID_INVALID,
                    "node_id", nodeId, "reason", "already exist, can not add again");
        }
        nodes.put(nodeId, new Vertex(nodeId, castExecutable(node)));
        if (waitForAll) {
            waits.add(nodeId);
        }
        return this;
    }

    @Override
    public Map<String, Executable<?, ?>> getNodes() {
        Map<String, Executable<?, ?>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Vertex> entry : nodes.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getExecutable());
        }
        return result;
    }

    public Vertex getVertex(String nodeId) {
        return nodes.get(nodeId);
    }

    public List<Edge> getEdges() {
        return List.copyOf(edges);
    }

    public Set<String> getWaits() {
        return Set.copyOf(waits);
    }

    public Map<String, Set<String>> getBranchTargets() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : branchTargets.entrySet()) {
            result.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return result;
    }

    @Override
    public PregelGraph addEdge(Object sourceNodeId, String targetNodeId) {
        validateEdge(sourceNodeId, targetNodeId);
        edges.add(new Edge(sourceNodeId, targetNodeId));
        return this;
    }

    @Override
    public PregelGraph addConditionalEdges(String sourceNodeId, Object router) {
        if (sourceNodeId == null || sourceNodeId.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.PREGEL_GRAPH_CONDITION_EDGE_INVALID,
                    "source_id", sourceNodeId, "reason", "source_node_id is None or empty");
        }
        if (router == null) {
            throw ErrorHelper.buildError(StatusCode.PREGEL_GRAPH_CONDITION_EDGE_INVALID,
                    "source_id", sourceNodeId, "reason", "router is None");
        }
        String name = getCallableName(router);
        branches.computeIfAbsent(sourceNodeId, ignored -> new LinkedHashMap<>())
                .put(name, new Branch(router));
        return this;
    }

    public PregelGraph registerBranchTargets(String branchNodeId, Set<String> targets) {
        if (branchNodeId != null && !branchNodeId.isEmpty() && targets != null && targets.size() > 1) {
            branchTargets.put(branchNodeId, new LinkedHashSet<>(targets));
        }
        return this;
    }

    @Override
    public ExecutableGraph<?, ?> compile(BaseSession session, Map<String, Object> kwargs) {
        Map<String, Object> options = kwargs == null ? Collections.emptyMap() : new LinkedHashMap<>(kwargs);
        if (session instanceof Vertex.VertexSession vertexSession) {
            for (Vertex node : nodes.values()) {
                node.init(vertexSession, options);
            }
        }

        Consumer<PregelLoop> afterStep = loop -> {
            if (this.session instanceof CompiledGraph.GraphRuntimeSession runtimeSession) {
                CompiledGraph.WorkflowState state = runtimeSession.workflowState();
                if (state != null) {
                    state.commit();
                }
            }
            Loggers.GRAPH.debug("Finished to run graph super-step [{}]", loop.getStep());
        };

        if (pregel == null) {
            Store graphStore = null;
            if (session instanceof CompiledGraph.GraphRuntimeSession runtimeSession) {
                checkpointer = runtimeSession.checkpointer();
                if (checkpointer != null && checkpointer.graphStore() != null) {
                    graphStore = new GraphStore(checkpointer.graphStore());
                }
            }
            pregel = compilePregel(graphStore, afterStep);
        }
        this.session = session;
        return new CompiledGraph(pregel, checkpointer);
    }

    public Pregel compilePregel(Store graphStore, Consumer<PregelLoop> stepCallback) {
        List<Edge> regularEdges = new ArrayList<>();
        Map<String, List<Set<String>>> sources = new LinkedHashMap<>();
        PregelBuilder builder = new PregelBuilder();
        for (Map.Entry<String, Vertex> entry : nodes.entrySet()) {
            Vertex vertex = entry.getValue();
            builder.addNode(entry.getKey(), invocation -> invokeVertex(vertex, invocation));
        }

        for (Edge edge : edges) {
            Object sourceNodeId = edge.sourceNodeId();
            String targetNodeId = edge.targetNodeId();
            if (waits.contains(targetNodeId)) {
                List<Set<String>> groups = sources.computeIfAbsent(targetNodeId, ignored -> new ArrayList<>());
                if (sourceNodeId instanceof String source) {
                    groups.add(Set.of(source));
                } else if (sourceNodeId instanceof Collection<?> collection) {
                    for (Object item : collection) {
                        groups.add(Set.of(String.valueOf(item)));
                    }
                }
            } else {
                regularEdges.add(edge);
            }
        }

        for (Map.Entry<String, List<Set<String>>> entry : sources.entrySet()) {
            List<Set<String>> groups = resolveBarrierGroups(entry.getKey(), entry.getValue());
            builder.addEdge(toBarrierStart(groups), entry.getKey());
        }
        for (Edge edge : regularEdges) {
            addBuilderEdge(builder, edge.sourceNodeId(), edge.targetNodeId());
        }
        for (Map.Entry<String, Map<String, Branch>> startEntry : branches.entrySet()) {
            for (Branch branch : startEntry.getValue().values()) {
                builder.addBranch(startEntry.getKey(), branch.asSupplier());
            }
        }
        return builder.build(graphStore, stepCallback);
    }

    public void reset() {
        for (Vertex node : nodes.values()) {
            node.reset();
        }
    }

    static String getCallableName(Object func) {
        if (func == null) {
            return "None";
        }
        String reflectedName = reflectName(func);
        if (reflectedName != null && !reflectedName.isEmpty()) {
            return reflectedName;
        }
        String simpleName = func.getClass().getSimpleName();
        return simpleName == null || simpleName.isEmpty() ? func.getClass().getName() : simpleName;
    }

    private Set<String> forwardReachable(String startNode) {
        Set<String> visited = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(startNode);
        while (!queue.isEmpty()) {
            String node = queue.remove();
            if (!visited.add(node)) {
                continue;
            }
            for (Edge edge : edges) {
                if (edge.sourceNodeId() instanceof String source
                        && source.equals(node)
                        && !visited.contains(edge.targetNodeId())) {
                    queue.add(edge.targetNodeId());
                }
            }
        }
        return visited;
    }

    public List<Set<String>> resolveBarrierGroups(String targetId, List<Set<String>> sourceList) {
        if (branchTargets.isEmpty() || sourceList == null || sourceList.isEmpty()) {
            return sourceList;
        }

        Set<String> allPredecessors = new LinkedHashSet<>();
        for (Set<String> group : sourceList) {
            allPredecessors.addAll(group);
        }

        Map<BranchTarget, Set<String>> reachable = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : branchTargets.entrySet()) {
            for (String target : entry.getValue()) {
                reachable.put(new BranchTarget(entry.getKey(), target), forwardReachable(target));
            }
        }

        Map<String, Set<BranchTarget>> predecessorInfo = new LinkedHashMap<>();
        for (String predecessor : allPredecessors) {
            Set<BranchTarget> owners = new LinkedHashSet<>();
            for (Map.Entry<BranchTarget, Set<String>> entry : reachable.entrySet()) {
                if (entry.getValue().contains(predecessor)) {
                    owners.add(entry.getKey());
                }
            }
            predecessorInfo.put(predecessor, owners);
        }

        Map<String, Set<String>> branchGroups = new LinkedHashMap<>();
        List<Set<String>> standalone = new ArrayList<>();
        for (String predecessor : allPredecessors) {
            Set<BranchTarget> owners = predecessorInfo.get(predecessor);
            if (owners.size() == 1) {
                String branchId = owners.iterator().next().branchId();
                branchGroups.computeIfAbsent(branchId, ignored -> new LinkedHashSet<>()).add(predecessor);
            } else {
                standalone.add(Set.of(predecessor));
            }
        }

        List<Set<String>> result = new ArrayList<>(branchGroups.values());
        result.addAll(standalone);
        return result.isEmpty() ? sourceList : result;
    }

    private static Object invokeVertex(Vertex vertex, Object invocation) {
        Map<String, Object> invocationMap = CompiledGraph.copyConfigMap(invocation);
        GraphState state = invocationMap.get("state") instanceof GraphState graphState ? graphState : new GraphState();
        Map<String, Object> config = CompiledGraph.copyConfigMap(invocationMap.get("config"));
        try {
            return vertex.invoke(state, config).toCompletableFuture().join();
        } catch (CompletionException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw ex;
        }
    }

    private static void addBuilderEdge(PregelBuilder builder, Object sourceNodeId, String targetNodeId) {
        if (sourceNodeId instanceof String source) {
            builder.addEdge(source, targetNodeId);
            return;
        }
        if (sourceNodeId instanceof Collection<?> collection) {
            builder.addEdge(collection, targetNodeId);
            return;
        }
        throw new IllegalArgumentException("Unsupported edge source type: " + sourceNodeId.getClass().getName());
    }

    private static List<Object> toBarrierStart(List<Set<String>> groups) {
        List<Object> start = new ArrayList<>(groups.size());
        for (Set<String> group : groups) {
            if (group.size() == 1) {
                start.add(group.iterator().next());
            } else {
                start.add(new LinkedHashSet<>(group));
            }
        }
        return start;
    }

    @SuppressWarnings("unchecked")
    private static Executable<Map<String, Object>, Map<String, Object>> castExecutable(Executable<?, ?> executable) {
        return (Executable<Map<String, Object>, Map<String, Object>>) executable;
    }

    private static void validateNodeId(String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.PREGEL_GRAPH_NODE_ID_INVALID,
                    "node_id", nodeId, "reason", "is None or empty");
        }
    }

    private static void validateEdge(Object sourceNodeId, String targetNodeId) {
        if (sourceNodeId == null
                || sourceNodeId instanceof String source && source.isEmpty()
                || sourceNodeId instanceof Collection<?> collection && collection.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.PREGEL_GRAPH_EDGE_INVALID,
                    "source_id", String.valueOf(sourceNodeId), "target_node_id", targetNodeId,
                    "reason", "source_node_id is None or empty");
        }
        if (sourceNodeId instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item == null || item instanceof String text && text.isEmpty()) {
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
    }

    private static String reflectName(Object func) {
        try {
            Method method = func.getClass().getMethod("__name__");
            Object value = method.invoke(func);
            return value == null ? null : String.valueOf(value);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    /**
     * Mirrors Python's edge tuple entries in
     * {@code openjiuwen/core/graph/graph.py}.
     */
    public record Edge(Object sourceNodeId, String targetNodeId) {
    }

    /**
     * Mirrors Python's conditional branch wrapper in
     * {@code openjiuwen/core/graph/graph.py}.
     *
     * <p>This nested type preserves the 0.1.12 public API after the branch
     * implementation moved to {@link com.openjiuwen.core.graph.Branch}.</p>
     */
    public static class Branch extends com.openjiuwen.core.graph.Branch {

        public Branch(Object condition) {
            super(condition);
        }
    }

    /**
     * Mirrors Python's internal {@code (branch_id, target)} reachability key in
     * {@code openjiuwen/core/graph/graph.py}.
     */
    private record BranchTarget(String branchId, String target) {
    }
}
