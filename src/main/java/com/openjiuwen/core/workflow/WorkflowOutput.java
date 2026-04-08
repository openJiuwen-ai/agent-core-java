/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

/**
 * Final output container for workflow execution.
 * Contains both the result data and the execution state.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.base.WorkflowOutput}.
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

    @Override
    public String toString() {
        return "WorkflowOutput{result=" + result + ", state=" + state + "}";
    }
}
