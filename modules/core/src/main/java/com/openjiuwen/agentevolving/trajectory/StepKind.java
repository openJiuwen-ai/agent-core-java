/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

/**
 * Step kind aligned with Python's trajectory StepKind literal values.
 * 
 * @since 0.1.7
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

    /**
     * value.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String value() {
        return value;
    }

    /**
     * fromValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
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
