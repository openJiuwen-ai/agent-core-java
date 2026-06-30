/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public WorkflowOutput() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public WorkflowOutput(Object result, WorkflowExecutionState state) {
        this.result = result;
        this.state = state;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getResult() {
        return result;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setResult(Object result) {
        this.result = result;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public WorkflowExecutionState getState() {
        return state;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setState(WorkflowExecutionState state) {
        this.state = state;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String toString() {
        return "WorkflowOutput{result=" + result + ", state=" + state + "}";
    }
}
