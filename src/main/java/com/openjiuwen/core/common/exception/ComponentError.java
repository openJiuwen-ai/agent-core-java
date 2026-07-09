/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Component execution error.
 * 
 * @since 0.1.7
 */
public class ComponentError extends ExecutionError {
    /**
     * ComponentError.
     * 
     * @param status status
     * @param msg msg
     * @param details details
     * @param cause cause
     * @param params params
     * @since 0.1.7
     */
    public ComponentError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }

    /**
     * Creates a ComponentError with status and parameters.
     * 
     * @param status the status code
     * @param params template parameters for message rendering
     * @since 0.1.7
     */
    public ComponentError(StatusCode status, Map<String, Object> params) {
        super(status, params);
    }

    /**
     * Creates a ComponentError with status only.
     * 
     * @param status the status code
     * @since 0.1.7
     */
    public ComponentError(StatusCode status) {
        super(status);
    }
}
