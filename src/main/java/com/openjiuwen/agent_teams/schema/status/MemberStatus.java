/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema.status;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Member status enum.
 * <p>
 * Mirrors Python's {@code MemberStatus} in
 * {@code openjiuwen/agent_teams/schema/status.py}.
 */
public enum MemberStatus {
    UNSTARTED("unstarted"),
    STARTING("starting"),
    READY("ready"),
    BUSY("busy"),
    PAUSED("paused"),
    STOPPED("stopped"),
    RESTARTING("restarting"),
    SHUTDOWN_REQUESTED("shutdown_requested"),
    SHUTDOWN("shut_down"),
    ERROR("error");

    private final String value;

    MemberStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static MemberStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (MemberStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown member status: " + value);
    }
}
