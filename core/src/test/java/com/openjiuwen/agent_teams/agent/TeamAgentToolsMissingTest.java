/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamSpec;
import com.openjiuwen.agent_teams.rails.TeamToolApprovalRail;
import com.openjiuwen.agent_teams.tools.InMemoryTeamDatabase;
import com.openjiuwen.agent_teams.tools.TeamBackend;
import com.openjiuwen.agent_teams.tools.TeamTools;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_teams/test_team_agent_tools.py}.
 */
class TeamAgentToolsMissingTest {

    @Test
    void leaderGetsManagementTools() {
        Set<String> names = toolNames("leader", "team_leader", true);

        assertThat(names).contains("create_task", "build_team", "spawn_member", "send_message", "view_task");
    }

    @Test
    void teammateGetsExecutionTools() {
        Set<String> names = toolNames("teammate", "dev-1", false);

        assertThat(names).contains("claim_task", "send_message", "view_task");
        assertThat(names).doesNotContain("create_task", "build_team", "spawn_member");
    }

    @Test
    void taskAndMessageManagersAreStored() {
        TeamAgent leader = configuredLeader(baseSpec());

        assertThat(leader.getInfra().getTaskManager()).isNotNull();
        assertThat(leader.getInfra().getMessageManager()).isNotNull();
    }

    @Test
    void teammateRegistersToolApprovalRailFromDeepAgentSpec() {
        TeamAgentSpec spec = baseSpec();
        DeepAgentSpec teammateSpec = new DeepAgentSpec();
        teammateSpec.setApprovalRequiredTools(List.of("send_message"));
        spec.setAgents(Map.of("leader", new DeepAgentSpec(), "teammate", teammateSpec));
        TeamAgent leader = configuredLeader(spec);

        TeamRuntimeContext context = context(TeamRole.TEAMMATE, "dev-1");
        context.setTeamSpec(leader.getRuntimeContext().getTeamSpec());
        context.setDbConfig(leader.getRuntimeContext().getDbConfig());

        TeamAgent teammate = new TeamAgent(new AgentCard("dev-1", "Dev", "Teammate: dev"));
        teammate.configure(spec, context);

        assertThat(teammate.getHarness().findRails(TeamToolApprovalRail.class))
                .hasSize(1)
                .first()
                .isInstanceOfSatisfying(TeamToolApprovalRail.class, rail ->
                        assertThat(rail.getTools()).containsExactly("send_message"));
    }

    private static Set<String> toolNames(String role, String memberName, boolean leader) {
        TeamBackend backend = new TeamBackend("test", memberName, leader, new InMemoryTeamDatabase(), null);
        return TeamTools.createTeamTools(
                        role,
                        backend,
                        "build_mode",
                        "temporary",
                        null,
                        null,
                        Set.of(),
                        "en"
                )
                .stream()
                .map(tool -> tool.card().name())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static TeamAgent configuredLeader(TeamAgentSpec spec) {
        TeamAgent leader = new TeamAgent(new AgentCard("team_leader", "Leader", "leader"));
        leader.configure(spec, context(TeamRole.LEADER, "team_leader"));
        return leader;
    }

    private static TeamAgentSpec baseSpec() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName("test");
        spec.setAgents(Map.of("leader", new DeepAgentSpec()));
        return spec;
    }

    private static TeamRuntimeContext context(TeamRole role, String memberName) {
        TeamRuntimeContext context = new TeamRuntimeContext();
        context.setRole(role);
        context.setMemberName(memberName);
        context.setPersona("dev");
        context.setTeamSpec(new TeamSpec("test", "Test", "team_leader"));
        return context;
    }
}
