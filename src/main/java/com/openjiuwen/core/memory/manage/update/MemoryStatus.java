/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.memory.manage.update;

/**
 * Status of memory action.
 */
public enum MemoryStatus {
    ADD("add"),
    DELETE("delete");

    private final String value;

    MemoryStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MemoryStatus fromValue(String value) {
        for (MemoryStatus ms : values()) {
            if (ms.value.equalsIgnoreCase(value)) {
                return ms;
            }
        }
        return ADD;
    }
}
