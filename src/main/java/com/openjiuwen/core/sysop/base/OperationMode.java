/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.base;

/**
 * Enum for operation mode.
 *
 * <p>Mirrors Python's {@code OperationMode} in
 * {@code openjiuwen.core.sys_operation.base}.</p>
 */
public enum OperationMode {

    /** Local operation mode - executes directly on local machine. */
    LOCAL("local"),

    /** Sandbox operation mode - executes in sandbox environment. */
    SANDBOX("sandbox");

    private final String value;

    OperationMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static OperationMode fromValue(String value) {
        for (OperationMode mode : OperationMode.values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown operation mode: " + value);
    }
}