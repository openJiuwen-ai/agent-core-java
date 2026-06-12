/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.memory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Member-memory prompt mode literal values.
 *
 * <p>Mirrors Python's {@code PromptMode} in
 * {@code openjiuwen/agent_teams/memory/manager_params.py}.</p>
 */
public enum PromptMode {
    PROACTIVE("proactive"),
    PASSIVE("passive");

    private final String value;

    PromptMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PromptMode fromValue(String value) {
        for (PromptMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown prompt mode: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
