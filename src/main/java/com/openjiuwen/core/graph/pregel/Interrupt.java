/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

/**
 * Mirrors Python's {@code Interrupt} in
 * {@code openjiuwen/core/graph/pregel/base.py}.
 */
public class Interrupt {

    private final Object value;

    public Interrupt(Object value) {
        this.value = value;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
