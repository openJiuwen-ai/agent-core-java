/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Configuration error — a specialized {@link FrameworkError}.
 */
public class ConfigurationError extends FrameworkError {

    public ConfigurationError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }

    public ConfigurationError(StatusCode status, Map<String, Object> params) {
        super(status, params);
    }

    public ConfigurationError(StatusCode status) {
        super(status);
    }
}
