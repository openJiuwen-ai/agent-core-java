/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.controller.modules.EventHandlerInput;
import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.session.AgentSessionApi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backward-compatible facade mirroring Python's {@code WorkflowController}.
 */
public class WorkflowController {

    private WorkflowAgentConfig agentConfig;
    private ContextEngine contextEngine;
    private WorkflowEventHandler eventHandler;

    public WorkflowController() {
    }

    public WorkflowController(WorkflowAgentConfig config, ContextEngine contextEngine) {
        configure(config, contextEngine);
    }

    public void setupFromAgent(WorkflowAgent agent) {
        if (agent == null) {
            throw new IllegalArgumentException("agent is required");
        }
        configure(agent.getAgentConfig(), agent.getContextEngine());
        eventHandler.setAbilityManager(agent.getAbilityManager());
    }

    public Map<String, Object> handleEvent(Event event, AgentSessionApi session) {
        ensureConfigured();
        return eventHandler.handleInput(new EventHandlerInput(event, session));
    }

    public WorkflowIntent intentDetection(Event event, AgentSessionApi session) {
        ensureConfigured();
        return eventHandler.intentDetection(event, session);
    }

    public Map<String, Object> execTask(Event event, Task task, AgentSessionApi session) {
        ensureConfigured();
        WorkflowSchema workflow = resolveWorkflow(task);
        if (workflow == null) {
            throw new IllegalArgumentException("No workflow matched task " + (task != null ? task.getTaskId() : null));
        }
        return eventHandler.execTask(event, task, session, workflow);
    }

    public void interruptTask(Task task, AgentSessionApi session, List<Object> interactionData) {
        ensureConfigured();
        eventHandler.interruptTask(task, session, interactionData);
    }

    public Event createMessage(Map<String, Object> inputs) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (inputs != null) {
            normalized.putAll(inputs);
        }
        if (!normalized.containsKey("query") && normalized.containsKey("content")) {
            normalized.put("query", normalized.get("content"));
        }
        normalized.putIfAbsent("query", "");
        return InputEvent.fromUserInput(normalized);
    }

    public WorkflowAgentConfig getAgentConfig() {
        return agentConfig;
    }

    public ContextEngine getContextEngine() {
        return contextEngine;
    }

    public WorkflowEventHandler getEventHandler() {
        ensureConfigured();
        return eventHandler;
    }

    private void configure(WorkflowAgentConfig config, ContextEngine contextEngine) {
        this.agentConfig = config;
        this.contextEngine = contextEngine != null
                ? contextEngine
                : new ContextEngine(ContextEngineConfig.builder().build());
        this.eventHandler = config != null ? new WorkflowEventHandler(config, this.contextEngine) : null;
    }

    private void ensureConfigured() {
        if (eventHandler == null) {
            throw new IllegalStateException("WorkflowController is not configured with agent config");
        }
    }

    private WorkflowSchema resolveWorkflow(Task task) {
        if (task == null || agentConfig == null || agentConfig.getWorkflows() == null) {
            return null;
        }
        String targetId = task.getMetadata() != null ? (String) task.getMetadata().get("target_id") : null;
        String targetName = task.getDescription();
        for (WorkflowSchema workflow : agentConfig.getWorkflows()) {
            String workflowId = workflow.getId() + "_" + workflow.getVersion();
            if ((targetId != null && targetId.equals(workflowId))
                    || (targetName != null && targetName.equals(workflow.getName()))) {
                return workflow;
            }
        }
        return null;
    }
}
