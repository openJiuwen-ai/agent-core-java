/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.factory.AgentTeamsFactory;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.TeamRuntimeContext;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgentConfig;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_teams/test_team_agent_tools.py}.
 */
class TeamAgentToolsTest {

    @Test
    void testLeaderGetsManagementTools() {
        TeamAgent leader = AgentTeamsFactory.createAgentTeam(
                dummyAgents(false),
                "test",
                null,
                null,
                null,
                null,
                List.of(),
                metadata()
        );

        Set<String> names = toolNames(leader);

        assertTrue(names.contains("create_task"));
        assertTrue(names.contains("build_team"));
        assertTrue(names.contains("spawn_member"));
        assertTrue(names.contains("send_message"));
        assertTrue(names.contains("view_task"));
    }

    @Test
    void testTeammateGetsExecutionTools() {
        TeamAgent leader = AgentTeamsFactory.createAgentTeam(
                dummyAgents(false), "test", null, null, null, null, List.of(), metadata());
        TeamAgent teammate = new TeamAgent(card("dev-1", "Dev", "Teammate: dev"));

        teammate.configure(leader.getSpec(), memberContext(leader, "dev-1", "Dev", "dev"));
        Set<String> names = toolNames(teammate);

        assertTrue(names.contains("claim_task"));
        assertTrue(names.contains("send_message"));
        assertTrue(names.contains("view_task"));
        assertFalse(names.contains("create_task"));
        assertFalse(names.contains("build_team"));
        assertFalse(names.contains("spawn_member"));
    }

    @Test
    void testTaskAndMessageManagersAreStored() {
        TeamAgent leader = AgentTeamsFactory.createAgentTeam(
                dummyAgents(false), "test", null, null, null, null, List.of(), metadata());

        assertNotNull(leader.getTeamBackend());
        assertNotNull(leader.getTeamBackend().getTaskManager());
        assertNotNull(leader.getTeamBackend().getMessageManager());
    }

    @Test
    void testTeammateRegistersToolApprovalRailFromDeepAgentSpec() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName("test");
        spec.setAgents(dummyAgents(true));
        spec.setMetadata(metadata());
        TeamAgent leader = spec.build();

        TeamAgent teammate = new TeamAgent(card("dev-1", "Dev", "Teammate: dev"));
        teammate.configure(leader.getSpec(), memberContext(leader, "dev-1", "Dev", "dev"));

        assertTrue(teammate.getRegisteredRails().stream().anyMatch(TeamToolApprovalRail.class::isInstance));
    }

    private static Map<String, DeepAgentSpec> dummyAgents(boolean approvalRequired) {
        Map<String, DeepAgentSpec> agents = new LinkedHashMap<>();
        agents.put("leader", deepSpec(List.of()));
        agents.put("teammate", deepSpec(approvalRequired ? List.of("send_message") : List.of()));
        return agents;
    }

    private static DeepAgentSpec deepSpec(List<String> approvalRequiredTools) {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setSystemPrompt("team test");
        DeepAgentSpec spec = new DeepAgentSpec();
        spec.setConfig(config);
        spec.setApprovalRequiredTools(approvalRequiredTools);
        return spec;
    }

    private static TeamRuntimeContext memberContext(TeamAgent leader, String memberName, String name, String persona) {
        TeamMemberSpec member = new TeamMemberSpec();
        member.setMemberName(memberName);
        member.setDisplayName(name);
        member.setRoleType(TeamRole.TEAMMATE);
        member.setPersona(persona);
        return leader.buildMemberContext(member);
    }

    private static AgentCard card(String id, String name, String description) {
        AgentCard card = new AgentCard();
        card.setId(id);
        card.setName(name);
        card.setDescription(description);
        return card;
    }

    private static Set<String> toolNames(TeamAgent agent) {
        return agent.getDeepAgent().getDelegate().getAbilityManager().listToolInfo().stream()
                .map(ToolInfo::getName)
                .collect(Collectors.toSet());
    }

    private static Map<String, Object> metadata() {
        MessagerTransportConfig config = new MessagerTransportConfig();
        config.setBackend("pyzmq");
        config.setTeamName("test");
        config.setNodeId("team_leader");
        return Map.of("messager_config", config);
    }
}
