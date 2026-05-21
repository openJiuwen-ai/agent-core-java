// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.schema;

/**
 * Member status enum - simple status for team members.
 * 
 * Mirrors Python's agent_teams.schema.status.MemberStatus
 * 
 * States:
 *     UNSTARTED: Member has been created but not yet started
 *     READY: Member is ready to receive tasks
 *     BUSY: Member is currently processing a task
 *     RESTARTING: Member process is being restarted after failure
 *     SHUTDOWN_REQUESTED: Member has received shutdown request
 *     SHUTDOWN: Member has been shut down
 *     ERROR: Member is in error state
 * 
 * @since 0.1.12
 */
public enum MemberStatus {
    UNSTARTED("unstarted"),
    READY("ready"),
    BUSY("busy"),
    RESTARTING("restarting"),
    SHUTDOWN_REQUESTED("shutdown_requested"),
    SHUTDOWN("shut_down"),
    ERROR("error");

    private final String value;

    MemberStatus(String value) {
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
     * Parse a string to a MemberStatus.
     * 
     * @param value The string value to parse
     * @return The corresponding MemberStatus, or null if not found
     */
    public static MemberStatus fromValue(String value) {
        for (MemberStatus status : MemberStatus.values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        return null;
    }
}