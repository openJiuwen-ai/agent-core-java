/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.update;

/**
 * Result of memory check operation.
 */
public enum CheckResult {
    REDUNDANT("redundant"),
    CONFLICTING("conflicting"),
    NONE("none");

    private final String value;

    CheckResult(String value) {
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
    public static CheckResult fromValue(String value) {
        for (CheckResult cr : values()) {
            if (cr.value.equalsIgnoreCase(value)) {
                return cr;
            }
        }
        return NONE;
    }
}
