/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema.status;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Member mode enum.
 * <p>
 * Mirrors Python's {@code MemberMode} in
 * {@code openjiuwen/agent_teams/schema/status.py}.
 */
public enum MemberMode {
    BUILD_MODE("build_mode"),
    PLAN_MODE("plan_mode");

    private final String value;

    MemberMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static MemberMode fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (MemberMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown member mode: " + value);
    }
}
