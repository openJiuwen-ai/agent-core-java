// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.constants;

/**
 * Task type enumeration
 * 
 * <p>Defines the types of tasks that can be executed in the system.
 * 
 * @since 0.1.4
 */
public enum TaskType {
    
    /** Plugin task type */
    PLUGIN("plugin"),
    
    /** Workflow task type */
    WORKFLOW("workflow"),
    
    /** MCP (Model Context Protocol) task type */
    MCP("mcp"),
    
    /** Undefined task type */
    UNDEFINED("undefined");
    
    private final String value;
    
    TaskType(String value) {
        this.value = value;
    }
    
    /**
     * Gets the string value of this task type
     * 
     * @return the string value
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Gets TaskType from string value
     * 
     * @param value the string value
     * @return the corresponding TaskType, or UNDEFINED if not found
     */
    public static TaskType fromValue(String value) {
        if (value == null) {
            return UNDEFINED;
        }
        for (TaskType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return UNDEFINED;
    }
    
    @Override
    public String toString() {
        return value;
    }
}

