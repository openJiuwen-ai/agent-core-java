/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

import java.util.Locale;

/**
 * Shell selection enum aligned with Python's shell.py.
 * 
 * @since 0.1.7
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
     * fromString.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static ShellType fromString(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ShellType shellType : values()) {
            if (shellType.value.equals(normalized)) {
                return shellType;
            }
        }
        return AUTO;
    }
}
