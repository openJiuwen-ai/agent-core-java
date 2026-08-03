/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Mirrors Python's {@code PermissionLevel} in
 * {@code openjiuwen/harness/security/models.py}.
 */
public enum PermissionLevel {
    ALLOW("allow"),
    ASK("ask"),
    DENY("deny");

    private final String value;

    PermissionLevel(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static PermissionLevel fromValue(String value) {
        if (value == null || value.isBlank()) {
            return ASK;
        }
        for (PermissionLevel level : values()) {
            if (level.value.equalsIgnoreCase(value)) {
                return level;
            }
        }
        return ASK;
    }
}
