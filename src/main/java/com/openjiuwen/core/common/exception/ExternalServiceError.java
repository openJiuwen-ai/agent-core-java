/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Error from an external service call.
 * 
 * @since 0.1.7
 */
public class ExternalServiceError extends ExecutionError {
    /**
     * ExternalServiceError.
     * 
     * @param status status
     * @param msg msg
     * @param details details
     * @param cause cause
     * @param params params
     * @since 0.1.7
     */
    public ExternalServiceError(StatusCode status, String msg, Object details, Throwable cause,
            Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }

    /**
     * Creates an ExternalServiceError with status and parameters.
     * 
     * @param status the status code
     * @param params template parameters for message rendering
     * @since 0.1.7
     */
    public ExternalServiceError(StatusCode status, Map<String, Object> params) {
        super(status, params);
    }

    /**
     * Creates an ExternalServiceError with status only.
     * 
     * @param status the status code
     * @since 0.1.7
     */
    public ExternalServiceError(StatusCode status) {
        super(status);
    }
}
