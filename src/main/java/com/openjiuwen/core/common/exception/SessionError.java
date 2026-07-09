/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Session error.
 * 
 * @since 0.1.7
 */
public class SessionError extends ExecutionError {
    /**
     * SessionError.
     * 
     * @param status status
     * @param msg msg
     * @param details details
     * @param cause cause
     * @param params params
     * @since 0.1.7
     */
    public SessionError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }

    /**
     * Creates a SessionError with status and parameters.
     * 
     * @param status the status code
     * @param params template parameters for message rendering
     * @since 0.1.7
     */
    public SessionError(StatusCode status, Map<String, Object> params) {
        super(status, params);
    }

    /**
     * Creates a SessionError with status only.
     * 
     * @param status the status code
     * @since 0.1.7
     */
    public SessionError(StatusCode status) {
        super(status);
    }
}
