/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Mirrors Python's {@code HookType} in
 * {@code openjiuwen/core/runner/callback/enums.py}.
 */
public enum HookType {
    BEFORE("before"),
    AFTER("after"),
    ERROR("error"),
    CLEANUP("cleanup");

    private final String value;

    HookType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
