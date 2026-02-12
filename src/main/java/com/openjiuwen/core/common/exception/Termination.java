// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Non-error control-flow termination
 * 
 * <p>Used for normal stop, cancellation, completion, etc.
 * 
 * @since 0.1.4
 */
public class Termination extends BaseError {
    
    public Termination(StatusCode status) {
        super(status);
        this.recoverable = false;
        this.fatal = false;
    }
    
    public Termination(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
        this.recoverable = false;
        this.fatal = false;
    }
}

