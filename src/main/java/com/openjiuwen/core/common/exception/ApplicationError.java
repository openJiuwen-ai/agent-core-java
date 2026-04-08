/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Map;

/** Application-level execution error. */
public class ApplicationError extends ExecutionError {
    /**
     * Creates an ApplicationError with full details.
     *
     * @param status  the status code
     * @param msg     optional custom message
     * @param details optional additional details
     * @param cause   optional root cause
     * @param params  template parameters for message rendering
     */
    public ApplicationError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }
    
    /**
     * Creates an ApplicationError with status and parameters.
     *
     * @param status the status code
     * @param params template parameters for message rendering
     */
    public ApplicationError(StatusCode status, Map<String, Object> params) { super(status, params); }
    
    /**
     * Creates an ApplicationError with status only.
     *
     * @param status the status code
     */
    public ApplicationError(StatusCode status) { super(status); }
}
