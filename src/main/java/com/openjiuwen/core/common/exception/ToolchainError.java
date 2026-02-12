// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Toolchain error
 * 
 * @since 0.1.4
 */
public class ToolchainError extends ExecutionError {
    
    public ToolchainError(StatusCode status) {
        super(status);
    }
    
    public ToolchainError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }
}

