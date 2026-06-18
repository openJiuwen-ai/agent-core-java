/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

/**
 * Mirrors Python's {@code WorkflowExecutionState} in
 * {@code openjiuwen/core/workflow/base.py}.
 */
public enum WorkflowExecutionState {
    COMPLETED,
    INPUT_REQUIRED,
    ERROR
}
