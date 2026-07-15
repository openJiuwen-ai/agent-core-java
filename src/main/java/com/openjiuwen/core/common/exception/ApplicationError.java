/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Application-level execution error.
 * 
 * @since 0.1.7
 */
public class ApplicationError extends ExecutionError {
    /**
     * ApplicationError.
     * 
     * @param status status
     * @param msg msg
     * @param details details
     * @param cause cause
     * @param params params
     * @since 0.1.7
     */
    public ApplicationError(StatusCode status, String msg, Object details, Throwable cause,
            Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }

    /**
     * Creates an ApplicationError with status and parameters.
     * 
     * @param status the status code
     * @param params template parameters for message rendering
     * @since 0.1.7
     */
    public ApplicationError(StatusCode status, Map<String, Object> params) {
        super(status, params);
    }

    /**
     * Creates an ApplicationError with status only.
     * 
     * @param status the status code
     * @since 0.1.7
     */
    public ApplicationError(StatusCode status) {
        super(status);
    }
}
