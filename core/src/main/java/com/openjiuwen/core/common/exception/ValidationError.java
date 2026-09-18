/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Mirrors Python's {@code ValidationError} in
 * {@code openjiuwen/core/common/exception/errors.py}.
 */
public class ValidationError extends BaseError {
    public ValidationError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }
    public ValidationError(StatusCode status, Map<String, Object> params) { super(status, params); }
    public ValidationError(StatusCode status) { super(status); }
    @Override protected boolean defaultRecoverable() { return false; }
    @Override protected boolean defaultFatal() { return false; }
}
