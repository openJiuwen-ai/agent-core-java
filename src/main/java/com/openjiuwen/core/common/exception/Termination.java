// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.exception;

/**
 * 终止异常
 *
 * <p>非错误控制流终止。
 * 用于正常停止、取消、完成等。</p>
 */
public class Termination extends BaseError {

    public Termination(StatusCode status) {
        super(status);
    }

    public Termination(StatusCode status, String message) {
        super(status, message);
    }

    public Termination(StatusCode status, Object details, Throwable cause) {
        super(status, details, cause);
    }

    public Termination(StatusCode status, String message, Object details, Throwable cause) {
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