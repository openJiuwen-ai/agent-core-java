// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.schema;

/**
 * Task status enum for team tasks.
 * 
 * Mirrors Python's agent_teams.schema.status.TaskStatus
 * 
 * States:
 *     PENDING: Task is waiting to be claimed
 *     CLAIMED: Task has been claimed by a member
 *     PLAN_APPROVED: Task plan has been approved (only for PLAN_MODE members)
 *     COMPLETED: Task has been completed
 *     CANCELLED: Task was cancelled
 *     BLOCKED: Task is blocked due to dependencies
 * 
 * @since 0.1.12
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

    /**
     * Get the string value of this status.
     * 
     * @return The string representation
     */
    public String getValue() {
        return value;
    }

    /**
     * Parse a string to a TaskStatus.
     * 
     * @param value The string value to parse
     * @return The corresponding TaskStatus, or null if not found
     */
    public static TaskStatus fromValue(String value) {
        for (TaskStatus status : TaskStatus.values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        return null;
    }
}