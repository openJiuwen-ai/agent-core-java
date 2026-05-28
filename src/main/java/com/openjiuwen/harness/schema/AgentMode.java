/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

/**
 * Agent mode enum for DeepAgent.
 *
 * <p>DeepAgent operation modes:
 * - PLAN: Read-only planning mode — LLM explores codebase and writes a
 *   plan file before any modifications are made.
 * - NORMAL: Normal execution mode (default).
 *
 * <p>Mirrors Python's {@code AgentMode} in
 * {@code openjiuwen.harness.schema.agent_mode}.
 */
public enum AgentMode {

    /** Read-only planning mode. */
    PLAN("plan"),

    /** Normal execution mode. */
    NORMAL("normal");

    private final String value;

    AgentMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse from string value.
     */
    public static AgentMode fromValue(String value) {
        if (value == null || value.isEmpty()) {
            return NORMAL;
        }
        for (AgentMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        return NORMAL;
    }
}