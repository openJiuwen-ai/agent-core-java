/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.constants;

/**
 * Mirrors Python's {@code TaskType} in
 * {@code openjiuwen/core/common/constants/enums.py}.
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

    public static TaskType fromValue(String value) {
        for (TaskType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return UNDEFINED;
    }
}
