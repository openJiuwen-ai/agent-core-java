// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.workflow;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Final output container for workflow execution.
 * 
 * 对应Python: workflow/base.py - WorkflowOutput
 */
public class WorkflowOutput {

    private Object result;
    private WorkflowExecutionState state;

    public WorkflowOutput() {
    }

    public WorkflowOutput(Object result, WorkflowExecutionState state) {
        this.result = result;
        this.state = state;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public WorkflowExecutionState getState() {
        return state;
    }

    public void setState(WorkflowExecutionState state) {
        this.state = state;
    }

    /**
     * Convert to a Map for serialization.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("result", result);
        map.put("state", state != null ? state.getValue() : null);
        return map;
    }

    /**
     * Create from a Map.
     */
    public static WorkflowOutput fromMap(Map<String, Object> data) {
        WorkflowOutput output = new WorkflowOutput();
        output.result = data.get("result");
        Object stateObj = data.get("state");
        if (stateObj instanceof WorkflowExecutionState wes) {
            output.state = wes;
        } else if (stateObj instanceof String s) {
            output.state = WorkflowExecutionState.valueOf(s);
        }
        return output;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof WorkflowOutput other)) return false;
        return java.util.Objects.equals(result, other.result)
                && state == other.state;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(result, state);
    }

    @Override
    public String toString() {
        return "WorkflowOutput(result=" + result + ", state=" + state + ")";
    }
}

