/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.prompts;

import com.openjiuwen.agent_teams.schema.TeamRole;

/**
 * Minimal roster row passed to {@link BridgeRemoteBrief#buildTeamOverview(String, Iterable, String)}.
 *
 * <p>Mirrors Python's {@code MemberSummary} in
 * {@code openjiuwen/agent_teams/prompts/bridge_remote_brief.py}.</p>
 */
public record MemberSummary(String memberName, TeamRole role, String persona) {

    public MemberSummary {
        persona = persona == null ? "" : persona;
    }

    public MemberSummary(String memberName, TeamRole role) {
        this(memberName, role, "");
    }
}
