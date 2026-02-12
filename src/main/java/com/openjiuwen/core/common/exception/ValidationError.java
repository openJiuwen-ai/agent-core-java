// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Constraint / validation / unsupported capability errors
 * 
 * <p>Should NOT retry or replan.
 * 
 * @since 0.1.4
 */
public class ValidationError extends BaseError {
    
    public ValidationError(StatusCode status) {
        super(status);
        this.recoverable = false;
        this.fatal = false;
    }
    
    public ValidationError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
        this.recoverable = false;
        this.fatal = false;
    }
}

