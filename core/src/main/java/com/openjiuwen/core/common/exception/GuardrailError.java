/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Mirrors Python's {@code GuardrailError} in
 * {@code openjiuwen/core/common/exception/errors.py}.
 */
public class GuardrailError extends ValidationError {
    public GuardrailError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) { super(status, msg, details, cause, params); }
    public GuardrailError(StatusCode status, Map<String, Object> params) { super(status, params); }
    public GuardrailError(StatusCode status) { super(status); }
}
