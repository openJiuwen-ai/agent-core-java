// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.schema;

/**
 * Member mode enum - defines how members interact with tasks.
 * 
 * Mirrors Python's agent_teams.schema.status.MemberMode
 * 
 * Modes:
 *     BUILD_MODE: Members can claim and complete tasks directly (default)
 *     PLAN_MODE: Members need leader approval before completing tasks
 * 
 * @since 0.1.12
 */
public enum MemberMode {
    BUILD_MODE("build_mode"),
    PLAN_MODE("plan_mode");

    private final String value;

    MemberMode(String value) {
        this.value = value;
    }

    /**
     * Get the string value of this mode.
     * 
     * @return The string representation
     */
    public String getValue() {
        return value;
    }

    /**
     * Parse a string to a MemberMode.
     * 
     * @param value The string value to parse
     * @return The corresponding MemberMode, or null if not found
     */
    public static MemberMode fromValue(String value) {
        for (MemberMode mode : MemberMode.values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        return null;
    }
}