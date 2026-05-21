/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import com.openjiuwen.core.common.exception.ExecutionError;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.Map;

/**
 * Exception to abort callback execution and propagate the error out of trigger().
 * <p>
 * Mirrors Python's {@code openjiuwen.core.runner.callback.errors.AbortError}.
 * </p>
 * <p>
 * When raised in a callback, stops further callback execution and propagates
 * out of trigger(). If cause is provided, trigger() re-raises cause instead of
 * AbortError, so the caller sees the original exception.
 * </p>
 */
public class AbortError extends ExecutionError {

    private final String reason;

    /**
     * Creates an AbortError with reason and optional cause.
     *
     * @param reason Human-readable reason for abort
     * @param cause  Optional exception to re-raise at trigger boundary
     */
    public AbortError(String reason, Throwable cause) {
        super(StatusCode.CALLBACK_EXECUTION_ABORTED, reason, null, cause,
                Map.of("reason", reason != null ? reason : ""));
        this.reason = reason != null ? reason : "";
    }

    /**
     * Creates an AbortError with reason only.
     *
     * @param reason Human-readable reason for abort
     */
    public AbortError(String reason) {
        this(reason, null);
    }

    /**
     * Creates an AbortError with default reason.
     */
    public AbortError() {
        this("", null);
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