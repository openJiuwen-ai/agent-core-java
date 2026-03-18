/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Infrastructure / environment / dependency failures.
 * Must abort current execution.
 */
public class FrameworkError extends BaseError {

    public FrameworkError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }

    public FrameworkError(StatusCode status, Map<String, Object> params) {
        super(status, params);
    }

    public FrameworkError(StatusCode status) {
        super(status);
    }

    @Override
    protected boolean defaultRecoverable() {
        return false;
    }

    @Override
    protected boolean defaultFatal() {
        return true;
    }
}
