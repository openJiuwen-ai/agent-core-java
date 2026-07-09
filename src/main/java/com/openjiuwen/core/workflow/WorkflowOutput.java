/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

/**
 * Final output container for workflow execution.
 * Contains both the result data and the execution state.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.base.WorkflowOutput}.
 * 
 * @since 0.1.7
 */
public class WorkflowOutput {
    private Object result;
    private WorkflowExecutionState state;

    /**
     * WorkflowOutput.
     * 
     * @since 0.1.7
     */
    public WorkflowOutput() {
    }

    /**
     * WorkflowOutput.
     * 
     * @param result result
     * @param state state
     * @since 0.1.7
     */
    public WorkflowOutput(Object result, WorkflowExecutionState state) {
        this.result = result;
        this.state = state;
    }

    /**
     * getResult.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getResult() {
        return result;
    }

    /**
     * setResult.
     * 
     * @param result result
     * @since 0.1.7
     */
    public void setResult(Object result) {
        this.result = result;
    }

    /**
     * getState.
     * 
     * @return the result
     * @since 0.1.7
     */
    public WorkflowExecutionState getState() {
        return state;
    }

    /**
     * setState.
     * 
     * @param state state
     * @since 0.1.7
     */
    public void setState(WorkflowExecutionState state) {
        this.state = state;
    }

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        return "WorkflowOutput{result=" + result + ", state=" + state + "}";
    }
}
