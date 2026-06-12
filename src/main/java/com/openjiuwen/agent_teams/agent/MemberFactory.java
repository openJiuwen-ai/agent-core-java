/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.ConfiguredTeamBackend;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamInfra;

/**
 * Factory for team member handles.
 *
 * <p>Mirrors Python's {@code create_member_handle} in
 * {@code openjiuwen/agent_teams/agent/member_factory.py}.</p>
 */
public final class MemberFactory {

    private MemberFactory() {
    }

    public static TeamMember createMemberHandle(
            String memberName,
            TeamAgentBlueprint blueprint,
            TeamInfra infra,
            AgentCard agentCard
    ) {
        ConfiguredTeamBackend teamBackend = infra.getTeamBackend();
        if (teamBackend == null) {
            return null;
        }
        return new TeamMember(
                memberName,
                teamBackend.getTeamName(),
                agentCard,
                teamBackend.getMemberStore(),
                infra.getMessager(),
                null,
                null,
                blueprint.getCtx().getPersona()
        );
    }
}
