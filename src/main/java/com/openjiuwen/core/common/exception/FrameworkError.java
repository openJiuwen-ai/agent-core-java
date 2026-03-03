// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.exception;

/**
 * 基础设施/环境/依赖失败
 *
 * <p>必须中止当前执行。</p>
 */
public class FrameworkError extends BaseError {

    public FrameworkError(StatusCode status) {
        super(status);
    }

    public FrameworkError(StatusCode status, String message) {
        super(status, message);
    }

    public FrameworkError(StatusCode status, Object details, Throwable cause) {
        super(status, details, cause);
    }

    public FrameworkError(StatusCode status, String message, Object details, Throwable cause) {
        super(status, message, details, cause);
    }

    @Override
    public boolean isRecoverable() {
        return false;
    }

    @Override
    public boolean isFatal() {
        return true;
    }
}