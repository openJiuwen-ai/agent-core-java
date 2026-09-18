/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.openjiuwen.agent_teams.agent.AgentConfigurator;

/**
 * Supported team roles.
 *
 * <p>Mirrors Python's {@code TeamRole} in
 * {@code openjiuwen/agent_teams/schema/team.py}.</p>
 */
public enum TeamRole {
    LEADER("leader"),
    TEAMMATE("teammate"),
    HUMAN_AGENT("human_agent"),
    BRIDGE_AGENT("bridge_agent");

    private final String value;

    TeamRole(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public AgentConfigurator.TeamRole toConfiguratorRole() {
        return AgentConfigurator.TeamRole.fromValue(value);
    }

    @JsonCreator
    public static TeamRole fromValue(String value) {
        for (TeamRole role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown team role: " + value);
    }

    public static TeamRole fromConfiguratorRole(AgentConfigurator.TeamRole role) {
        return role == null ? null : fromValue(role.value());
    }
}
