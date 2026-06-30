/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.status;

/**
 * Detailed execution lifecycle states for the narrow Java stream-controller slice.
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public String value() {
        return value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean canTransitionTo(ExecutionStatus next) {
        if (next == null) {
            return false;
        }
        return switch (this) {
            case IDLE -> next == STARTING;
            case STARTING -> next == RUNNING
                    || next == CANCEL_REQUESTED
                    || next == CANCELLING
                    || next == FAILED
                    || next == TIMED_OUT;
            case RUNNING -> next == CANCEL_REQUESTED
                    || next == CANCELLING
                    || next == COMPLETING
                    || next == FAILED
                    || next == TIMED_OUT;
            case CANCEL_REQUESTED -> next == CANCELLING
                    || next == CANCELLED
                    || next == FAILED
                    || next == TIMED_OUT;
            case CANCELLING -> next == CANCELLED || next == FAILED || next == TIMED_OUT;
            case CANCELLED, COMPLETED, FAILED, TIMED_OUT -> next == IDLE;
            case COMPLETING -> next == COMPLETED || next == FAILED || next == TIMED_OUT;
        };
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static ExecutionStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return IDLE;
        }
        for (ExecutionStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown execution status: " + value);
    }
}
