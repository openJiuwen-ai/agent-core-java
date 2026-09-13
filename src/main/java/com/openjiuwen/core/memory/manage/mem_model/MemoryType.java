/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

/**
 * Types of memory data.
 * 
 * @since 0.1.7
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
    public static MemoryType fromValue(String value) {
        for (MemoryType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
