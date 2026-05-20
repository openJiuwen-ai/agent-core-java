/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.status;

/**
 * Team member lifecycle states aligned to the current narrow Python recovery slice.
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
     * Auto-generated for codecheck compliance.
     */
    public String value() {
        return value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isLiveForSessionSwitch() {
        return this != UNSTARTED && this != SHUTDOWN;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean canTransitionTo(MemberStatus next) {
        if (next == null) {
            return false;
        }
        return switch (this) {
            case UNSTARTED -> next == READY || next == SHUTDOWN || next == ERROR;
            case READY -> next == READY
                    || next == BUSY
                    || next == SHUTDOWN_REQUESTED
                    || next == SHUTDOWN
                    || next == ERROR;
            case BUSY -> next == READY || next == SHUTDOWN_REQUESTED || next == ERROR;
            case RESTARTING -> next == READY || next == ERROR || next == SHUTDOWN;
            case SHUTDOWN_REQUESTED -> next == SHUTDOWN || next == ERROR;
            case SHUTDOWN -> next == RESTARTING;
            case ERROR -> next == RESTARTING || next == READY || next == SHUTDOWN_REQUESTED || next == SHUTDOWN;
        };
    }

    /**
     * Auto-generated for codecheck compliance.
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
