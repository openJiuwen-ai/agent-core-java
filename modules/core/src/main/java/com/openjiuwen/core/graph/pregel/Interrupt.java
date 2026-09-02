/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.io.Serial;
import java.io.Serializable;

/**
 * Represents an interrupt value during graph execution.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.base.Interrupt}.
 * 
 * @since 0.1.7
 */
public class Interrupt implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Object value;

    /**
     * Interrupt.
     * 
     * @param value value
     * @since 0.1.7
     */
    public Interrupt(Object value) {
        this.value = value;
    }

    /**
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getValue() {
        return value;
    }

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        return "Interrupt{value=" + value + '}';
    }
}
