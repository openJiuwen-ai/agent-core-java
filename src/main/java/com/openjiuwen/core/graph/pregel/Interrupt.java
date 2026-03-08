/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.pregel;

/**
 * Represents an interrupt value during graph execution.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.base.Interrupt}.
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
        return "Interrupt{value=" + value + '}';
    }
}
