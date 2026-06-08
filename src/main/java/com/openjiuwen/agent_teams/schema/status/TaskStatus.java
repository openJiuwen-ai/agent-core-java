/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema.status;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Task status enum.
 * <p>
 * Mirrors Python's {@code TaskStatus} in
 * {@code openjiuwen/agent_teams/schema/status.py}.
 */
public enum TaskStatus {
    PENDING("pending"),
    CLAIMED("claimed"),
    PLAN_APPROVED("plan_approved"),
    COMPLETED("completed"),
    CANCELLED("cancelled"),
    BLOCKED("blocked");

    private final String value;

    TaskStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static TaskStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (TaskStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown task status: " + value);
    }
}
