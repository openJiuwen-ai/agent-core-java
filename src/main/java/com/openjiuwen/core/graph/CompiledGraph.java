/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
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

/**
 * A compiled graph that wraps a Pregel engine and a Checkpointer for execution.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.graph.CompiledGraph}.
 */
public class CompiledGraph extends ExecutableGraph<Object, Map<String, Object>> {

    private static final LoggerProtocol logger = Loggers.GRAPH;

    private final Pregel pregel;
    private final Checkpointer checkpointer;

    public CompiledGraph(Pregel pregel, Checkpointer checkpointer) {
        this.pregel = pregel;
        this.checkpointer = checkpointer;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> doInvoke(Object inputs, BaseSession session, Object config) {
        boolean isMain = session instanceof WorkflowSession;
        String sessionId = session.sessionId();
        String workflowId = "";

        if (session instanceof WorkflowSession) {
            workflowId = ((WorkflowSession) session).workflowId();
        } else {
            workflowId = sessionId;
        }

        PregelConfig pregelConfig;
        if (config == null) {
            pregelConfig = new PregelConfig(sessionId, workflowId, PregelConstants.MAX_RECURSIVE_LIMIT);
        } else if (config instanceof PregelConfig) {
            pregelConfig = (PregelConfig) config;
        } else {
            pregelConfig = new PregelConfig(sessionId, workflowId, PregelConstants.MAX_RECURSIVE_LIMIT);
        }

        try {
            // Pre-execution checkpoint
            if (isMain && checkpointer != null) {
                if (inputs instanceof InteractiveInput interactiveInput) {
                    checkpointer.preWorkflowExecute(session, interactiveInput);
                } else {
                    checkpointer.preWorkflowExecute(session, null);
                }
            }

            // Commit user inputs to state
            if (!(inputs instanceof InteractiveInput) && inputs instanceof Map) {
                if (session.state() instanceof WorkflowStateCollection) {
                    ((WorkflowStateCollection) session.state()).commitUserInputs((Map<String, Object>) inputs);
                }
            }

            Map<String, Object> result = null;
            Exception exception = null;

            try {
                result = pregel.run(pregelConfig);
            } catch (Exception e) {
                exception = e;
            }

            // Post-execution checkpoint
            if (isMain && checkpointer != null) {
                checkpointer.postWorkflowExecute(session, result, exception);
            } else if (exception != null) {
                if (exception instanceof RuntimeException) {
                    throw (RuntimeException) exception;
                }
                throw new RuntimeException(exception);
            }

            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Iterator<Map<String, Object>> stream(Object inputs, BaseSession session) {
        // Stream not yet implemented
        return null;
    }

    @Override
    public void interrupt(Map<String, Object> message) {
        // No-op
    }
}
