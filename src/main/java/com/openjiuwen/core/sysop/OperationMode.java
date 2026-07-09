/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

/**
 * Enum for operation mode.
 * <p>
 * Mirrors Python's {@code OperationMode} enum in {@code sys_operation/base.py}.
 * 
 * @since 0.1.7
 */
public enum OperationMode {
    LOCAL("local"),
    SANDBOX("sandbox");

    private final String value;

    OperationMode(String value) {
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
     * Parse a string value to OperationMode (case-insensitive).
     * 
     * @param text the string to parse
     * @return the corresponding OperationMode
     * @since 0.1.7
     */
    public static OperationMode fromString(String text) {
        for (OperationMode mode : values()) {
            if (mode.value.equalsIgnoreCase(text)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown operation mode: " + text);
    }
}
