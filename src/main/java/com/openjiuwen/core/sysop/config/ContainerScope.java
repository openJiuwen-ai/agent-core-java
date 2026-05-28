/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.config;

/**
 * Container creation granularity.
 * <p>
 * Determines how the ContainerManager routes requests to container instances.
 * <p>
 * Mirrors Python's {@code ContainerScope} enum in {@code sys_operation/config.py}.
 */
public enum ContainerScope {
    /** System-wide: all requests share one container. */
    SYSTEM("system"),
    
    /** Session-level: requests with same session_id share one container. */
    SESSION("session"),
    
    /** Custom-level: uses context.id directly as the key. */
    CUSTOM("custom");

    private final String value;

    ContainerScope(String value) {
        this.value = value;
    }

    /**
     * Get the string value of this scope.
     *
     * @return the scope string value
     */
    public String getValue() {
        return value;
    }

    /**
     * Parse a string value to ContainerScope.
     *
     * @param value the string value
     * @return the matching ContainerScope, or SESSION as default
     */
    public static ContainerScope fromValue(String value) {
        if (value == null) {
            return SESSION;
        }
        for (ContainerScope scope : values()) {
            if (scope.value.equals(value)) {
                return scope;
            }
        }
        return SESSION;
    }
}