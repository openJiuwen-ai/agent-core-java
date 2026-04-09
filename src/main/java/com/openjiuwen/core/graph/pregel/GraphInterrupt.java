/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

/**
 * Exception thrown when a graph execution is interrupted.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.base.GraphInterrupt}.
 */
public class GraphInterrupt extends Exception {

    private final Interrupt value;

    public GraphInterrupt() {
        this(null);
    }

    public GraphInterrupt(Interrupt value) {
        super(value != null ? value.toString() : "GraphInterrupt");
        this.value = value;
    }

    public Interrupt getValue() {
        return value;
    }
}
