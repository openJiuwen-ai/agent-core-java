/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Mirrors Python's {@code ChainAction} in
 * {@code openjiuwen/core/runner/callback/enums.py}.
 */
public enum ChainAction {
    CONTINUE("continue"),
    BREAK("break"),
    RETRY("retry"),
    ROLLBACK("rollback");

    private final String value;

    ChainAction(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
