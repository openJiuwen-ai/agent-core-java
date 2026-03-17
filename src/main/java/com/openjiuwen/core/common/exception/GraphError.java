/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.exception;

import java.util.Map;

/** Graph execution error. */
public class GraphError extends ExecutionError {
    public GraphError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) { super(status, msg, details, cause, params); }
    public GraphError(StatusCode status, Map<String, Object> params) { super(status, params); }
    public GraphError(StatusCode status) { super(status); }
}
