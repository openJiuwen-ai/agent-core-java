/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Model call error that retrying cannot recover from (for example quota
 * exhausted, permanent authentication failure, or context length exceeded).
 * Marked non-recoverable so upstream error surfaces do not advertise the
 * failure as retryable. The recoverable flag is a constant override rather
 * than an instance field because {@link BaseError} evaluates
 * {@link #defaultRecoverable()} during its constructor, before any subclass
 * field could be assigned.
 *
 * @since 2026-10-19
 */
public class PermanentModelError extends ModelError {
    /**
     * Creates an error with full context.
     *
     * @param status status code carrying code and message template
     * @param msg custom message, template message is used when null
     * @param details structured detail payload, must be serializable
     * @param cause original cause
     * @param params template placeholder values
     */
    public PermanentModelError(StatusCode status, String msg, Object details, Throwable cause,
        Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }

    /**
     * Creates an error with template params only.
     *
     * @param status status code carrying code and message template
     * @param params template placeholder values
     */
    public PermanentModelError(StatusCode status, Map<String, Object> params) {
        super(status, params);
    }

    /**
     * Creates an error with template message only.
     *
     * @param status status code carrying code and message template
     */
    public PermanentModelError(StatusCode status) {
        super(status);
    }

    @Override
    protected boolean defaultRecoverable() {
        return false;
    }
}
