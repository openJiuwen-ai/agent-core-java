/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Map;

/** Context engine error. */
public class ContextError extends ExecutionError {
    /**
     * Creates a ContextError with full details.
     *
     * @param status  the status code
     * @param msg     optional custom message
     * @param details optional additional details
     * @param cause   optional root cause
     * @param params  template parameters for message rendering
     */
    public ContextError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) { super(status, msg, details, cause, params); }
    
    /**
     * Creates a ContextError with status and parameters.
     *
     * @param status the status code
     * @param params template parameters for message rendering
     */
    public ContextError(StatusCode status, Map<String, Object> params) { super(status, params); }
    
    /**
     * Creates a ContextError with status only.
     *
     * @param status the status code
     */
    public ContextError(StatusCode status) { super(status); }
}
