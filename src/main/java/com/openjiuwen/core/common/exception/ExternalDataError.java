// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * External data error
 * 
 * @since 0.1.4
 */
public class ExternalDataError extends ExecutionError {
    
    public ExternalDataError(StatusCode status) {
        super(status);
    }
    
    public ExternalDataError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }
}

