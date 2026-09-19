/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.team;

/**
 * Public enum TeamRole used by the Java parity implementation.
 *
 * @since 1.0
 */
public enum TeamRole {
    LEADER,
    MEMBER,
    HUMAN_AGENT,
    USER;

    /**
     * Return the snake_case string value persisted on the member row.
     *
     * <p>Mirrors Python {@code schema/team.py:TeamRole} ({@code str, Enum})
     * values so the {@code team_member.role} column matches the Python DB
     * layout byte-for-byte. Used by DAO {@code is_human_agent} /
     * {@code list_human_agent_names} probes.</p>
     *
     * @return the snake_case string value
     */
    public String value() {
        return switch (this) {
            case LEADER -> "leader";
            case MEMBER -> "teammate";
            case HUMAN_AGENT -> "human_agent";
            case USER -> "user";
        };
    }
}
