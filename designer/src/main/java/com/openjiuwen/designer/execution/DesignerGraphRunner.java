/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.execution;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.ExecutableGraph;
import com.openjiuwen.core.graph.PregelGraph;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeSession;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeState;
import com.openjiuwen.designer.event.DesignerEventSink;
import com.openjiuwen.designer.schema.DesignerAssetRef;
import com.openjiuwen.designer.schema.DesignerEdge;
import com.openjiuwen.designer.schema.DesignerExecutionRun;
import com.openjiuwen.designer.schema.DesignerGraph;
import com.openjiuwen.designer.schema.DesignerNode;
import com.openjiuwen.designer.schema.DesignerNodeState;
import com.openjiuwen.designer.spi.DesignerWorkspacePort;
import com.openjiuwen.designer.store.DesignerGraphStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executes a designer graph on the core Pregel engine.
 *
 * @since 2026-11-06
 */
public final class DesignerGraphRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DesignerGraphRunner.class);

    private static final int MAX_SCOPE_NODES = 500;

    private final DesignerGraphStore store;
    private final NodeHandlerRegistry handlers;
    private final DesignerEventSink eventSink;
    private final DesignerWorkspacePort workspace;

    /**
     * Create a graph runner.
     *
     * @param store run store
     * @param handlers node handlers
     * @param eventSink event sink
     * @param workspace workspace port
     */
    public DesignerGraphRunner(DesignerGraphStore store, NodeHandlerRegistry handlers,
            DesignerEventSink eventSink, DesignerWorkspacePort workspace) {
        this.store = store;
        this.handlers = handlers;
        this.eventSink = eventSink;
        this.workspace = workspace;
    }

    /**
     * Execute one run scope.
     *
     * @param graph graph
     * @param run running run document
     * @param nodeId optional single/cascade target
     * @param cascade whether cascade scope is requested
     * @return terminal run document
     */
    public DesignerExecutionRun run(DesignerGraph graph, DesignerExecutionRun run, String nodeId,
            boolean cascade) {
        DesignerRunContext context = new DesignerRunContext(graph, run, run.runId(),
                sessionId(graph), handlers, eventSink, workspace, store);
        return run(graph, run, nodeId, cascade, context);
    }

    /**
     * Execute one run scope with a caller-owned context.
     *
     * @param graph graph
     * @param run running run document
     * @param nodeId optional single/cascade target
     * @param cascade whether cascade scope is requested
     * @param context caller-owned run context
     * @return terminal run document
     */
    public DesignerExecutionRun run(DesignerGraph graph, DesignerExecutionRun run, String nodeId,
            boolean cascade, DesignerRunContext context) {
        RunScope scope = resolveScope(graph, run, nodeId, cascade);
        LOG.info("Designer graph run start: graph={}, run={}, target={}, cascade={}, "
                        + "scope={}, forced={}, frozen={}",
                graph.graphId(), run.runId(), nodeId, cascade, scope.scope().size(),
                scope.forced().size(), scope.frozen().size());
        seedFrozen(context, graph, run);
        PregelGraph pregel = compile(graph, scope, context);
        BaseSession session = new WorkflowRuntimeSession(run.runId(), null, run.runId(),
                WorkflowRuntimeState.create(), null);
        ExecutableGraph<?, ?> compiled = pregel.compile(session, Map.of());
        compiled.invoke(envelope(), session);
        DesignerExecutionRun terminal = terminalRun(context, scope, graph);
        LOG.info("Designer graph run complete: graph={}, run={}, status={}, outcomes={}",
                graph.graphId(), run.runId(), terminal.status(), context.outcomes().size());
        context.updateRun(terminal);
        context.persistRun();
        persistOutputs(graph, context);
        return terminal;
    }

    private void persistOutputs(DesignerGraph graph, DesignerRunContext context) {
        List<DesignerNode> nodes = new ArrayList<>();
        boolean changed = false;
        for (DesignerNode node : graph.nodes()) {
            NodeOutcome outcome = context.outcome(node.id()).orElse(null);
            DesignerAssetRef ref = outcome == null ? null : outcome.outputRef();
            boolean userReplaced = Boolean.TRUE.equals(node.config().get("user_replaced_output"));
            if (ref != null && !userReplaced && !ref.equals(node.outputRef())) {
                nodes.add(new DesignerNode(node.id(), node.type(), node.label(), node.config(),
                        node.layout(), ref));
                changed = true;
            } else {
                nodes.add(node);
            }
        }
        if (changed) {
            DesignerGraph updated = store.saveGraph(graph.withNodes(nodes)
                    .withUpdatedAt(System.currentTimeMillis()));
            LOG.info("Designer graph outputs persisted: graph={}, nodesWithOutput={}",
                    graph.graphId(), context.outcomes().size());
            eventSink.onGraphUpdated(sessionId(graph), updated);
        }
    }

    private PregelGraph compile(DesignerGraph graph, RunScope scope, DesignerRunContext context) {
        PregelGraph pregel = new PregelGraph();
        Set<String> scopeIds = scope.scope();
        for (DesignerNode node : graph.nodes()) {
            if (!scopeIds.contains(node.id())) {
                continue;
            }
            boolean frozen = scope.frozen().contains(node.id());
            Executable<Map<String, Object>, Object> executable = frozen
                    ? new FrozenExecutable(node.id(), context)
                    : new DesignerNodeExecutable(node, context, false);
            pregel.addNode(node.id(), executable);
        }
        Set<String> hasIncoming = new HashSet<>();
        Set<String> hasOutgoing = new HashSet<>();
        for (DesignerEdge edge : graph.edges()) {
            if (scopeIds.contains(edge.source()) && scopeIds.contains(edge.target())) {
                pregel.addEdge(edge.source(), edge.target());
                hasIncoming.add(edge.target());
                hasOutgoing.add(edge.source());
            }
        }
        for (String id : scopeIds) {
            if (!hasIncoming.contains(id)) {
                pregel.startNode(id);
            }
            if (!hasOutgoing.contains(id)) {
                pregel.endNode(id);
            }
        }
        return pregel;
    }

    private void seedFrozen(DesignerRunContext context, DesignerGraph graph, DesignerExecutionRun run) {
        for (DesignerNode node : graph.nodes()) {
            DesignerNodeState state = run.nodeStates().get(node.id());
            if (state != null && "completed".equals(state.status()) && isProduced(state)) {
                context.record(node.id(), new NodeOutcome("completed", state.text(),
                        state.outputRef(), state.outputRefs(), null, List.of()));
            }        }
    }

    private DesignerExecutionRun terminalRun(DesignerRunContext context, RunScope scope,
            DesignerGraph graph) {
        boolean failed = false;
        String error = null;
        for (String id : scope.scope()) {
            NodeOutcome outcome = context.outcome(id).orElse(null);
            if (outcome == null) {
                failed = true;
                error = error == null ? "node " + id + " did not execute" : error;
            } else if (!outcome.isCompleted()) {
                failed = true;
                if (error == null) {
                    error = outcome.error();
                }
            }
        }
        if (context.isCancelled()) {
            return DesignerRunStateMapper.terminal(context.run(), "cancelled", null,
                    System.currentTimeMillis());
        }
        return DesignerRunStateMapper.terminal(context.run(), failed ? "failed" : "completed",
                error, System.currentTimeMillis());
    }

    private RunScope resolveScope(DesignerGraph graph, DesignerExecutionRun run, String nodeId,
            boolean cascade) {
        Map<String, DesignerNode> nodes = new LinkedHashMap<>();
        for (DesignerNode node : graph.nodes()) {
            nodes.put(node.id(), node);
        }
        List<String> order = new ArrayList<>(nodes.keySet());
        if (nodeId == null || nodeId.isBlank()) {
            Set<String> scope = new LinkedHashSet<>(order);
            return new RunScope(scope, Set.of(), frozen(scope, run), order);
        }
        if (!nodes.containsKey(nodeId)) {
            throw new DesignerNodeException("node not found: " + nodeId);
        }
        Set<String> forced = new LinkedHashSet<>();
        forced.add(nodeId);
        if (cascade) {
            forced.addAll(descendants(graph, nodeId));
        }
        Set<String> scope = new LinkedHashSet<>(forced);
        scope.addAll(ancestors(graph, nodeId));
        List<String> scopedOrder = new ArrayList<>();
        for (String id : order) {
            if (scope.contains(id)) {
                scopedOrder.add(id);
            }
        }
        Set<String> frozen = new LinkedHashSet<>();
        List<String> notReady = new ArrayList<>();
        for (String id : scope) {
            if (forced.contains(id)) {
                continue;
            }
            if (frozen(id, run)) {
                frozen.add(id);
            } else {
                notReady.add(id);
            }
        }
        if (!notReady.isEmpty()) {
            throw new DesignerNodeException("upstream not ready: " + String.join(", ", notReady));
        }
        if (scope.size() > MAX_SCOPE_NODES) {
            throw new DesignerNodeException("designer run scope exceeds " + MAX_SCOPE_NODES + " nodes");
        }
        return new RunScope(scope, forced, frozen, scopedOrder);
    }

    private Set<String> frozen(Set<String> scope, DesignerExecutionRun run) {
        Set<String> frozen = new LinkedHashSet<>();
        for (String id : scope) {
            if (frozen(id, run)) {
                frozen.add(id);
            }
        }
        return frozen;
    }

    private boolean frozen(String nodeId, DesignerExecutionRun run) {
        DesignerNodeState state = run.nodeStates().get(nodeId);
        return state != null && "completed".equals(state.status()) && isProduced(state);
    }

    private static boolean isProduced(DesignerNodeState state) {
        boolean hasText = state.text() != null && !state.text().isBlank();
        return hasText || state.outputRef() != null;
    }

    private static Set<String> descendants(DesignerGraph graph, String nodeId) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> frontier = new ArrayDeque<>();
        frontier.add(nodeId);
        while (!frontier.isEmpty()) {
            String current = frontier.removeFirst();
            for (DesignerEdge edge : graph.edges()) {
                if (edge.source().equals(current) && seen.add(edge.target())) {
                    frontier.addLast(edge.target());
                }
            }
        }
        seen.remove(nodeId);
        return seen;
    }

    private static Set<String> ancestors(DesignerGraph graph, String nodeId) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> frontier = new ArrayDeque<>();
        frontier.add(nodeId);
        while (!frontier.isEmpty()) {
            String current = frontier.removeFirst();
            for (DesignerEdge edge : graph.edges()) {
                if (edge.target().equals(current) && seen.add(edge.source())) {
                    frontier.addLast(edge.source());
                }
            }
        }
        seen.remove(nodeId);
        return seen;
    }

    private static String sessionId(DesignerGraph graph) {
        Object value = graph.metadata().get("session_id");
        return value == null ? "" : String.valueOf(value);
    }

    private static Map<String, Object> envelope() {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put(Constant.INPUTS_KEY, Map.of());
        envelope.put(Constant.CONFIG_KEY, Map.of());
        return envelope;
    }

    private record RunScope(Set<String> scope, Set<String> forced, Set<String> frozen,
            List<String> order) {
    }

    private static final class FrozenExecutable extends Executable<Map<String, Object>, Object> {
        private final String nodeId;
        private final DesignerRunContext context;

        private FrozenExecutable(String nodeId, DesignerRunContext context) {
            this.nodeId = nodeId;
            this.context = context;
        }

        @Override
        public Object onInvoke(Map<String, Object> inputs, BaseSession session, Object... kwargs) {
            return context.outcome(nodeId).orElse(null);
        }
    }
}
