/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

/**
 * Backward-compatible enum for the moved system operation mode type.
 *
 * <p>Mirrors Python's {@code OperationMode} in
 * {@code openjiuwen/core/sys_operation/base.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.OperationMode}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public enum OperationMode {
    LOCAL("local"),
    SANDBOX("sandbox");

    private final String value;

    OperationMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public String value() {
        return value;
    }

    public com.openjiuwen.core.sys_operation.OperationMode toNewMode() {
        return com.openjiuwen.core.sys_operation.OperationMode.fromValue(value);
    }

    public static OperationMode fromNewMode(com.openjiuwen.core.sys_operation.OperationMode mode) {
        if (mode == null) {
            return LOCAL;
        }
        return fromString(mode.value());
    }

    public static OperationMode fromString(String text) {
        if (text == null) {
            return LOCAL;
        }
        for (OperationMode mode : values()) {
            if (mode.value.equalsIgnoreCase(text.trim())) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown operation mode: " + text);
    }

    @Override
    public String toString() {
        return value;
    }
}
