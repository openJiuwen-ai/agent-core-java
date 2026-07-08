/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow_agent;

import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.legacy.agent.ControllerAgent;
import com.openjiuwen.core.singleagent.legacy.config.WorkflowAgentConfig;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Workflow-based agent that executes predefined workflows with a workflow controller.
 *
 * <p>Mirrors Python's {@code WorkflowAgent} in
 * {@code openjiuwen/core/application/workflow_agent/workflow_agent.py}.</p>
 */
public class WorkflowAgent extends ControllerAgent {

    public WorkflowAgent(WorkflowAgentConfig agentConfig) {
        super(requireWorkflowController(agentConfig), new WorkflowController());
    }

    @Override
    protected com.openjiuwen.core.context_engine.ContextEngine createContextEngine() {
        return new com.openjiuwen.core.context.ContextEngine();
    }

    @Override
    public CompletionStage<Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
        return super.invoke(inputs, session);
    }

    public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session) {
        return stream(inputs, session, List.of(StreamMode.OUTPUT));
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session, List<StreamMode> streamModes) {
        return super.stream(inputs, session, streamModes);
    }

    private static WorkflowAgentConfig requireWorkflowController(WorkflowAgentConfig agentConfig) {
        WorkflowAgentConfig checked = Objects.requireNonNull(agentConfig, "agentConfig");
        if (checked.getControllerType() != ControllerType.WORKFLOW_CONTROLLER) {
            throw new UnsupportedOperationException(
                    "WorkflowAgent requires WorkflowController (WORKFLOW_CONTROLLER), got " + checked.getControllerType()
            );
        }
        return checked;
    }
}
