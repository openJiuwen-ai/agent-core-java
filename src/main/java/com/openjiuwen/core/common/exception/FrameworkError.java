// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Infrastructure / environment / dependency failures
 * 
 * <p>Must abort current execution.
 * 
 * @since 0.1.4
 */
public class FrameworkError extends BaseError {
    
    public FrameworkError(StatusCode status) {
        super(status);
        this.recoverable = false;
        this.fatal = true;
    }
    
    public FrameworkError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
        this.recoverable = false;
        this.fatal = true;
    }
}

