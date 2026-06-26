/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Alert severity levels for security alerts.
 *
 * <p>Mirrors Python's {@code SecurityAlertLevel} in
 * {@code openjiuwen/harness/rails/security/base_security_rail.py}.</p>
 */
public enum SecurityAlertLevel {
    INFO("info"),
    WARNING("warning"),
    ERROR("error"),
    CRITICAL("critical");

    private final String value;

    SecurityAlertLevel(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static SecurityAlertLevel fromValue(String value) {
        if (value == null || value.isBlank()) {
            return WARNING;
        }
        for (SecurityAlertLevel level : values()) {
            if (level.value.equalsIgnoreCase(value)) {
                return level;
            }
        }
        return WARNING;
    }
}
