/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.update;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Result of a memory check operation.
 *
 * <p>Mirrors Python's {@code CheckResult} in
 * {@code openjiuwen/core/memory/manage/update/mem_update_checker.py}.</p>
 */
public enum CheckResult {
    REDUNDANT("redundant"),
    CONFLICTING("conflicting"),
    NONE("none");

    private final String value;

    CheckResult(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static CheckResult fromValue(String value) {
        for (CheckResult result : values()) {
            if (result.value.equals(value)) {
                return result;
            }
        }
        throw new IllegalArgumentException("Unknown check result: " + value);
    }
}
