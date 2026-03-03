// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.exception;

/**
 * 外部服务错误
 */
public class ExternalServiceError extends ExecutionError {

    public ExternalServiceError(StatusCode status) {
        super(status);
    }

    public ExternalServiceError(StatusCode status, String message) {
        super(status, message);
    }

    public ExternalServiceError(StatusCode status, Object details, Throwable cause) {
        super(status, details, cause);
    }

    public ExternalServiceError(StatusCode status, String message, Object details, Throwable cause) {
        super(status, message, details, cause);
    }
}