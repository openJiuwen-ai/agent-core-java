/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.update;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status of a memory action.
 *
 * <p>Mirrors Python's {@code MemoryStatus} in
 * {@code openjiuwen/core/memory/manage/update/mem_update_checker.py}.</p>
 */
public enum MemoryStatus {
    ADD("add"),
    DELETE("delete");

    private final String value;

    MemoryStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static MemoryStatus fromValue(String value) {
        for (MemoryStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown memory status: " + value);
    }
}
