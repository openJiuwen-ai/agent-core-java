/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.memory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Team memory lifecycle literal values.
 *
 * <p>Mirrors Python's {@code TeamLifecycle} in
 * {@code openjiuwen/agent_teams/memory/manager_params.py}.</p>
 */
public enum TeamLifecycle {
    TEMPORARY("temporary"),
    PERSISTENT("persistent");

    private final String value;

    TeamLifecycle(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static TeamLifecycle fromValue(String value) {
        for (TeamLifecycle lifecycle : values()) {
            if (lifecycle.value.equals(value)) {
                return lifecycle;
            }
        }
        throw new IllegalArgumentException("Unknown team lifecycle: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
