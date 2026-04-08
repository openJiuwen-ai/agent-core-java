/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

/**
 * Types of memory data.
 */
public enum MemoryType {
    FRAGMENT_MEMORY("fragment"),
    VARIABLE("variable"),
    SUMMARY("summary"),
    UNKNOWN("unknown");

    private final String value;

    MemoryType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MemoryType fromValue(String value) {
        for (MemoryType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
