/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Actions that callbacks can return to control chain execution.
 */
public enum ChainAction {
    /** Continue to next callback in chain. */
    CONTINUE("continue"),
    /** Break the chain and return current result. */
    BREAK("break"),
    /** Retry current callback. */
    RETRY("retry"),
    /** Rollback all executed callbacks. */
    ROLLBACK("rollback");

    private final String value;

    ChainAction(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
