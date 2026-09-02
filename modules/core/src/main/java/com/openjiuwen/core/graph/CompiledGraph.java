/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.graph.pregel.Pregel;
import com.openjiuwen.core.graph.pregel.PregelConfig;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.WorkflowStateCollection;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * A compiled graph that wraps a Pregel engine and a Checkpointer for execution.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.graph.CompiledGraph}.
 * 
 * @since 0.1.7
 */
public class CompiledGraph extends ExecutableGraph<Object, Map<String, Object>> {
    private static final LoggerProtocol logger = Loggers.GRAPH;

    private final Pregel pregel;
    private final Checkpointer checkpointer;

    /**
     * Creates a CompiledGraph with the given Pregel engine and checkpointer.
     * 
     * @param pregel the Pregel execution engine
     * @param checkpointer the checkpointer for state persistence
     * @since 0.1.7
     */
    public CompiledGraph(Pregel pregel, Checkpointer checkpointer) {
        this.pregel = pregel;
        this.checkpointer = checkpointer;
    }

    /**
     * doInvoke.
     * 
     * @param inputs inputs
     * @param session session
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    @Override
    protected Map<String, Object> doInvoke(Object inputs, BaseSession session, Object config) {
        boolean isMain = session instanceof WorkflowSession;
        String sessionId = session.sessionId();
        String workflowId = session instanceof WorkflowSession workflowSession
                ? workflowSession.workflowId()
                : sessionId;
        PregelConfig pregelConfig = resolvePregelConfig(config, sessionId, workflowId);
        prepareExecution(inputs, session, isMain);
        GraphExecutionResult executionResult = executePregel(pregelConfig);
        return finishExecution(session, isMain, executionResult);
    }

    /**
     * Resolve the Pregel configuration for this invocation.
     *
     * @param config optional invocation configuration
     * @param sessionId session ID
     * @param workflowId workflow ID
     * @return resolved Pregel configuration
     * @since 0.1.7
     */
    private static PregelConfig resolvePregelConfig(Object config, String sessionId, String workflowId) {
        if (config instanceof PregelConfig pregelConfig) {
            return pregelConfig;
        }
        return new PregelConfig(sessionId, workflowId, PregelConstants.MAX_RECURSIVE_LIMIT);
    }

    /**
     * Run pre-execution checkpointing and commit fresh user input.
     *
     * @param inputs workflow inputs
     * @param session workflow session
     * @param isMain whether this is a top-level workflow session
     * @since 0.1.7
     */
    private void prepareExecution(Object inputs, BaseSession session, boolean isMain) {
        if (isMain && checkpointer != null) {
            InteractiveInput interactiveInput = inputs instanceof InteractiveInput value ? value : null;
            checkpointer.preWorkflowExecute(session, interactiveInput);
        }
        commitUserInputs(inputs, session);
    }

    /**
     * Commit non-interactive workflow inputs to the state collection.
     *
     * @param inputs workflow inputs
     * @param session workflow session
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    private static void commitUserInputs(Object inputs, BaseSession session) {
        if (!(inputs instanceof InteractiveInput) && inputs instanceof Map
                && session.state() instanceof WorkflowStateCollection state) {
            state.commitUserInputs((Map<String, Object>) inputs);
        }
    }

    /**
     * Execute Pregel synchronously while retaining its checked failure for checkpointing.
     *
     * @param pregelConfig Pregel configuration
     * @return execution result and optional failure
     * @since 0.1.7
     */
    private GraphExecutionResult executePregel(PregelConfig pregelConfig) {
        FutureTask<Map<String, Object>> execution = new FutureTask<>(() -> pregel.run(pregelConfig));
        execution.run();
        try {
            return new GraphExecutionResult(execution.get(), null);
        } catch (CancellationException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            CancellationException cancellation = new CancellationException("Pregel execution cancelled");
            cancellation.initCause(exception);
            throw cancellation;
        } catch (ExecutionException exception) {
            return handlePregelFailure(exception.getCause());
        }
    }

    /**
     * Convert a captured Pregel failure to the graph execution result.
     *
     * @param failure Pregel failure
     * @return failed graph execution result
     * @since 0.1.7
     */
    private static GraphExecutionResult handlePregelFailure(Throwable failure) {
        if (failure instanceof CancellationException cancellation) {
            throw cancellation;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof Exception exception) {
            return new GraphExecutionResult(null, exception);
        }
        return new GraphExecutionResult(null, new IllegalStateException("Pregel execution failed", failure));
    }

    /**
     * Run post-execution checkpointing or propagate the captured graph failure.
     *
     * @param session workflow session
     * @param isMain whether this is a top-level workflow session
     * @param executionResult graph execution result
     * @return graph result
     * @since 0.1.7
     */
    private Map<String, Object> finishExecution(BaseSession session, boolean isMain,
            GraphExecutionResult executionResult) {
        if (isMain && checkpointer != null) {
            checkpointer.postWorkflowExecute(session, executionResult.result(), executionResult.failure());
            return executionResult.result();
        }
        Exception failure = executionResult.failure();
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure != null) {
            throw new IllegalStateException("Pregel execution failed", failure);
        }
        return executionResult.result();
    }

    /**
     * Captures the Pregel result and the checked failure passed to checkpointing.
     *
     * @param result graph result
     * @param failure graph failure
     * @since 0.1.7
     */
    private record GraphExecutionResult(Map<String, Object> result, Exception failure) {
    }

    /**
     * stream.
     * 
     * @param inputs inputs
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<Map<String, Object>> stream(Object inputs, BaseSession session) {
        // Stream not yet implemented
        return null;
    }

    /**
     * interrupt.
     * 
     * @param message message
     * @since 0.1.7
     */
    @Override
    public void interrupt(Map<String, Object> message) {
        // No-op
    }
}
