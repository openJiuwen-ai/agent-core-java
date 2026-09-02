/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

/**
 * Exception thrown when a graph execution is interrupted.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.base.GraphInterrupt}.
 * 
 * @since 0.1.7
 */
public class GraphInterrupt extends Exception {
    private final Interrupt value;

    /**
     * GraphInterrupt.
     * 
     * @since 0.1.7
     */
    public GraphInterrupt() {
        this(null);
    }

    /**
     * GraphInterrupt.
     * 
     * @param value value
     * @since 0.1.7
     */
    public GraphInterrupt(Interrupt value) {
        super(value != null ? value.toString() : "GraphInterrupt");
        this.value = value;
    }

    /**
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Interrupt getValue() {
        return value;
    }
}
