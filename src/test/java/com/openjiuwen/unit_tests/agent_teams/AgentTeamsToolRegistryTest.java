/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams;

import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.tools.AgentTeamsToolRegistry;
import com.openjiuwen.agent_teams.tools.TeamBackend;
import com.openjiuwen.agent_teams.tools.TeamBackendRegistry;
import com.openjiuwen.core.foundation.tool.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code create_team_tools} factory tests in
 * {@code tests.unit_tests.agent_teams.test_team_tools}.
 */
class AgentTeamsToolRegistryTest {

    @BeforeEach
    void resetBackendRegistry() {
        TeamBackendRegistry.clear();
    }

    @Test
    void leaderBuildModeOmitsApprovalToolsAndPreservesPythonOrder() {
        List<String> names = toolNames(AgentTeamsToolRegistry.createTeamTools(
                agentTeam(),
                TeamRole.LEADER,
                "build_mode"
        ));

        assertEquals(List.of(
                "build_team",
                "clean_team",
                "spawn_member",
                "shutdown_member",
                "list_members",
                "create_task",
                "update_task",
                "view_task",
                "send_message"
        ), names);
    }

    @Test
    void leaderPlanModeIncludesApprovalTools() {
        List<String> names = toolNames(AgentTeamsToolRegistry.createTeamTools(
                agentTeam(),
                TeamRole.LEADER,
                "plan_mode"
        ));

        assertTrue(names.contains("approve_plan"));
        assertTrue(names.contains("approve_tool"));
        assertTrue(names.indexOf("approve_plan") < names.indexOf("list_members"));
    }

    @Test
    void memberToolsUseSharedAndClaimTaskOnly() {
        List<String> names = toolNames(AgentTeamsToolRegistry.createTeamTools(
                agentTeam(),
                TeamRole.TEAMMATE,
                "build_mode"
        ));

        assertEquals(List.of("view_task", "claim_task", "send_message"), names);
    }

    @Test
    void humanAgentOnlyGetsSendMessage() {
        List<String> names = toolNames(AgentTeamsToolRegistry.createTeamTools(
                agentTeam(),
                TeamRole.HUMAN_AGENT,
                "build_mode"
        ));

        assertEquals(List.of("send_message"), names);
    }

    @Test
    void excludeToolsRemovesAllowedTools() {
        List<String> names = toolNames(AgentTeamsToolRegistry.createTeamTools(
                agentTeam(),
                TeamRole.LEADER,
                "plan_mode",
                Set.of("send_message", "approve_tool")
        ));

        assertFalse(names.contains("send_message"));
        assertFalse(names.contains("approve_tool"));
        assertTrue(names.contains("approve_plan"));
    }

    @Test
    void workspaceMetaIsAllowedLikePythonButNoJavaTeamToolIsRegistered() {
        assertTrue(AgentTeamsToolRegistry.LEADER_TOOLS.contains("workspace_meta"));
        assertTrue(AgentTeamsToolRegistry.MEMBER_TOOLS.contains("workspace_meta"));
        assertFalse(toolNames(AgentTeamsToolRegistry.createTeamTools(
                agentTeam(),
                TeamRole.LEADER,
                "plan_mode"
        )).contains("workspace_meta"));
    }

    private static TeamBackend agentTeam() {
        TeamBackend team = new TeamBackend("test_team", "leader1", true, MemberMode.BUILD_MODE, List.of());
        team.getStore().createTeam("Test Team", null, "leader1", null, null);
        return team;
    }

    private static List<String> toolNames(List<Tool> tools) {
        return tools.stream().map(tool -> tool.getCard().getName()).toList();
    }
}
