/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import com.openjiuwen.core.common.exception.ExecutionError;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.Map;

/**
 * Mirrors Python's {@code AbortError} in
 * {@code openjiuwen/core/runner/callback/errors.py}.
 */
public class AbortError extends ExecutionError {

    private final String reason;

    public AbortError() {
        this("", null, null);
    }

    public AbortError(String reason) {
        this(reason, null, null);
    }

    public AbortError(String reason, Throwable cause) {
        this(reason, cause, null);
    }

    public AbortError(String reason, Throwable cause, Object details) {
        super(
                StatusCode.CALLBACK_EXECUTION_ABORTED,
                null,
                details,
                cause,
                Map.of("reason", reason != null ? reason : "")
        );
        this.reason = reason != null ? reason : "";
    }

    public String getReason() {
        return reason;
    }

    @Override
    protected boolean defaultRecoverable() {
        return false;
    }

    @Override
    protected boolean defaultFatal() {
        return false;
    }
}
