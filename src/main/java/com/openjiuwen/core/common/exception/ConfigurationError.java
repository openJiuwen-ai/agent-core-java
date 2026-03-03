// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.exception;

/**
 * 配置错误
 */
public class ConfigurationError extends FrameworkError {

    public ConfigurationError(StatusCode status) {
        super(status);
    }

    public ConfigurationError(StatusCode status, String message) {
        super(status, message);
    }

    public ConfigurationError(StatusCode status, Object details, Throwable cause) {
        super(status, details, cause);
    }

    public ConfigurationError(StatusCode status, String message, Object details, Throwable cause) {
        super(status, message, details, cause);
    }
}