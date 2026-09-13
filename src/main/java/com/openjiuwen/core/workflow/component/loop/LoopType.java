/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

/**
 * Types of loop conditions.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.loop.loop_comp.LoopType}.
 * 
 * @since 0.1.7
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

    /**
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getValue() {
        return value;
    }

    /**
     * fromValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static LoopType fromValue(String value) {
        for (LoopType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
