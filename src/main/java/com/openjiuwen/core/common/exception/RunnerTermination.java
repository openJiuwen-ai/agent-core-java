// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.exception;

/**
 * Runner 终止
 */
public class RunnerTermination extends Termination {

    private final String reason;

    public RunnerTermination(String reason, StatusCode status) {
        super(status);
        this.reason = reason;
    }

    public RunnerTermination(String reason, StatusCode status, String message) {
        super(status, message);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}