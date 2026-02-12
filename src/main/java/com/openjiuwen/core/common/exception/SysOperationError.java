// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * System operation error
 * 
 * @since 0.1.4
 */
public class SysOperationError extends ExecutionError {
    
    public SysOperationError(StatusCode status) {
        super(status);
    }
    
    public SysOperationError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }
}

