/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Map;

/** Runner execution error. */
public class RunnerError extends ExecutionError {
    /**
     * Creates a RunnerError with full details.
     *
     * @param status  the status code
     * @param msg     optional custom message
     * @param details optional additional details
     * @param cause   optional root cause
     * @param params  template parameters for message rendering
     */
    public RunnerError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) { super(status, msg, details, cause, params); }
    /**
     * Creates a RunnerError with status and parameters.
     *
     * @param status the status code
     * @param params template parameters for message rendering
     */
    public RunnerError(StatusCode status, Map<String, Object> params) { super(status, params); }
    /**
     * Creates a RunnerError with status only.
     *
     * @param status the status code
     */
    public RunnerError(StatusCode status) { super(status); }
}
