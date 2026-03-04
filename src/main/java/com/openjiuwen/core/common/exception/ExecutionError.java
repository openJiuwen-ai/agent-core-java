// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.exception;

/**
 * 执行错误
 *
 * <p>工作流/Agent/工具执行时的运行时错误。
 * 通常可以通过重试/重新规划恢复。</p>
 */
public class ExecutionError extends BaseError {

    public ExecutionError(StatusCode status) {
        super(status);
    }

    public ExecutionError(StatusCode status, String message) {
        super(status, message);
    }

    public ExecutionError(StatusCode status, Object details, Throwable cause) {
        super(status, details, cause);
    }

    public ExecutionError(StatusCode status, String message, Object details, Throwable cause) {
        super(status, message, details, cause);
    }

    @Override
    public boolean isRecoverable() {
        return true;
    }

    @Override
    public boolean isFatal() {
        return false;
    }
}