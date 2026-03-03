// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.exception;

/**
 * 应用错误
 */
public class ApplicationError extends ExecutionError {

    public ApplicationError(StatusCode status) {
        super(status);
    }

    public ApplicationError(StatusCode status, String message) {
        super(status, message);
    }

    public ApplicationError(StatusCode status, Object details, Throwable cause) {
        super(status, details, cause);
    }

    public ApplicationError(StatusCode status, String message, Object details, Throwable cause) {
        super(status, message, details, cause);
    }
}