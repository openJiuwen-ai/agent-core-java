// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.exception;

/**
 * 验证错误
 *
 * <p>约束/验证/不支持能力的错误。
 * 不应重试或重新规划。</p>
 */
public class ValidationError extends BaseError {

    public ValidationError(StatusCode status) {
        super(status);
    }

    public ValidationError(StatusCode status, String message) {
        super(status, message);
    }

    public ValidationError(StatusCode status, Object details, Throwable cause) {
        super(status, details, cause);
    }

    public ValidationError(StatusCode status, String message, Object details, Throwable cause) {
        super(status, message, details, cause);
    }

    @Override
    public boolean isRecoverable() {
        return false;
    }

    @Override
    public boolean isFatal() {
        return false;
    }
}