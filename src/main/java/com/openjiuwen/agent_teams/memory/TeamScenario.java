/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.memory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Team memory scenario literal values.
 *
 * <p>Mirrors Python's {@code TeamScenario} in
 * {@code openjiuwen/agent_teams/memory/manager_params.py}.</p>
 */
public enum TeamScenario {
    GENERAL("general"),
    CODING("coding");

    private final String value;

    TeamScenario(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static TeamScenario fromValue(String value) {
        for (TeamScenario scenario : values()) {
            if (scenario.value.equals(value)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown team scenario: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
