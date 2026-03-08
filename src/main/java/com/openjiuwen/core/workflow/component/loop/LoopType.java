/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow.component.loop;

/**
 * Types of loop conditions.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.loop.loop_comp.LoopType}.
 */
public enum LoopType {
    ARRAY("array"),
    NUMBER("number"),
    ALWAYS_TRUE("always_true"),
    EXPRESSION("expression");

    private final String value;

    LoopType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static LoopType fromValue(String value) {
        for (LoopType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
