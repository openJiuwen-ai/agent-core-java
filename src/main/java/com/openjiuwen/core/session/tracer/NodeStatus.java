  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.session.tracer;

/**
 * Workflow node status for tracing.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.tracer.data.NodeStatus}.
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
