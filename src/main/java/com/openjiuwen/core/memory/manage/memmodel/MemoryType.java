/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

/**
 * Memory type enumeration.
 * Corresponds to Python: manage/mem_model/memory_unit.py - MemoryType
 */
public enum MemoryType {
    USER_PROFILE("user_profile"),
    VARIABLE("variable"),
    IMPLICIT_USER_PROFILE("implicit_user_profile"),
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

