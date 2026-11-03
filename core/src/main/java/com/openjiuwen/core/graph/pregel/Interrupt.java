/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.io.Serial;
import java.io.Serializable;

/**
 * Interrupt value captured when graph execution pauses for user input.
 */
public class Interrupt implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
