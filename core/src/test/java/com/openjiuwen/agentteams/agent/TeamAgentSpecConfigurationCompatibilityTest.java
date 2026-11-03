/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentteams.messager.InProcessMessager;
import com.openjiuwen.agentteams.messager.PyZmqMessager;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.schema.team.TeamRuntimeContext;
import com.openjiuwen.agentteams.tools.database.DatabaseType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

@Tag("agent-teams-config-slice")
class TeamAgentSpecConfigurationCompatibilityTest {
    private final List<TeamAgent> configuredAgents = new ArrayList<>();

    @AfterEach
    void closeConfiguredAgents() {
        configuredAgents.forEach(TeamAgent::close);
    }

    @Test
    void shouldCreateTransportSelectedBySpec() {
        TeamAgent inProcessAgent =
                configureAgent("transport-inprocess", "inprocess", "memory", null, "build_mode");
        TeamAgent pyZmqAgent =
                configureAgent("transport-pyzmq", "pyzmq", "memory", null, "build_mode");

        assertThat(inProcessAgent.getTeamBackend().getMessager()).isInstanceOf(InProcessMessager.class);
        assertThat(pyZmqAgent.getTeamBackend().getMessager()).isInstanceOf(PyZmqMessager.class);
    }

    @Test
    void shouldCreateDatabaseSelectedBySpec() {
        TeamAgent memoryAgent =
                configureAgent("storage-memory", "inprocess", "memory", null, "build_mode");
        TeamAgent sqliteAgent =
                configureAgent("storage-sqlite", "inprocess", "sqlite", ":memory:", "build_mode");

        assertThat(memoryAgent.getTeamBackend().getDb().getConfig().getDbType()).isEqualTo(DatabaseType.MEMORY);
        assertThat(memoryAgent.getTeamBackend().getDb().getConfig().getConnectionString()).isEmpty();
        assertThat(sqliteAgent.getTeamBackend().getDb().getConfig().getDbType()).isEqualTo(DatabaseType.SQLITE);
        assertThat(sqliteAgent.getTeamBackend().getDb().getConfig().getConnectionString()).isEqualTo(":memory:");
        assertThat(memoryAgent.getTeamBackend().getDb()).isNotSameAs(sqliteAgent.getTeamBackend().getDb());
    }

    @Test
    void shouldRenderTeammateModeSelectedBySpec() {
        TeamAgent buildModeAgent =
                configureAgent("mode-build", "inprocess", "memory", null, "build_mode");
        TeamAgent planModeAgent =
                configureAgent("mode-plan", "inprocess", "memory", null, "plan_mode");

        assertThat(renderRoleSection(buildModeAgent))
                .contains("Teammate execution mode: build_mode")
                .doesNotContain("Teammate execution mode: plan_mode");
        assertThat(renderRoleSection(planModeAgent))
                .contains("Teammate execution mode: plan_mode")
                .doesNotContain("Teammate execution mode: build_mode");
    }

    @Test
    void shouldPropagateMemberRoleToBackend() {
        TeamAgentSpec spec = TeamAgentSpec.builder()
                .name("member-role")
                .transport("inprocess")
                .storage("memory")
                .members(List.of(
                        TeamMemberSpec.builder().name("leader").role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("worker").role(TeamRole.MEMBER).build()))
                .build();
        TeamRuntimeContext context = TeamRuntimeContext.builder()
                .teamId(spec.getName())
                .memberName("worker")
                .role(TeamRole.MEMBER)
                .build();

        TeamAgent agent = new TeamAgent().configure(spec, context);
        configuredAgents.add(agent);

        assertThat(agent.getTeamBackend().isLeader()).isFalse();
    }

    private TeamAgent configureAgent(String teamName, String transport, String storage, String connectionString,
            String teammateMode) {
        TeamAgentSpec spec = TeamAgentSpec.builder()
                .name(teamName)
                .transport(transport)
                .storage(storage)
                .connectionString(connectionString)
                .teammateMode(teammateMode)
                .language("en")
                .members(List.of(TeamMemberSpec.builder()
                        .name("leader")
                        .role(TeamRole.LEADER)
                        .build()))
                .build();
        TeamRuntimeContext context = TeamRuntimeContext.builder()
                .teamId(teamName)
                .memberName("leader")
                .role(TeamRole.LEADER)
                .build();
        TeamAgent agent = new TeamAgent().configure(spec, context);
        configuredAgents.add(agent);
        return agent;
    }

    private String renderRoleSection(TeamAgent agent) {
        TeamRail rail = agent.getDeepAgent()
                .getRegisteredRails()
                .stream()
                .filter(TeamRail.class::isInstance)
                .map(TeamRail.class::cast)
                .findFirst()
                .orElseThrow();
        return rail.getStaticSections()
                .stream()
                .filter(section -> TeamRail.ROLE.equals(section.getName()))
                .findFirst()
                .orElseThrow()
                .render("en");
    }
}
