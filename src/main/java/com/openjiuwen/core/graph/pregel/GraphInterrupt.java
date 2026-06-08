/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

/**
 * Mirrors Python's {@code GraphInterrupt} in
 * {@code openjiuwen/core/graph/pregel/base.py}.
 */
public class GraphInterrupt extends Exception {

    private final Object value;

    public GraphInterrupt() {
        this(null);
    }

    public GraphInterrupt(Object value) {
        super(String.valueOf(value));
        this.value = value;
    }

    public Object getValue() {
        return value;
    }
}
