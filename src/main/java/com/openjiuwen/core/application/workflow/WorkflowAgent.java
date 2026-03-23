/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */
package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.controller.Controller;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.ControllerAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Workflow-based Agent - Executes predefined workflows with multi-workflow controller.
 *
 * <p>Implemented using ControllerAgent with WorkflowEventHandler for
 * workflow-specific execution logic including intent detection and
 * interruption handling.</p>
 *
 * <p>Mirrors Python's {@code WorkflowAgent} in {@code openjiuwen.core.application.workflow_agent}.</p>
 */
public class WorkflowAgent extends ControllerAgent {

    private final WorkflowAgentConfig agentConfig;

    /**
     * Create WorkflowAgent with the given configuration.
     *
     * @param agentConfig the workflow agent configuration
     */
    public WorkflowAgent(WorkflowAgentConfig agentConfig) {
        super(buildAgentCard(agentConfig), new Controller(), buildControllerConfig());
        if (agentConfig.getControllerType() != null
                && agentConfig.getControllerType() != ControllerType.WORKFLOW_CONTROLLER) {
            throw new UnsupportedOperationException(
                    "WorkflowAgent requires WORKFLOW_CONTROLLER, got " + agentConfig.getControllerType()
            );
        }
        this.agentConfig = agentConfig;

        // Set up the WorkflowEventHandler on the controller
        WorkflowEventHandler eventHandler = new WorkflowEventHandler(agentConfig, getContextEngine());
        getController().setEventHandler(eventHandler);
    }

    @Override
    public ControllerOutput invoke(Object inputs, Session session) {
        AgentSessionApi managedSession = session == null ? createManagedSession(inputs) : null;
        Session effectiveSession = managedSession != null ? managedSession : session;

        if (managedSession != null) {
            managedSession.preRun(inputs);
        }
        try {
            return super.invoke(inputs, effectiveSession);
        } finally {
            if (managedSession != null) {
                managedSession.postRun();
            }
        }
    }

    @Override
    public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
        AgentSessionApi managedSession = session == null ? createManagedSession(inputs) : null;
        Session effectiveSession = managedSession != null ? managedSession : session;

        if (managedSession != null) {
            managedSession.preRun(inputs);
        }
        Iterator<Object> delegate = super.stream(inputs, effectiveSession, streamModes);
        return new Iterator<>() {
            private boolean finalized;

            @Override
            public boolean hasNext() {
                boolean hasNext = delegate.hasNext();
                if (!hasNext) {
                    finalizeStream();
                }
                return hasNext;
            }

            @Override
            public Object next() {
                try {
                    return delegate.next();
                } catch (NoSuchElementException e) {
                    finalizeStream();
                    throw e;
                }
            }

            private void finalizeStream() {
                if (finalized) {
                    return;
                }
                finalized = true;
                if (managedSession != null) {
                    managedSession.postRun();
                }
            }
        };
    }

    public WorkflowAgentConfig getAgentConfig() {
        return agentConfig;
    }

    // ==================== Private Helpers ====================

    private static AgentCard buildAgentCard(WorkflowAgentConfig config) {
        return AgentCard.builder()
                .id(config.getId())
                .name(config.getId())
                .description(config.getDescription())
                .build();
    }

    private static ControllerConfig buildControllerConfig() {
        ControllerConfig cc = new ControllerConfig();
        cc.setMaxConcurrentTasks(1);
        return cc;
    }

    private AgentSessionApi createManagedSession(Object inputs) {
        String sessionId = "default_session";
        if (inputs instanceof Map<?, ?> inputMap) {
            Object conversationId = inputMap.get("conversation_id");
            if (conversationId instanceof String s && !s.isBlank()) {
                sessionId = s;
            }
        }
        return AgentSessionApi.create(sessionId, null, getCard());
    }
}
