// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Execution-time errors during workflow / agent / tool execution
 * 
 * <p>Usually recoverable via retry / replan.
 * 
 * @since 0.1.4
 */
public class ExecutionError extends BaseError {
    
    public ExecutionError(StatusCode status) {
        super(status);
        this.recoverable = true;
        this.fatal = false;
    }
    
    public ExecutionError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
        this.recoverable = true;
        this.fatal = false;
    }
}

