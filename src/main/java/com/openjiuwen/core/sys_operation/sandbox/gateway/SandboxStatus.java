/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.sandbox.gateway;

/**
 * Mirrors Python's {@code SandboxStatus} in
 * {@code openjiuwen/core/sys_operation/sandbox/gateway/sandbox_store.py}.
 */
public enum SandboxStatus {
    RUNNING("running"),
    PAUSED("paused"),
    KILLED("killed");

    private final String value;

    SandboxStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
