/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.graph.pregel.Interrupt;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamWriter;

import java.util.HashMap;
import java.util.Map;

/**
 * Workflow-level interaction handler that interrupts graph execution for user input.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.interaction.interaction.WorkflowInteraction}.
 * <p>
 * Throws proper {@link GraphInterrupt} with {@link Interrupt} value so the graph engine
 * can recover from the interruption.
 */
public class WorkflowInteraction extends BaseInteraction {

    private final String nodeId;

    @SuppressWarnings("unchecked")
    public WorkflowInteraction(BaseSession session) {
        super(session, getWorkflowInteractiveInput(session));
        this.nodeId = getExecutableId(session);
    }

    private static Object getWorkflowInteractiveInput(BaseSession session) {
        if (!(session.state() instanceof WorkflowStateCollection stateCollection)) {
            return null;
        }
        synchronized (stateCollection) {
            Object interactiveInput = stateCollection.getWorkflow(Constant.INTERACTIVE_INPUT);
            if (interactiveInput != null) {
                Map<String, Object> clearMap = new HashMap<>();
                clearMap.put(Constant.INTERACTIVE_INPUT, null);
                stateCollection.updateWorkflow(clearMap);
                if (session.state() instanceof WorkflowCommitState commitState) {
                    commitState.commitWorkflow();
                }
            }
            return interactiveInput;
        }
    }

    private static String getExecutableId(BaseSession session) {
        if (session instanceof NodeSession nodeSession) {
            return nodeSession.executableId();
        }
        return session.sessionId();
    }

    @Override
    public Object waitUserInputs(Object value) {
        Object res = getNextInteractiveInput();
        if (res != null) {
            return res;
        }

        if (session.state() instanceof WorkflowStateCollection stateCollection) {
            stateCollection.commitCmp();
        }

        InteractionOutput payload = new InteractionOutput(nodeId, value);

        Map<String, Object> outputData = new HashMap<>();
        outputData.put("type", Constant.INTERACTION);
        outputData.put("index", idx);
        outputData.put("payload", payload);

        // Write to output stream if available
        if (session.streamWriterManager() != null) {
            StreamWriter<OutputSchema> outputWriter = session.streamWriterManager().getOutputWriter();
            if (outputWriter != null) {
                outputWriter.write(outputData);
            }
        }

        // Throw proper GraphInterrupt with recoverable Interrupt value
        throw new GraphInterruptRuntimeWrapper(
                new GraphInterrupt(new Interrupt(outputData)));
    }

    @Override
    public Object userLatestInput(Object value) {
        if (latestInteractiveInputs != null) {
            Object res = latestInteractiveInputs;
            latestInteractiveInputs = null;
            return res;
        }

        Map<String, Object> outputData = new HashMap<>();
        outputData.put("type", Constant.INTERACTION);
        outputData.put("index", idx);
        outputData.put("payload", new InteractionOutput(nodeId, value));

        if (session.streamWriterManager() != null) {
            StreamWriter<OutputSchema> outputWriter = session.streamWriterManager().getOutputWriter();
            if (outputWriter != null) {
                outputWriter.write(outputData);
            }
        }

        throw new GraphInterruptRuntimeWrapper(
                new GraphInterrupt(new Interrupt(outputData)));
    }

    /**
     * Runtime wrapper for {@link GraphInterrupt} so it can be thrown from methods
     * that don't declare checked exceptions.
     */
    public static class GraphInterruptRuntimeWrapper extends RuntimeException {
        private final GraphInterrupt graphInterrupt;

        public GraphInterruptRuntimeWrapper(GraphInterrupt cause) {
            super(cause.getMessage(), cause);
            this.graphInterrupt = cause;
        }

        public GraphInterrupt getGraphInterrupt() {
            return graphInterrupt;
        }
    }
}
