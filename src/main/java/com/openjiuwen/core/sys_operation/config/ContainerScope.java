/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Container creation granularity.
 * <p>
 * Mirrors Python's {@code ContainerScope} in
 * {@code openjiuwen/core/sys_operation/config.py}.
 */
public enum ContainerScope {
    SYSTEM("system"),
    SESSION("session"),
    CUSTOM("custom");

    private final String value;

    ContainerScope(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static ContainerScope fromValue(String value) {
        if (value == null) {
            return SESSION;
        }
        for (ContainerScope scope : values()) {
            if (scope.value.equalsIgnoreCase(value)) {
                return scope;
            }
        }
        return SESSION;
    }
}
