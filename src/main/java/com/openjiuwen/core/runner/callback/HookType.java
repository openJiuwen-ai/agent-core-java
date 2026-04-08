/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Types of hooks that can be registered for lifecycle events.
 */
public enum HookType {
    /** Executed before event processing. */
    BEFORE("before"),
    /** Executed after event processing. */
    AFTER("after"),
    /** Executed when an error occurs. */
    ERROR("error"),
    /** Executed during cleanup phase. */
    CLEANUP("cleanup");

    private final String value;

    HookType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
