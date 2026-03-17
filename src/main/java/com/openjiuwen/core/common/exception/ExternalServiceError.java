/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.exception;

import java.util.Map;

/** Error from an external service call. */
public class ExternalServiceError extends ExecutionError {
    public ExternalServiceError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }
    public ExternalServiceError(StatusCode status, Map<String, Object> params) { super(status, params); }
    public ExternalServiceError(StatusCode status) { super(status); }
}
