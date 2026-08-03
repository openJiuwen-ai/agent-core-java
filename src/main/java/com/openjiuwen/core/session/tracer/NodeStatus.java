/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

/**
 * Mirrors Python's {@code NodeStatus} in
 * {@code openjiuwen/core/session/tracer/data.py}.
 */
public enum NodeStatus {
    START("start"),
    FINISH("finish"),
    RUNNING("running"),
    INTERRUPTED("interrupted"),
    ERROR("error");

    private final String value;

    NodeStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
