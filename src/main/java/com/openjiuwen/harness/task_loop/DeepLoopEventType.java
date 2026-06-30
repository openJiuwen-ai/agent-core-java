/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

/**
 * Public enum DeepLoopEventType used by the Java parity implementation.
 *
 * @since 1.0
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
     * Auto-generated for codecheck compliance.
     */
    public String value() {
        return value;
    }
}
