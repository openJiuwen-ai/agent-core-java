/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Map;

/** Component execution error. */
public class ComponentError extends ExecutionError {
    /**
     * Creates a ComponentError with full details.
     *
     * @param status  the status code
     * @param msg     optional custom message
     * @param details optional additional details
     * @param cause   optional root cause
     * @param params  template parameters for message rendering
     */
    public ComponentError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) { super(status, msg, details, cause, params); }
    
    /**
     * Creates a ComponentError with status and parameters.
     *
     * @param status the status code
     * @param params template parameters for message rendering
     */
    public ComponentError(StatusCode status, Map<String, Object> params) { super(status, params); }
    
    /**
     * Creates a ComponentError with status only.
     *
     * @param status the status code
     */
    public ComponentError(StatusCode status) { super(status); }
}
