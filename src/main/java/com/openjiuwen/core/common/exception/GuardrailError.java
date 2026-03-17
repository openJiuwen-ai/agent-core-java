/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Guardrail security check blocked error.
 * Raised when guardrail detects a security risk and blocks the execution.
 */
public class GuardrailError extends ValidationError {
    public GuardrailError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) { super(status, msg, details, cause, params); }
    public GuardrailError(StatusCode status, Map<String, Object> params) { super(status, params); }
    public GuardrailError(StatusCode status) { super(status); }
}
