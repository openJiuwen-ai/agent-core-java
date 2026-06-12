/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.memory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Team memory prompt language literal values.
 *
 * <p>Mirrors Python's {@code TeamLanguage} in
 * {@code openjiuwen/agent_teams/memory/manager_params.py}.</p>
 */
public enum TeamLanguage {
    CN("cn"),
    EN("en");

    private final String value;

    TeamLanguage(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static TeamLanguage fromValue(String value) {
        for (TeamLanguage language : values()) {
            if (language.value.equals(value)) {
                return language;
            }
        }
        throw new IllegalArgumentException("Unknown team language: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
