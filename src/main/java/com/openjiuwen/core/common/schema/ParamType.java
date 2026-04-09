/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.schema;

/**
 * Parameter type enumeration.
 */
public enum ParamType {
    STRING("string"),
    BOOLEAN("boolean"),
    INTEGER("integer"),
    NUMBER("number"),
    ARRAY("array"),
    OBJECT("object");

    private final String value;

    ParamType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ParamType fromValue(String value) {
        for (ParamType t : values()) {
            if (t.value.equalsIgnoreCase(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown ParamType: " + value);
    }
}
