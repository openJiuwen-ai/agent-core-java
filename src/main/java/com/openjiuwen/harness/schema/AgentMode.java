/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * DeepAgent operation mode.
 *
 * <p>Mirrors Python's {@code AgentMode} in
 * {@code openjiuwen/harness/schema/agent_mode.py}.
 */
public enum AgentMode {
    PLAN("plan"),
    NORMAL("normal");

    private final String value;

    AgentMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static AgentMode fromValue(String value) {
        if (value == null || value.isBlank()) {
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
