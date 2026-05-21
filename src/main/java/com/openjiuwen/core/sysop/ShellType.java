/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

/**
 * Shell type enumeration.
 * <p>
 * Mirrors Python's {@code ShellType} enum from
 * {@code core/sys_operation/shell.py}.
 */
public enum ShellType {
    AUTO("auto"),
    CMD("cmd"),
    POWERSHELL("powershell"),
    BASH("bash"),
    SH("sh");

    private final String value;

    ShellType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse shell type from string, defaults to AUTO.
     */
    public static ShellType fromString(String value) {
        if (value == null) {
            return AUTO;
        }
        try {
            return valueOf(value.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return AUTO;
        }
    }
}
