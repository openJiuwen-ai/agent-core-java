/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.common.constants;

/**
 * Task type enumeration.
 *
 * <p>Defines the supported task types for agent execution.</p>
 */
public enum TaskType {

    PLUGIN("plugin"),
    WORKFLOW("workflow"),
    MCP("mcp"),
    UNDEFINED("undefined");

    private final String value;

    TaskType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse a string value into the corresponding {@link TaskType}.
     *
     * @param value the string representation
     * @return the matching enum constant, or {@link #UNDEFINED} if no match
     */
    public static TaskType fromValue(String value) {
        for (TaskType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return UNDEFINED;
    }
}
