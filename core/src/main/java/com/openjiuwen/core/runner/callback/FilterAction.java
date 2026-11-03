/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Mirrors Python's {@code FilterAction} in
 * {@code openjiuwen/core/runner/callback/enums.py}.
 */
public enum FilterAction {
    CONTINUE("continue"),
    STOP("stop"),
    SKIP("skip"),
    MODIFY("modify");

    private final String value;

    FilterAction(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
