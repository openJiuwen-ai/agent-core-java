/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

/**
 * Possible states of workflow execution.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.base.WorkflowExecutionState}.
 */
public enum WorkflowExecutionState {
    COMPLETED,
    INPUT_REQUIRED,
    ERROR
}
