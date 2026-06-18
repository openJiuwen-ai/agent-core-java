/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.agents;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.single_agent.interrupt.BaseInterruptionState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Workflow interruption state for ReAct resume support.
 *
 * <p>Mirrors Python's {@code InterruptionState} in
 * {@code openjiuwen/core/single_agent/agents/react_agent.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InterruptionState extends BaseInterruptionState {
    @JsonProperty("interrupted_workflows")
    private Map<String, WorkflowInterruptEntry> interruptedWorkflows = new LinkedHashMap<>();

    @JsonProperty("pending_workflow_id")
    private String pendingWorkflowId;

    @JsonProperty("pending_component_id")
    private String pendingComponentId;

    public Map<String, WorkflowInterruptEntry> getInterruptedWorkflows() {
        return new LinkedHashMap<>(interruptedWorkflows);
    }

    public void setInterruptedWorkflows(Map<String, WorkflowInterruptEntry> interruptedWorkflows) {
        this.interruptedWorkflows = interruptedWorkflows == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(interruptedWorkflows);
    }

    public String getPendingWorkflowId() {
        return pendingWorkflowId;
    }

    public void setPendingWorkflowId(String pendingWorkflowId) {
        this.pendingWorkflowId = pendingWorkflowId;
    }

    public String getPendingComponentId() {
        return pendingComponentId;
    }

    public void setPendingComponentId(String pendingComponentId) {
        this.pendingComponentId = pendingComponentId;
    }
}
