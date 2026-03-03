// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.exception;

/**
 * 外部数据错误
 */
public class ExternalDataError extends ExecutionError {

    public ExternalDataError(StatusCode status) {
        super(status);
    }

    public ExternalDataError(StatusCode status, String message) {
        super(status, message);
    }

    public ExternalDataError(StatusCode status, Object details, Throwable cause) {
        super(status, details, cause);
    }

    public ExternalDataError(StatusCode status, String message, Object details, Throwable cause) {
        super(status, message, details, cause);
    }
}