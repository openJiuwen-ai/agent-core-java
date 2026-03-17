/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.exception;

import java.util.Map;

/** Toolchain execution error. */
public class ToolchainError extends ExecutionError {
    public ToolchainError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) { super(status, msg, details, cause, params); }
    public ToolchainError(StatusCode status, Map<String, Object> params) { super(status, params); }
    public ToolchainError(StatusCode status) { super(status); }
}
