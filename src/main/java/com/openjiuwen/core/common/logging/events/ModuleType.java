/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.events;

/**
 * Module type enumeration.
 * 
 * @since 0.1.7
 */
public enum ModuleType {
    AGENT("agent"),
    WORKFLOW("workflow"),
    WORKFLOW_COMPONENT("workflow_component"),
    LLM("llm"),
    TOOL("tool"),
    STORE("store"),
    MEMORY("memory"),
    SESSION("session"),
    CONTEXT("context"),
    RETRIEVAL("retrieval"),
    SYSTEM("system"),
    USER("user"),
    SYS_OPERATION("sys_operation");

    private final String value;

    ModuleType(String value) {
        this.value = value;
    }

    /**
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getValue() {
        return value;
    }
}
