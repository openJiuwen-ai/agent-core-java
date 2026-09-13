/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Actions that callbacks can return to control chain execution.
 * 
 * @since 0.1.7
 */
public enum ChainAction {
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

    /**
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getValue() {
        return value;
    }
}
