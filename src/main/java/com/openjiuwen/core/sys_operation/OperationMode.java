/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum for operation mode.
 *
 * <p>Mirrors Python's {@code OperationMode} in
 * {@code openjiuwen/core/sys_operation/base.py}.</p>
 */
public enum OperationMode {
    LOCAL("local"),
    SANDBOX("sandbox");

    private final String value;

    OperationMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static OperationMode fromValue(String value) {
        if (value == null) {
            return LOCAL;
        }
        for (OperationMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        return LOCAL;
    }

    @Override
    public String toString() {
        return value;
    }
}
