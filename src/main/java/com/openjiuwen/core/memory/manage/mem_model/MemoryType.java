/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

/**
 * Types of memory data.
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getValue() {
        return value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static MemoryType fromValue(String value) {
        for (MemoryType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
