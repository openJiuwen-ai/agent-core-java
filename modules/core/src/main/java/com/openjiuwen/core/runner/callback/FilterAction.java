/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Actions that filters can return to control callback execution.
 * 
 * @since 0.1.7
 */
public enum FilterAction {
    CONTINUE("continue"),
    /** Stop the entire event processing. */
    STOP("stop"),
    /** Skip current callback and continue to next. */
    SKIP("skip"),
    /** Modify arguments and continue. */
    MODIFY("modify");

    private final String value;

    FilterAction(String value) {
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
