// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.workflow;

/**
 * Possible states of workflow execution.
 * 
 * 对应Python: workflow/base.py - WorkflowExecutionState
 */
public enum WorkflowExecutionState {
    COMPLETED("COMPLETED"),
    INPUT_REQUIRED("INPUT_REQUIRED"),
    ERROR("ERROR");

    private final String value;

    WorkflowExecutionState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

