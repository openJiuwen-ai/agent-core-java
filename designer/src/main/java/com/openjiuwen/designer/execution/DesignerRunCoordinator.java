/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.execution;

import com.openjiuwen.designer.event.DesignerEventSink;
import com.openjiuwen.designer.schema.DesignerExecutionRun;
import com.openjiuwen.designer.schema.DesignerGraph;
import com.openjiuwen.designer.schema.DesignerNodeState;
import com.openjiuwen.designer.schema.DesignerSchema;
import com.openjiuwen.designer.spi.DesignerWorkspacePort;
import com.openjiuwen.designer.store.DesignerGraphStore;
import com.openjiuwen.designer.workspace.DesignerConflictException;
import com.openjiuwen.designer.workspace.DesignerNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes triggers and owns the run lifecycle for each graph.
 *
 * @since 2026-11-06
 */
public final class DesignerRunCoordinator {

    private static final Logger LOG = LoggerFactory.getLogger(DesignerRunCoordinator.class);

    private static final int EXECUTOR_THREADS = 2;
    private static final int EXECUTOR_QUEUE = 16;

    private final DesignerGraphStore store;
    private final DesignerGraphRunner runner;
    private final DesignerEventSink eventSink;
    private final DesignerWorkspacePort workspace;
    private final NodeHandlerRegistry handlers;
    private final ExecutorService executor;
    private final Map<String, ReentrantLock> graphLocks = new ConcurrentHashMap<>();
    private final Map<String, DesignerRunContext> activeContexts = new ConcurrentHashMap<>();
    private final Map<String, String> activeGraphs = new ConcurrentHashMap<>();

    /**
     * Create a coordinator.
     *
     * @param store store
     * @param handlers node handlers
     * @param eventSink event sink
     * @param workspace workspace port
     */
    public DesignerRunCoordinator(DesignerGraphStore store, NodeHandlerRegistry handlers,
            DesignerEventSink eventSink, DesignerWorkspacePort workspace) {
        this.store = store;
        this.handlers = handlers;
        this.eventSink = eventSink;
        this.workspace = workspace;
        this.runner = new DesignerGraphRunner(store, handlers, eventSink, workspace);
        this.executor = new ThreadPoolExecutor(EXECUTOR_THREADS, EXECUTOR_THREADS, 0L,
                TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(EXECUTOR_QUEUE),
                runnable -> {
                    Thread thread = new Thread(runnable, "designer-run");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Start a run trigger.
     *
     * @param graphId graph id
     * @param nodeId optional target node
     * @param cascade cascade flag
     * @param source trigger source
     * @return started run and warnings
     */
    public DesignerRunStartResult start(String graphId, String nodeId, boolean cascade, String source) {
        LOG.info("Designer run start request: graph={}, node={}, cascade={}, source={}",
                graphId, nodeId, cascade, source);
        ReentrantLock lock = graphLocks.computeIfAbsent(graphId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            DesignerGraph graph = store.loadGraph(graphId)
                    .orElseThrow(() -> new DesignerNotFoundException("graph not found: " + graphId));
            if (activeGraphs.containsKey(graphId)) {
                throw new DesignerConflictException("a trigger is already executing on this graph");
            }
            String runId = ensureRunId(graph);
            DesignerExecutionRun run = loadOrCreate(graph, runId);
            healZombie(run);
            run = bumpVersions(graph, run, nodeId, cascade);
            Map<String, Object> runMetadata = withTriggerSource(run.metadata(), source);
            runMetadata.put("session_id", sessionId(graph));
            run = new DesignerExecutionRun(run.schemaVersion(), run.runId(), run.graphId(),
                    run.projectId(), "running", run.nodeStates(), List.of(), run.createdAt(),
                    System.currentTimeMillis(), runMetadata, null, null, List.of());
            run = store.saveRun(run);
            DesignerRunContext context = new DesignerRunContext(graph, run, runId,
                    sessionId(graph), handlers, eventSink, workspace, store);
            activeContexts.put(runId, context);
            activeGraphs.put(graphId, runId);
            eventSink.onRunUpdated(sessionId(graph), run);
            LOG.info("Designer run accepted: graph={}, run={}, nodes={}, triggerSource={}",
                    graphId, runId, run.nodeStates().size(), source);
            submit(graph, run, nodeId, cascade, context);
            return new DesignerRunStartResult(run, List.of());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Load a run.
     *
     * @param runId run id
     * @return run
     */
    public DesignerExecutionRun get(String runId) {
        return store.loadRun(runId)
                .orElseThrow(() -> new DesignerNotFoundException("run not found: " + runId));
    }

    /**
     * Cancel a run.
     *
     * @param runId run id
     * @return updated run
     */
    public DesignerExecutionRun cancel(String runId) {
        LOG.info("Designer run cancel request: run={}", runId);
        DesignerRunContext context = activeContexts.get(runId);
        if (context != null) {
            context.cancel();
        }
        DesignerExecutionRun run = get(runId);
        if (context == null && "running".equals(run.status())) {
            run = DesignerRunStateMapper.terminal(run, "cancelled", null,
                    System.currentTimeMillis());
            store.saveRun(run);
            eventSink.onRunUpdated(sessionOf(run), run);
        }
        return run;
    }

    /**
     * Whether a graph has a live trigger.
     *
     * @param graphId graph id
     * @return true when active
     */
    public boolean isActive(String graphId) {
        return activeGraphs.containsKey(graphId);
    }

    /**
     * Resolve the run paired with a graph.
     *
     * @param graph graph
     * @return run when present
     */
    public Optional<DesignerExecutionRun> runForGraph(DesignerGraph graph) {
        Object value = graph.metadata().get("team_design_run_id");
        if (value == null || String.valueOf(value).isBlank()) {
            return Optional.empty();
        }
        return store.loadRun(String.valueOf(value));
    }

    private void submit(DesignerGraph graph, DesignerExecutionRun run, String nodeId,
            boolean cascade, DesignerRunContext context) {
        executor.execute(() -> {
            try {
                DesignerExecutionRun terminal = runner.run(graph, run, nodeId, cascade, context);
                LOG.info("Designer run finished: graph={}, run={}, status={}",
                        graph.graphId(), run.runId(), terminal.status());
            } catch (RuntimeException ex) {
                LOG.error("Designer run failed: graph={}, run={}, error={}",
                        graph.graphId(), run.runId(), ex.getMessage());
                DesignerExecutionRun failed = DesignerRunStateMapper.terminal(context.run(),
                        "failed", message(ex), System.currentTimeMillis());
                context.updateRun(failed);
                context.persistRun();
            } finally {
                activeContexts.remove(run.runId());
                activeGraphs.remove(graph.graphId());
            }
        });
    }

    private void healZombie(DesignerExecutionRun run) {
        if (!"running".equals(run.status()) || activeContexts.containsKey(run.runId())) {
            return;
        }
        DesignerExecutionRun healed = DesignerRunStateMapper.terminal(run, "failed",
                "interrupted by process restart (zombie self-heal)", System.currentTimeMillis());
        LOG.warn("Designer run zombie healed: run={}, graph={}", run.runId(), run.graphId());
        store.saveRun(healed);
        eventSink.onRunUpdated(sessionOf(healed), healed);
    }

    private DesignerExecutionRun bumpVersions(DesignerGraph graph, DesignerExecutionRun run,
            String nodeId, boolean cascade) {
        Set<String> bumpIds = executingIds(graph, nodeId, cascade);
        Map<String, DesignerNodeState> states = new LinkedHashMap<>(run.nodeStates());
        for (String id : bumpIds) {
            DesignerNodeState state = states.getOrDefault(id, new DesignerNodeState("pending",
                    null, null, null, List.of(), null, List.of(), null, List.of(), List.of(),
                    null, List.of(), 1));
            states.put(id, new DesignerNodeState(state.status(), state.startedAt(),
                    state.completedAt(), state.outputRef(), state.outputRefs(), state.error(),
                    state.blockedBy(), state.activity(), state.activityTail(), state.activityLog(),
                    state.text(), state.history(), state.version() + 1));
        }
        return new DesignerExecutionRun(run.schemaVersion(), run.runId(), run.graphId(),
                run.projectId(), run.status(), states, run.currentNodeIds(), run.createdAt(),
                run.updatedAt(), run.metadata(), run.error(), run.warning(), run.warnings());
    }

    private String ensureRunId(DesignerGraph graph) {
        Object value = graph.metadata().get("team_design_run_id");
        if (value != null && !String.valueOf(value).isBlank()) {
            return String.valueOf(value);
        }
        String runId = "run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, Object> metadata = new LinkedHashMap<>(graph.metadata());
        metadata.put("team_design_run_id", runId);
        store.saveGraph(graph.withMetadata(metadata).withUpdatedAt(System.currentTimeMillis()));
        return runId;
    }

    private DesignerExecutionRun loadOrCreate(DesignerGraph graph, String runId) {
        Optional<DesignerExecutionRun> existing = store.loadRun(runId);
        if (existing.isPresent()) {
            return existing.get();
        }
        long now = System.currentTimeMillis();
        return new DesignerExecutionRun(DesignerSchema.RUN_SCHEMA_VERSION, runId,
                graph.graphId(), graph.projectId(), "draft", Map.of(), List.of(), now, now,
                Map.of(), null, null, List.of());
    }

    private Set<String> executingIds(DesignerGraph graph, String nodeId, boolean cascade) {
        if (nodeId == null || nodeId.isBlank()) {
            Set<String> all = new LinkedHashSet<>();
            graph.nodes().forEach(node -> all.add(node.id()));
            return all;
        }
        Set<String> ids = new LinkedHashSet<>();
        ids.add(nodeId);
        if (cascade) {
            ids.addAll(descendants(graph, nodeId));
        }
        return ids;
    }

    private static Set<String> descendants(DesignerGraph graph, String nodeId) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> frontier = new ArrayDeque<>();
        frontier.add(nodeId);
        while (!frontier.isEmpty()) {
            String current = frontier.removeFirst();
            for (var edge : graph.edges()) {
                if (edge.source().equals(current) && seen.add(edge.target())) {
                    frontier.addLast(edge.target());
                }
            }
        }
        seen.remove(nodeId);
        return seen;
    }

    private static Map<String, Object> withTriggerSource(Map<String, Object> metadata,
            String source) {
        Map<String, Object> next = new LinkedHashMap<>(metadata);
        next.put("trigger_source", source == null || source.isBlank() ? "canvas" : source);
        return next;
    }

    private static String sessionId(DesignerGraph graph) {
        Object value = graph.metadata().get("session_id");
        return value == null ? "" : String.valueOf(value);
    }

    private static String sessionOf(DesignerExecutionRun run) {
        return String.valueOf(run.metadata().getOrDefault("session_id", ""));
    }

    private static String message(RuntimeException ex) {
        String value = ex.getMessage();
        return value == null || value.isBlank() ? ex.getClass().getSimpleName()
                : value.substring(0, Math.min(value.length(), DesignerSchema.NODE_ERROR_LIMIT));
    }

    /**
     * Result of starting a run.
     *
     * @param run started run
     * @param warnings non-fatal warnings
     * @since 2026-11-06
     */
    public record DesignerRunStartResult(DesignerExecutionRun run, List<String> warnings) {

        public DesignerRunStartResult {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }
}
