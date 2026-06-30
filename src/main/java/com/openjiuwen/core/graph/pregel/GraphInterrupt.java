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

    /**
     * Auto-generated for codecheck compliance.
     */
    public GraphInterrupt() {
        this(null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public GraphInterrupt(Interrupt value) {
        super(value != null ? value.toString() : "GraphInterrupt");
        this.value = value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Interrupt getValue() {
        return value;
    }
}
