/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.graph.pregel.Pregel;
import com.openjiuwen.core.graph.pregel.PregelConfig;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.interaction.InteractiveInput;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code CompiledGraph} in
 * {@code openjiuwen/core/graph/graph.py}.
 */
public class CompiledGraph extends ExecutableGraph<Object, Map<String, Object>> {

    private final Pregel pregel;
    private final GraphCheckpointer checkpointer;
    private final PregelGraph pregelGraph;
    private final BaseSession compileSession;
    private final Map<String, Object> compileOptions;

    public CompiledGraph(Pregel pregel, GraphCheckpointer checkpointer) {
        this(pregel, checkpointer, null, null, null);
    }

    /**
     * Creates a CompiledGraph with per-run session context.
     *
     * @param pregel         the compiled Pregel engine (one per compile call)
     * @param checkpointer   checkpointer derived from the compile-argument session
     * @param pregelGraph    the owning PregelGraph (for per-run vertex init)
     * @param compileSession the session passed to {@code compile()} (per-run)
     * @param compileOptions the options map passed to {@code compile()}
     */
    public CompiledGraph(Pregel pregel, GraphCheckpointer checkpointer,
                         PregelGraph pregelGraph, BaseSession compileSession,
                         Map<String, Object> compileOptions) {
        this.pregel = pregel;
        this.checkpointer = checkpointer;
        this.pregelGraph = pregelGraph;
        this.compileSession = compileSession;
        this.compileOptions = compileOptions;
    }

    @Override
    protected Map<String, Object> invokeInternal(Object inputs, BaseSession session, Object config) {
        boolean isMain = config == null;
        PregelConfig pregelConfig = normalizeConfig(config, session);

        // Vertices are shared graph-level instances, so overlapping runs on
        // compilations of the same PregelGraph would race over vertex
        // sessions. Refuse the second run instead of silently corrupting the
        // first one; serial reuse (one invoke at a time) is unaffected.
        if (pregelGraph != null && !pregelGraph.tryBeginRun(describeSession(session))) {
            throw ErrorHelper.buildError(StatusCode.PREGEL_GRAPH_CONCURRENT_RUN_INVALID,
                    "workflow_id", describeSession(session),
                    "reason", "another invoke is still in progress on the same graph");
        }
        Exception exception = null;
        Map<String, Object> result = null;

        try {
            // Per-run vertex init: bind every vertex to the session of this
            // invocation so nodes read/write the correct workflow state.
            // Previously compile() did this once and stored the session on the
            // shared PregelGraph, which a second compile would overwrite.
            initVerticesForRun(session);

            if (isMain && checkpointer != null) {
                checkpointer.preWorkflowExecute(session, inputs);
            }
            if (!(inputs instanceof InteractiveInput) && session instanceof GraphRuntimeSession runtimeSession) {
                WorkflowState state = runtimeSession.workflowState();
                if (state != null) {
                    state.commitUserInputs(inputs);
                }
            }
            try {
                result = pregel.run(pregelConfig);
            } catch (Exception ex) {
                exception = ex;
            }
            if (isMain && checkpointer != null) {
                checkpointer.postWorkflowExecute(session, result, exception);
            } else {
                if (exception != null) {
                    throw propagate(exception);
                }
            }
            return result;
        } finally {
            if (pregelGraph != null) {
                pregelGraph.endRun();
            }
        }
    }

    private static String describeSession(BaseSession session) {
        if (session instanceof GraphRuntimeSession runtimeSession) {
            return runtimeSession.workflowId();
        }
        return String.valueOf(session);
    }

    /**
     * Initialize vertices with the invocation session.
     *
     * <p>Falls back to the compile-argument session when the invocation
     * session is not a VertexSession (e.g. null or a plain BaseSession),
     * preserving backward compatibility for callers that pass session only
     * at compile time.
     *
     * @param session the invocation session passed to the current run
     */
    private void initVerticesForRun(BaseSession session) {
        if (pregelGraph == null) {
            return;
        }
        BaseSession effectiveSession = session != null ? session : compileSession;
        if (effectiveSession instanceof Vertex.VertexSession vertexSession) {
            pregelGraph.initVertices(vertexSession, compileOptions);
        }
    }

    @Override
    public Iterator<Map<String, Object>> stream(Map<String, Object> inputs, BaseSession session) {
        return null;
    }

    @Override
    public void interrupt(Map<String, Object> message) {
        // Mirrors Python's pass body.
    }

    public Pregel getPregel() {
        return pregel;
    }

    public GraphCheckpointer getCheckpointer() {
        return checkpointer;
    }

    private static PregelConfig normalizeConfig(Object config, BaseSession session) {
        if (config instanceof PregelConfig pregelConfig) {
            return pregelConfig;
        }
        PregelConfig normalized = defaultConfig(session);
        if (config instanceof Map<?, ?> map) {
            Object sessionId = map.get(PregelConstants.SESSION_ID);
            Object ns = map.get(PregelConstants.NS);
            Object parentNs = map.get(PregelConstants.PARENT_NS);
            Object recursionLimit = map.get(PregelConstants.RECURSION_LIMIT);
            if (sessionId != null) {
                normalized.setSessionId(String.valueOf(sessionId));
            }
            if (ns != null) {
                normalized.setNs(String.valueOf(ns));
            }
            if (parentNs != null) {
                normalized.setParentNs(String.valueOf(parentNs));
            }
            if (recursionLimit instanceof Number number) {
                normalized.setRecursionLimit(number.intValue());
            }
        }
        return normalized;
    }

    private static PregelConfig defaultConfig(BaseSession session) {
        String sessionId = null;
        String workflowId = null;
        if (session instanceof GraphRuntimeSession runtimeSession) {
            sessionId = runtimeSession.sessionId();
            workflowId = runtimeSession.workflowId();
        }
        return new PregelConfig(sessionId, workflowId, PregelConstants.MAX_RECURSIVE_LIMIT);
    }

    private static RuntimeException propagate(Exception exception) {
        if (exception instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(exception);
    }

    /**
     * Mirrors Python's runtime session dependency used by {@code CompiledGraph} in
     * {@code openjiuwen/core/graph/graph.py}.
     */
    public interface GraphRuntimeSession {
        String sessionId();

        String workflowId();

        WorkflowState workflowState();

        GraphCheckpointer checkpointer();
    }

    /**
     * Mirrors Python's workflow state calls used by {@code CompiledGraph} in
     * {@code openjiuwen/core/graph/graph.py}.
     */
    public interface WorkflowState {
        void commitUserInputs(Object inputs);

        void commit();
    }

    /**
     * Mirrors Python's checkpointer interaction used by {@code CompiledGraph} in
     * {@code openjiuwen/core/graph/graph.py}.
     */
    public interface GraphCheckpointer {
        default Store graphStore() {
            return null;
        }

        default void preWorkflowExecute(BaseSession session, Object inputs) {
        }

        default void postWorkflowExecute(BaseSession session, Map<String, Object> result, Exception exception) {
        }
    }

    static Map<String, Object> copyConfigMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof PregelConfig config) {
            result.putAll(config.toMap());
        } else if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }
}
