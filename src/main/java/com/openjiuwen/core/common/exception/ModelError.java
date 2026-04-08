/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Map;

/** Model (LLM) error. */
public class ModelError extends ExecutionError {
    /**
     * Creates a ModelError with full details.
     *
     * @param status  the status code
     * @param msg     optional custom message
     * @param details optional additional details
     * @param cause   optional root cause
     * @param params  template parameters for message rendering
     */
    public ModelError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) { super(status, msg, details, cause, params); }
    /**
     * Creates a ModelError with status and parameters.
     *
     * @param status the status code
     * @param params template parameters for message rendering
     */
    public ModelError(StatusCode status, Map<String, Object> params) { super(status, params); }
    /**
     * Creates a ModelError with status only.
     *
     * @param status the status code
     */
    public ModelError(StatusCode status) { super(status); }
}
