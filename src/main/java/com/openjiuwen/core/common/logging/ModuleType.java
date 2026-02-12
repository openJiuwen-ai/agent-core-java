// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.logging;

/**
 * Module type enumeration
 * 
 * @since 0.1.4
 */
public enum ModuleType {
    
    AGENT("agent"),
    WORKFLOW("workflow"),
    WORKFLOW_COMPONENT("workflow_component"),
    LLM("llm"),
    TOOL("tool"),
    MEMORY("memory"),
    SESSION("session"),
    CONTEXT("context"),
    RETRIEVAL("retrieval"),
    SYSTEM("system"),
    USER("user");
    
    private final String value;
    
    ModuleType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Get module type from string value
     * 
     * @param value the string value
     * @return the module type
     */
    public static ModuleType fromValue(String value) {
        for (ModuleType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown module type: " + value);
    }
}

