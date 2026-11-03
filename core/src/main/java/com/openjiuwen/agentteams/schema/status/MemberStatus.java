/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.status;

import java.util.Set;

/**
 * Team member lifecycle states aligned to Python 0.1.15
 * {@code schema/status.py:MemberStatus}.
 *
 * <p>The STARTING / PAUSED / STOPPED values were missing in the 0.1.12 narrow
 * slice; they are restored here so {@code startup_member} can use the
 * UNSTARTED&rarr;STARTING CAS guard and {@code MEMBER_SETTLED_STATUSES}
 * matches the Python settled set exactly ({READY, PAUSED, STOPPED, SHUTDOWN}).</p>
 *
 * @since 2026/7/9
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

    /**
     * Member statuses considered settled (not actively working).
     *
     * <p>Mirrors Python {@code schema/status.py:MEMBER_SETTLED_STATUSES}.
     * Used by {@code TeamBackend.isTeamCompleted()} as the second completion
     * condition: every member -- including the leader -- must be in one of
     * these states before the team can conclude.</p>
     */
    public static final Set<String> MEMBER_SETTLED_STATUSES =
            Set.of(READY.value(), PAUSED.value(), STOPPED.value(), SHUTDOWN.value());

    private final String value;

    MemberStatus(String value) {
        this.value = value;
    }

    /**
     * Return the snake_case string value for DB persistence.
     *
     * @return the string value
     */
    public String value() {
        return value;
    }

    /**
     * Whether this status allows session switching.
     *
     * @return {@code true} if live for session switch
     */
    public boolean isLiveForSessionSwitch() {
        return this != UNSTARTED && this != SHUTDOWN;
    }

    /**
     * Return whether {@code this} may transition to {@code next}.
     *
     * <p>Mirrors Python {@code MEMBER_TRANSITIONS} at
     * {@code schema/status.py:51}. The table is the authoritative source of
     * truth for member status transitions; {@code TeamBackend} and DAO CAS
     * guards rely on it to reject illegal flips.</p>
     *
     * @param next the target status
     * @return {@code true} if transition is allowed
     */
    public boolean canTransitionTo(MemberStatus next) {
        if (next == null) {
            return false;
        }
        return switch (this) {
            case UNSTARTED -> next == STARTING || next == READY || next == SHUTDOWN || next == ERROR;
            case STARTING -> next == READY || next == UNSTARTED || next == SHUTDOWN || next == ERROR;
            case READY -> next == READY
                    || next == BUSY
                    || next == PAUSED
                    || next == STOPPED
                    || next == SHUTDOWN_REQUESTED
                    || next == SHUTDOWN
                    || next == ERROR;
            case BUSY -> next == READY
                    || next == PAUSED
                    || next == STOPPED
                    || next == SHUTDOWN_REQUESTED
                    || next == ERROR;
            case PAUSED -> next == READY
                    || next == RESTARTING
                    || next == STOPPED
                    || next == SHUTDOWN_REQUESTED
                    || next == SHUTDOWN
                    || next == ERROR;
            case STOPPED -> next == READY
                    || next == RESTARTING
                    || next == SHUTDOWN_REQUESTED
                    || next == SHUTDOWN
                    || next == ERROR;
            case RESTARTING -> next == READY || next == STOPPED || next == ERROR || next == SHUTDOWN;
            case SHUTDOWN_REQUESTED -> next == SHUTDOWN || next == ERROR;
            case SHUTDOWN -> next == RESTARTING;
            case ERROR -> next == RESTARTING
                    || next == READY
                    || next == STOPPED
                    || next == SHUTDOWN_REQUESTED
                    || next == SHUTDOWN;
        };
    }

    /**
     * Look up a MemberStatus by its snake_case value.
     *
     * @param value the string value
     * @return the matching MemberStatus
     * @throws IllegalArgumentException if no match
     */
    public static MemberStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return READY;
        }
        for (MemberStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown member status: " + value);
    }
}
