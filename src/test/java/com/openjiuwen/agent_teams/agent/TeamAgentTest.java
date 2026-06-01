/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_teams.factory.AgentTeamsFactory;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.TeamRuntimeContext;
import com.openjiuwen.agent_teams.schema.TeamSpec;
import com.openjiuwen.core.runner.spawn.SpawnAgentConfig;
import com.openjiuwen.core.runner.spawn.SpawnAgentKind;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.harness.DeepAgentConfig;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_teams/test_team_agent.py}.
 */
class TeamAgentTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void testTeamAgentLeaderPolicy() {
        TeamAgent leader = leader("delivery");

        assertEquals(TeamRole.LEADER, leader.getRuntimeContext().getRole());
        TeamRail teamRail = leader.getRegisteredRails().stream()
                .filter(TeamRail.class::isInstance)
                .map(TeamRail.class::cast)
                .findFirst()
                .orElseThrow();
        PromptSection roleSection = teamRail.getStaticSections().stream()
                .filter(section -> TeamSectionName.ROLE.equals(section.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(roleSection.render("cn").contains("TeamLeader"));
    }

    @Test
    void testSpawnPayloadContainsMemberIdentity() {
        TeamAgent leader = leader("delivery");
        TeamRuntimeContext ctx = memberContext(leader, "fe-1", "Frontend Expert", "front-end engineer");

        Map<String, Object> payload = leader.buildSpawnPayload(ctx, "Review the design system impact.");
        @SuppressWarnings("unchecked")
        Map<String, Object> coordination = (Map<String, Object>) payload.get("coordination");
        @SuppressWarnings("unchecked")
        Map<String, Object> transport = (Map<String, Object>) coordination.get("messager_config");

        assertEquals("teammate", coordination.get("role"));
        assertEquals("front-end engineer", coordination.get("persona"));
        assertEquals("fe-1", transport.get("node_id"));
        assertEquals("Review the design system impact.", payload.get("query"));
    }

    @Test
    void testSpawnConfigContainsSerializableTeamAgentPayload() throws Exception {
        TeamAgent leader = leader("delivery");
        TeamRuntimeContext ctx = memberContext(leader, "be-1", "Backend Expert", "backend architect");

        SpawnAgentConfig spawnConfig = leader.buildSpawnConfig(ctx);

        assertEquals(SpawnAgentKind.TEAM_AGENT, spawnConfig.getAgentKind());
        assertNotNull(spawnConfig.getRunnerConfig());
        assertTrue(spawnConfig.getPayload().containsKey("spec"));
        assertTrue(spawnConfig.getPayload().containsKey("context"));
        @SuppressWarnings("unchecked")
        Map<String, Object> contextMap = (Map<String, Object>) spawnConfig.getPayload().get("context");
        @SuppressWarnings("unchecked")
        Map<String, Object> transport = (Map<String, Object>) contextMap.get("messager_config");
        assertEquals("teammate", contextMap.get("role"));
        assertEquals("be-1", transport.get("node_id"));

        JSON.writeValueAsString(spawnConfig);

        TeamAgent teammate = TeamAgent.fromSpawnPayload(spawnConfig.getPayload());

        assertEquals(TeamRole.TEAMMATE, teammate.getRuntimeContext().getRole());
        assertEquals("be-1", teammate.getCard().getName());
        assertNotNull(teammate.getRuntimeContext());
        assertNotNull(teammate.getRuntimeContext().getMessagerConfig());
        assertEquals("be-1", teammate.getRuntimeContext().getMessagerConfig().getNodeId());
    }

    @Test
    void testRuntimeContextRoundtripsWithJacksonSerialization() throws Exception {
        TeamRuntimeContext context = new TeamRuntimeContext();
        context.setRole(TeamRole.LEADER);
        context.setMemberName("leader-1");
        context.setPersona("pm");
        TeamSpec teamSpec = new TeamSpec();
        teamSpec.setTeamName("demo");
        teamSpec.setDisplayName("demo");
        context.setTeamSpec(teamSpec);

        TeamRuntimeContext restored = JSON.readValue(JSON.writeValueAsString(context), TeamRuntimeContext.class);

        assertEquals(TeamRole.LEADER, restored.getRole());
        assertEquals("leader-1", restored.getMemberName());
        assertEquals("pm", restored.getPersona());
        assertEquals("demo", restored.getTeamSpec().getTeamName());
    }

    private static TeamAgent leader(String teamName) {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setSystemPrompt("lead team");
        DeepAgentSpec leaderSpec = new DeepAgentSpec();
        leaderSpec.setConfig(config);
        MessagerTransportConfig transport = new MessagerTransportConfig();
        transport.setBackend("pyzmq");
        transport.setTeamName(teamName);
        transport.setNodeId("leader");
        return AgentTeamsFactory.createAgentTeam(
                Map.of("leader", leaderSpec, "teammate", leaderSpec),
                teamName,
                null,
                null,
                null,
                null,
                List.of(),
                Map.of("messager_config", transport)
        );
    }

    private static TeamRuntimeContext memberContext(TeamAgent leader, String memberName, String name, String persona) {
        TeamMemberSpec member = new TeamMemberSpec();
        member.setMemberName(memberName);
        member.setDisplayName(name);
        member.setRoleType(TeamRole.TEAMMATE);
        member.setPersona(persona);
        return leader.buildMemberContext(member);
    }
}
