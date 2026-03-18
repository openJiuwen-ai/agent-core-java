/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.exception;

import java.util.Map;

/** Session error. */
public class SessionError extends ExecutionError {
    public SessionError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) { super(status, msg, details, cause, params); }
    public SessionError(StatusCode status, Map<String, Object> params) { super(status, params); }
    public SessionError(StatusCode status) { super(status); }
}
