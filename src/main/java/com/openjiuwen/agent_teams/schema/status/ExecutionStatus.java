/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema.status;

/**
 * Minimal execution status enum.
 *
 * <p>Mirrors Python's {@code ExecutionStatus} in
 * {@code openjiuwen.agent_teams.schema.status}.
 */
public enum ExecutionStatus {
    IDLE("idle"),
    STARTING("starting"),
    RUNNING("running"),
    CANCEL_REQUESTED("cancel_requested"),
    CANCELLING("cancelling"),
    CANCELLED("cancelled"),
    COMPLETING("completing"),
    COMPLETED("completed"),
    FAILED("failed"),
    TIMED_OUT("timed_out");

    private final String value;

    ExecutionStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ExecutionStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ExecutionStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown execution status: " + value);
    }
}
