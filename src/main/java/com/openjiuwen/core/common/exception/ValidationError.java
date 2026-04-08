/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Constraint / validation / unsupported capability errors.
 * Should NOT retry or replan.
 */
public class ValidationError extends BaseError {

    /**
     * Creates a ValidationError with full details.
     *
     * @param status  the status code
     * @param msg     optional custom message
     * @param details optional additional details
     * @param cause   optional root cause
     * @param params  template parameters for message rendering
     */
    public ValidationError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }

    /**
     * Creates a ValidationError with status and parameters.
     *
     * @param status the status code
     * @param params template parameters for message rendering
     */
    public ValidationError(StatusCode status, Map<String, Object> params) {
        super(status, params);
    }

    /**
     * Creates a ValidationError with status only.
     *
     * @param status the status code
     */
    public ValidationError(StatusCode status) {
        super(status);
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
