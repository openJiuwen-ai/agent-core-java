/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.execution;

import com.openjiuwen.designer.event.DesignerEventSink;
import com.openjiuwen.designer.schema.DesignerExecutionRun;
import com.openjiuwen.designer.schema.DesignerGraph;
import com.openjiuwen.designer.schema.DesignerNode;
import com.openjiuwen.designer.spi.DesignerWorkspacePort;
import com.openjiuwen.designer.store.DesignerGraphStore;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Per-run blackboard shared by designer node handlers.
 *
 * <p>Pregel supplies ordering and fan-out. Node payloads travel through this
 * context because raw Pregel nodes receive an empty {@code inputs} map.</p>
 *
 * @since 2026-11-06
 */
public final class DesignerRunContext {

    private final DesignerGraph graph;
    private final String runId;
    private final String sessionId;
    private final NodeHandlerRegistry handlers;
    private final DesignerEventSink eventSink;
    private final DesignerWorkspacePort workspace;
    private final DesignerGraphStore store;
    private final Map<String, NodeOutcome> outcomes = new ConcurrentHashMap<>();
    private final AtomicReference<DesignerExecutionRun> run;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    /**
     * Create a run context.
     *
     * @param graph graph being executed
     * @param run initial run document
     * @param runId run id
     * @param sessionId owning session
     * @param handlers handler registry
     * @param eventSink event sink
     * @param workspace workspace port
     * @param store run store
     */
    public DesignerRunContext(DesignerGraph graph, DesignerExecutionRun run, String runId,
            String sessionId, NodeHandlerRegistry handlers, DesignerEventSink eventSink,
            DesignerWorkspacePort workspace, DesignerGraphStore store) {
        this.graph = graph;
        this.run = new AtomicReference<>(run);
        this.runId = runId;
        this.sessionId = sessionId;
        this.handlers = handlers;
        this.eventSink = eventSink;
        this.workspace = workspace;
        this.store = store;
    }

    /**
     * Return the graph.
     *
     * @return graph
     */
    public DesignerGraph graph() {
        return graph;
    }

    /**
     * Return the run id.
     *
     * @return run id
     */
    public String runId() {
        return runId;
    }

    /**
     * Return the owning session id.
     *
     * @return session id
     */
    public String sessionId() {
        return sessionId;
    }

    /**
     * Return the current run snapshot.
     *
     * @return run snapshot
     */
    public DesignerExecutionRun run() {
        return run.get();
    }

    /**
     * Replace the current run snapshot.
     *
     * @param next next snapshot
     */
    public void updateRun(DesignerExecutionRun next) {
        run.set(next);
    }

    /**
     * Atomically mark one node running and publish the snapshot.
     *
     * @param nodeId node id
     */
    public synchronized void markStarted(String nodeId) {
        run.set(DesignerRunStateMapper.started(run.get(), nodeId, System.currentTimeMillis()));
        persistRun();
    }

    /**
     * Atomically apply one node outcome and publish the snapshot.
     *
     * @param nodeId node id
     * @param outcome node outcome
     */
    public synchronized void markFinished(String nodeId, NodeOutcome outcome) {
        run.set(DesignerRunStateMapper.finished(run.get(), nodeId, outcome,
                System.currentTimeMillis()));
        persistNode(nodeId);
    }

    /**
     * Persist and publish the current run snapshot.
     */
    public void persistRun() {
        DesignerExecutionRun snapshot = store.saveRun(run.get());
        run.set(snapshot);
        eventSink.onRunUpdated(sessionId, snapshot);
    }

    /**
     * Persist and publish the current run snapshot for one node.
     *
     * @param nodeId updated node
     */
    public void persistNode(String nodeId) {
        DesignerExecutionRun snapshot = store.saveRun(run.get());
        run.set(snapshot);
        eventSink.onNodeUpdated(sessionId, snapshot, nodeId);
    }

    /**
     * Return the handler registry.
     *
     * @return handler registry
     */
    public NodeHandlerRegistry handlers() {
        return handlers;
    }

    /**
     * Return the event sink.
     *
     * @return event sink
     */
    public DesignerEventSink eventSink() {
        return eventSink;
    }

    /**
     * Resolve the output directory.
     *
     * @return output directory
     */
    public Path outputDir() {
        return workspace.outputDir(graph);
    }

    /**
     * Resolve the media directory.
     *
     * @return media directory
     */
    public Path mediaDir() {
        return workspace.mediaDir(graph);
    }

    /**
     * Record a node outcome.
     *
     * @param nodeId node id
     * @param outcome outcome
     */
    public void record(String nodeId, NodeOutcome outcome) {
        outcomes.put(nodeId, outcome);
    }

    /**
     * Read a node outcome.
     *
     * @param nodeId node id
     * @return outcome when present
     */
    public Optional<NodeOutcome> outcome(String nodeId) {
        return Optional.ofNullable(outcomes.get(nodeId));
    }

    /**
     * Return all outcomes.
     *
     * @return snapshot of outcomes
     */
    public Map<String, NodeOutcome> outcomes() {
        return new LinkedHashMap<>(outcomes);
    }

    /**
     * Resolve one node by id.
     *
     * @param nodeId node id
     * @return node when present
     */
    public Optional<DesignerNode> node(String nodeId) {
        return graph.nodes().stream().filter(node -> node.id().equals(nodeId)).findFirst();
    }

    /**
     * Request cooperative cancellation.
     */
    public void cancel() {
        cancelled.set(true);
    }

    /**
     * Whether cancellation was requested.
     *
     * @return true when cancelled
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Throw when cancellation was requested.
     */
    public void checkCancelled() {
        if (cancelled.get()) {
            throw new DesignerNodeException("designer run cancelled");
        }
    }
}
