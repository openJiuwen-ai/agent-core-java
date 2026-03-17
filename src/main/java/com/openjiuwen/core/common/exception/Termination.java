/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Non-error control-flow termination.
 * Used for normal stop, cancellation, completion, etc.
 */
public class Termination extends BaseError {
    public Termination(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }
    public Termination(StatusCode status, Map<String, Object> params) { super(status, params); }
    public Termination(StatusCode status) { super(status); }

    @Override protected boolean defaultRecoverable() { return false; }
    @Override protected boolean defaultFatal() { return false; }
}
