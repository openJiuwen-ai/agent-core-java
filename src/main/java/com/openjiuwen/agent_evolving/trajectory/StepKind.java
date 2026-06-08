/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

/**
 * Mirrors Python's {@code StepKind} in
 * {@code openjiuwen/agent_evolving/trajectory/types.py}.
 */
public enum StepKind {
    LLM("llm"),
    TOOL("tool"),
    MEMORY("memory"),
    WORKFLOW("workflow"),
    AGENT("agent");

    private final String value;

    StepKind(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static StepKind fromValue(String value) {
        if (value == null || value.isBlank()) {
            return AGENT;
        }
        if ("plugin".equalsIgnoreCase(value)) {
            return TOOL;
        }
        for (StepKind kind : values()) {
            if (kind.value.equalsIgnoreCase(value)) {
                return kind;
            }
        }
        return AGENT;
    }
}
