/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.agents;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-workflow interruption record used by {@link ReActAgent}.
 *
 * <p>Mirrors Python's {@code WorkflowInterruptEntry} in
 * {@code openjiuwen/core/single_agent/agents/react_agent.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowInterruptEntry {
    @JsonProperty("tool_call")
    private ToolCall toolCall;

    @JsonProperty("component_ids")
    private List<String> componentIds = new ArrayList<>();

    @JsonProperty("workflow_execution_state")
    private Object workflowExecutionState;

    @JsonProperty("collected_input")
    private Object collectedInput;

    public WorkflowInterruptEntry() {
    }

    public WorkflowInterruptEntry(ToolCall toolCall, List<String> componentIds, Object workflowExecutionState,
                                  Object collectedInput) {
        this.toolCall = toolCall;
        setComponentIds(componentIds);
        this.workflowExecutionState = workflowExecutionState;
        this.collectedInput = collectedInput;
    }

    public ToolCall getToolCall() {
        return toolCall;
    }

    public void setToolCall(ToolCall toolCall) {
        this.toolCall = toolCall;
    }

    public List<String> getComponentIds() {
        return new ArrayList<>(componentIds);
    }

    public void setComponentIds(List<String> componentIds) {
        this.componentIds = componentIds == null ? new ArrayList<>() : new ArrayList<>(componentIds);
    }

    public Object getWorkflowExecutionState() {
        return workflowExecutionState;
    }

    public void setWorkflowExecutionState(Object workflowExecutionState) {
        this.workflowExecutionState = workflowExecutionState;
    }

    public Object getCollectedInput() {
        return collectedInput;
    }

    public void setCollectedInput(Object collectedInput) {
        this.collectedInput = collectedInput;
    }
}
