/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.exception;

import java.util.Map;

/** Workflow execution error. */
public class WorkflowError extends ExecutionError {
    public WorkflowError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) { super(status, msg, details, cause, params); }
    public WorkflowError(StatusCode status, Map<String, Object> params) { super(status, params); }
    public WorkflowError(StatusCode status) { super(status); }
}
