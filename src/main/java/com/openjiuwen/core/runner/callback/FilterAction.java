// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.runner.callback;

/**
 * Actions that filters can return to control callback execution.
 */
public enum FilterAction {
    /** Continue with callback execution. */
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

    public String getValue() {
        return value;
    }
}
