/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

/**
 * Minimal permission levels for Java harness tool checks.
 *
 * <p>Mirrors Python's {@code PermissionLevel} in
 * {@code openjiuwen.harness.security.models}.
 */
public enum PermissionLevel {
    ALLOW("allow"),
    ASK("ask"),
    DENY("deny");

    private final String value;

    PermissionLevel(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse from string value.
     */
    public static PermissionLevel fromValue(String value) {
        if (value == null || value.isEmpty()) {
            return ASK;
        }
        String lower = value.toLowerCase();
        for (PermissionLevel level : values()) {
            if (level.value.equals(lower)) {
                return level;
            }
        }
        return ASK;
    }
}
