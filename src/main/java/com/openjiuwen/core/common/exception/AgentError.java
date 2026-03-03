/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.exception;

import java.util.Map;

/** Agent execution error. */
public class AgentError extends ExecutionError {
    public AgentError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) { super(status, msg, details, cause, params); }
    public AgentError(StatusCode status, Map<String, Object> params) { super(status, params); }
    public AgentError(StatusCode status) { super(status); }
}
