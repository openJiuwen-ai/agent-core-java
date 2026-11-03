/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Mirrors Python's {@code RunnerTermination} in
 * {@code openjiuwen/core/common/exception/errors.py}.
 */
public class RunnerTermination extends Termination {
    private final String reason;
    public RunnerTermination(String reason, StatusCode status, Map<String, Object> params) {
        super(status, params);
        this.reason = reason;
    }
    public RunnerTermination(String reason, StatusCode status) {
        super(status);
        this.reason = reason;
    }
    public String getReason() {
        return reason;
    }
}
