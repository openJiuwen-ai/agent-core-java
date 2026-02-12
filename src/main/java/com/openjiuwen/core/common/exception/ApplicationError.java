// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Application error
 * 
 * @since 0.1.4
 */
public class ApplicationError extends ExecutionError {
    
    public ApplicationError(StatusCode status) {
        super(status);
    }
    
    public ApplicationError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }
}

