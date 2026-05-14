/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.factory;

import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.LeaderSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamLifecycle;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.harness.DeepAgentConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal Java factory for creating TeamAgent instances.
 *
 * <p>Mirrors Python's factory helpers in
 * {@code openjiuwen.agent_teams.factory}.
 */
public final class AgentTeamsFactory {

    private AgentTeamsFactory() {
    }

    public static TeamAgent createAgentTeam(
            Map<String, DeepAgentSpec> agents,
            String teamName,
            TeamLifecycle lifecycle,
            String teammateMode,
            String spawnMode,
            LeaderSpec leader,
            List<TeamMemberSpec> predefinedMembers,
            Map<String, Object> metadata
    ) {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setAgents(agents != null ? agents : new LinkedHashMap<>());
        spec.setTeamName(teamName != null ? teamName : "agent_team");
        spec.setLifecycle(lifecycle != null ? lifecycle : TeamLifecycle.TEMPORARY);
        spec.setTeammateMode(teammateMode != null ? teammateMode : "build_mode");
        spec.setSpawnMode(spawnMode != null ? spawnMode : "process");
        spec.setLeader(leader != null ? leader : new LeaderSpec());
        spec.setPredefinedMembers(predefinedMembers);
        spec.setMetadata(metadata);
        return spec.build();
    }

    public static TeamAgent createDefaultLeaderTeam(String teamName) {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setSystemPrompt("You are coordinating a Java agent team.");
        DeepAgentSpec leader = new DeepAgentSpec();
        leader.setConfig(config);
        Map<String, DeepAgentSpec> agents = new LinkedHashMap<>();
        agents.put("leader", leader);
        return createAgentTeam(agents, teamName, TeamLifecycle.TEMPORARY,
                "build_mode", "process", new LeaderSpec(), List.of(), Map.of());
    }
}
