// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.schema;

/**
 * Execution status enum - detailed status for task execution.
 * 
 * Mirrors Python's agent_teams.schema.status.ExecutionStatus
 * 
 * States:
 *     IDLE: Not executing any task
 *     STARTING: Task execution is starting
 *     RUNNING: Task is actively running
 *     CANCEL_REQUESTED: Cancellation has been requested
 *     CANCELLING: Task is being cancelled
 *     CANCELLED: Task was cancelled
 *     COMPLETING: Task is completing
 *     COMPLETED: Task completed successfully
 *     FAILED: Task failed
 *     TIMED_OUT: Task timed out
 * 
 * @since 0.1.12
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
     * Get the string value of this status.
     * 
     * @return The string representation
     */
    public String getValue() {
        return value;
    }

    /**
     * Parse a string to an ExecutionStatus.
     * 
     * @param value The string value to parse
     * @return The corresponding ExecutionStatus, or null if not found
     */
    public static ExecutionStatus fromValue(String value) {
        for (ExecutionStatus status : ExecutionStatus.values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        return null;
    }
}