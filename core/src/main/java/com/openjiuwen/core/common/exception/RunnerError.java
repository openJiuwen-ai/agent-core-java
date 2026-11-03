/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Mirrors Python's {@code RunnerError} in
 * {@code openjiuwen/core/common/exception/errors.py}.
 */
public class RunnerError extends ExecutionError {
    public RunnerError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) { super(status, msg, details, cause, params); }
    public RunnerError(StatusCode status, Map<String, Object> params) { super(status, params); }
    public RunnerError(StatusCode status) { super(status); }
}
