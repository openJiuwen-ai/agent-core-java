/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.core.session.stream.OutputSchema;

/**
 * Team-layer output chunk tagged with the producing member identity.
 *
 * <p>Mirrors Python's {@code TeamOutputSchema} in
 * {@code openjiuwen/agent_teams/schema/stream.py}.</p>
 */
public class TeamOutputSchema extends OutputSchema {

    private String sourceMember;
    private TeamRole role;

    public TeamOutputSchema() {
    }

    public TeamOutputSchema(String type, int index, Object payload, String sourceMember, TeamRole role) {
        super(type, index, payload);
        this.sourceMember = sourceMember;
        this.role = role;
    }

    public static TeamOutputSchema fromOutput(OutputSchema base, String sourceMember, TeamRole role) {
        return new TeamOutputSchema(
                base.getType(),
                base.getIndex(),
                base.getPayload(),
                sourceMember,
                role
        );
    }

    public String getSourceMember() {
        return sourceMember;
    }

    public void setSourceMember(String sourceMember) {
        this.sourceMember = sourceMember;
    }

    public TeamRole getRole() {
        return role;
    }

    public void setRole(TeamRole role) {
        this.role = role;
    }
}
