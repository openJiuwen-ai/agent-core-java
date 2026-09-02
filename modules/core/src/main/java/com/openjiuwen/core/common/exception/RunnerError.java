/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Runner execution error.
 * 
 * @since 0.1.7
 */
public class RunnerError extends ExecutionError {
    /**
     * RunnerError.
     * 
     * @param status status
     * @param msg msg
     * @param details details
     * @param cause cause
     * @param params params
     * @since 0.1.7
     */
    public RunnerError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }

    /**
     * Creates a RunnerError with status and parameters.
     * 
     * @param status the status code
     * @param params template parameters for message rendering
     * @since 0.1.7
     */
    public RunnerError(StatusCode status, Map<String, Object> params) {
        super(status, params);
    }

    /**
     * Creates a RunnerError with status only.
     * 
     * @param status the status code
     * @since 0.1.7
     */
    public RunnerError(StatusCode status) {
        super(status);
    }
}
