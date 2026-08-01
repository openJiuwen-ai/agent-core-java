/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

/**
 * Public enum DeepLoopEventType used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public enum DeepLoopEventType {
    FOLLOWUP("followup"),
    STEER("steer"),
    ABORT("abort");

    private final String value;

    DeepLoopEventType(String value) {
        this.value = value;
    }

    /**
     * value.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String value() {
        return value;
    }
}
