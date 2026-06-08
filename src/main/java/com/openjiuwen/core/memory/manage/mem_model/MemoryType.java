/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

/**
 * Memory-type enumeration.
 *
 * <p>Mirrors Python's {@code MemoryType} in
 * {@code openjiuwen/core/memory/manage/mem_model/memory_unit.py}.</p>
 */
public enum MemoryType {
    USER_PROFILE("user_profile"),
    SEMANTIC_MEMORY("semantic_memory"),
    EPISODIC_MEMORY("episodic_memory"),
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
