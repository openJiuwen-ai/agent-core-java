// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.logging;

/**
 * 模块类型枚举
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

    public String getValue() {
        return value;
    }
}