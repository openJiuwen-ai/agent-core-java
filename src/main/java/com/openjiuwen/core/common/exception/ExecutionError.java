/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Execution-time errors during workflow / agent / tool execution.
 * Usually recoverable via retry / replan.
 * 
 * @since 0.1.7
 */
public class ExecutionError extends BaseError {
    /**
     * ExecutionError.
     * 
     * @param status status
     * @param msg msg
     * @param details details
     * @param cause cause
     * @param params params
     * @since 0.1.7
     */
    public ExecutionError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }

    /**
     * Creates an ExecutionError with status and parameters.
     * 
     * @param status the status code
     * @param params template parameters for message rendering
     * @since 0.1.7
     */
    public ExecutionError(StatusCode status, Map<String, Object> params) {
        super(status, params);
    }

    /**
     * Creates an ExecutionError with status only.
     * 
     * @param status the status code
     * @since 0.1.7
     */
    public ExecutionError(StatusCode status) {
        super(status);
    }

    /**
     * defaultRecoverable.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    protected boolean defaultRecoverable() {
        return true;
    }

    /**
     * defaultFatal.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    protected boolean defaultFatal() {
        return false;
    }
}
