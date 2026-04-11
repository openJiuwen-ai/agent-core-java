/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.schema;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.core.schema.message.Role}.
 * 
 * Message role enumeration.
 */
public enum Role {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool");
    
    private final String value;
    
    Role(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static Role fromValue(String value) {
        for (Role role : values()) {
            if (role.value.equalsIgnoreCase(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }
}