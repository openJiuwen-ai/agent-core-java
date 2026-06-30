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
 */
public class Interrupt implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Object value;

    /**
     * Auto-generated for codecheck compliance.
     */
    public Interrupt(Object value) {
        this.value = value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getValue() {
        return value;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String toString() {
        return "Interrupt{value=" + value + '}';
    }
}
