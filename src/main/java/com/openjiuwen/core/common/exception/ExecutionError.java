/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Execution-time errors during workflow / agent / tool execution.
 * Usually recoverable via retry / replan.
 */
public class ExecutionError extends BaseError {

    /**
     * Creates an ExecutionError with full details.
     *
     * @param status  the status code
     * @param msg     optional custom message
     * @param details optional additional details
     * @param cause   optional root cause
     * @param params  template parameters for message rendering
     */
    public ExecutionError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }

    /**
     * Creates an ExecutionError with status and parameters.
     *
     * @param status the status code
     * @param params template parameters for message rendering
     */
    public ExecutionError(StatusCode status, Map<String, Object> params) {
        super(status, params);
    }

    /**
     * Creates an ExecutionError with status only.
     *
     * @param status the status code
     */
    public ExecutionError(StatusCode status) {
        super(status);
    }

    @Override
    protected boolean defaultRecoverable() {
        return true;
    }

    @Override
    protected boolean defaultFatal() {
        return false;
    }
}
