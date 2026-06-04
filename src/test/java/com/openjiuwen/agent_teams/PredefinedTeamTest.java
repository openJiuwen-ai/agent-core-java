/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams;

import com.openjiuwen.agent_teams.agent.AgentPolicy;
import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.LeaderSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.TeamRuntimeContext;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.tools.AgentTeamsToolRegistry;
import com.openjiuwen.agent_teams.tools.TeamBackend;
import com.openjiuwen.core.foundation.tool.Tool;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_teams/test_predefined_team.py}.
 */
class PredefinedTeamTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Test
    void test_build_team_registers_predefined_members() {
        TeamBackend backend = predefinedBackend("registers");

        backend.buildTeam("Test Team", "A predefined team", "Leader", "PM");

        assertNotNull(backend.getMember("leader1"));
        assertNotNull(backend.getMember("backend-dev"));
        assertNotNull(backend.getMember("frontend-dev"));
        assertEquals(3, backend.getStore().getMembers().size());
    }

    @Test
    void test_predefined_members_status_is_unstarted() {
        TeamBackend backend = predefinedBackend("status");

        backend.buildTeam("Test Team", "desc", "Leader", "PM");

        assertEquals(MemberStatus.UNSTARTED, backend.getMember("backend-dev").getStatus());
        assertEquals(MemberStatus.UNSTARTED, backend.getMember("frontend-dev").getStatus());
        assertEquals(ExecutionStatus.IDLE, backend.getMember("backend-dev").getExecutionStatus());
        assertEquals(ExecutionStatus.IDLE, backend.getMember("frontend-dev").getExecutionStatus());
    }

    @Test
    void test_predefined_members_preserve_desc_and_prompt() {
        TeamBackend backend = predefinedBackend("desc_prompt");

        backend.buildTeam("Test Team", "desc", "Leader", "PM");

        assertEquals("Senior backend engineer", backend.getMember("backend-dev").getDesc());
        assertEquals("Check tasks and start working", backend.getMember("backend-dev").getPrompt());
        assertEquals("Senior frontend engineer", backend.getMember("frontend-dev").getDesc());
        assertNull(backend.getMember("frontend-dev").getPrompt());
    }

    @Test
    void test_leader_still_registered_as_busy() {
        TeamBackend backend = predefinedBackend("leader_busy");

        backend.buildTeam("Test Team", "desc", "Leader", "PM");

        assertEquals(MemberStatus.BUSY, backend.getMember("leader1").getStatus());
        assertEquals(ExecutionStatus.RUNNING, backend.getMember("leader1").getExecutionStatus());
    }

    @Test
    void test_build_team_only_registers_leader() {
        TeamBackend backend = new TeamBackend(
                teamName("auto"),
                "leader1",
                true,
                MemberMode.BUILD_MODE,
                List.of()
        );

        backend.buildTeam("Auto Team", "desc", "Leader", "PM");

        assertEquals(1, backend.getStore().getMembers().size());
        assertNotNull(backend.getMember("leader1"));
    }

    @Test
    void test_exclude_spawn_member_when_predefined() {
        TeamBackend backend = predefinedBackend("exclude_spawn");

        Set<String> toolNames = toolNames(AgentTeamsToolRegistry.createTeamTools(
                backend,
                TeamRole.LEADER,
                "build_mode",
                Set.of("spawn_member")
        ));

        assertFalse(toolNames.contains("spawn_member"));
        assertTrue(toolNames.contains("build_team"));
        assertTrue(toolNames.contains("shutdown_member"));
        assertTrue(toolNames.contains("create_task"));
    }

    @Test
    void test_no_exclusion_without_predefined() {
        TeamBackend backend = new TeamBackend(
                teamName("no_exclusion"),
                "leader1",
                true,
                MemberMode.BUILD_MODE,
                List.of()
        );

        Set<String> toolNames = toolNames(AgentTeamsToolRegistry.createTeamTools(
                backend,
                TeamRole.LEADER,
                "build_mode"
        ));

        assertTrue(toolNames.contains("spawn_member"));
    }

    @Test
    void test_exclude_does_not_affect_teammate_tools() {
        TeamBackend backend = predefinedBackend("teammate_exclusion");

        Set<String> toolNames = toolNames(AgentTeamsToolRegistry.createTeamTools(
                backend,
                TeamRole.TEAMMATE,
                "build_mode",
                Set.of("spawn_member")
        ));

        assertTrue(toolNames.contains("claim_task"));
    }

    @Test
    void test_leader_has_approval_tools_in_plan_mode() {
        TeamBackend backend = predefinedBackend("plan_approval");

        Set<String> toolNames = toolNames(AgentTeamsToolRegistry.createTeamTools(
                backend,
                TeamRole.LEADER,
                "plan_mode"
        ));

        assertTrue(toolNames.contains("approve_plan"));
        assertTrue(toolNames.contains("approve_tool"));
    }

    @Test
    void test_leader_no_approval_tools_in_build_mode() {
        TeamBackend backend = predefinedBackend("build_no_approval");

        Set<String> toolNames = toolNames(AgentTeamsToolRegistry.createTeamTools(
                backend,
                TeamRole.LEADER,
                "build_mode"
        ));

        assertFalse(toolNames.contains("approve_plan"));
        assertFalse(toolNames.contains("approve_tool"));
    }

    @Test
    void test_teammate_does_not_have_approval_tools() {
        TeamBackend backend = predefinedBackend("teammate_no_approval");

        for (String mode : List.of("build_mode", "plan_mode")) {
            Set<String> toolNames = toolNames(AgentTeamsToolRegistry.createTeamTools(
                    backend,
                    TeamRole.TEAMMATE,
                    mode
            ));
            assertFalse(toolNames.contains("approve_plan"));
            assertFalse(toolNames.contains("approve_tool"));
        }
    }

    @Test
    void test_predefined_prompt_includes_override() {
        String prompt = promptFor(TeamRole.LEADER, "predefined");

        assertTrue(prompt.contains("Predefined Team Mode"));
        assertTrue(prompt.contains("spawn_member"));
    }

    @Test
    void test_auto_team_prompt_no_override() {
        String prompt = promptFor(TeamRole.LEADER, "default");

        assertFalse(prompt.contains("Predefined Team Mode"));
    }

    @Test
    void test_hybrid_prompt_includes_hybrid_mode() {
        String prompt = promptFor(TeamRole.LEADER, "hybrid");

        assertTrue(prompt.contains("Hybrid Team Mode"));
        assertTrue(prompt.contains("spawn_member"));
    }

    @Test
    void test_predefined_workflow_not_applied_to_teammate() {
        String prompt = promptFor(TeamRole.TEAMMATE, "predefined");

        assertFalse(prompt.contains("Predefined Team Mode"));
    }

    @Test
    void test_resolve_by_member_name_first() throws Exception {
        DeepAgentSpec customSpec = new DeepAgentSpec();
        TeamAgentSpec spec = specWithAgents(Map.of(
                "leader", new DeepAgentSpec(),
                "teammate", new DeepAgentSpec(),
                "custom-member", customSpec
        ));

        assertSame(customSpec, resolveAgentSpec(spec, TeamRole.TEAMMATE, "custom-member"));
    }

    @Test
    void test_fallback_to_role_value() throws Exception {
        DeepAgentSpec teammateSpec = new DeepAgentSpec();
        TeamAgentSpec spec = specWithAgents(Map.of(
                "leader", new DeepAgentSpec(),
                "teammate", teammateSpec
        ));

        assertSame(teammateSpec, resolveAgentSpec(spec, TeamRole.TEAMMATE, "unknown-member"));
    }

    @Test
    void test_fallback_chain_to_leader() throws Exception {
        DeepAgentSpec leaderSpec = new DeepAgentSpec();
        TeamAgentSpec spec = specWithAgents(Map.of("leader", leaderSpec));

        assertSame(leaderSpec, resolveAgentSpec(spec, TeamRole.TEAMMATE, "unknown-member"));
    }

    @Test
    void test_leader_role_uses_leader_spec() throws Exception {
        DeepAgentSpec leaderSpec = new DeepAgentSpec();
        TeamAgentSpec spec = specWithAgents(Map.of(
                "leader", leaderSpec,
                "teammate", new DeepAgentSpec()
        ));

        assertSame(leaderSpec, resolveAgentSpec(spec, TeamRole.LEADER, null));
    }

    private static TeamBackend predefinedBackend(String suffix) {
        return new TeamBackend(
                teamName(suffix),
                "leader1",
                true,
                MemberMode.BUILD_MODE,
                predefinedMembers()
        );
    }

    private static List<TeamMemberSpec> predefinedMembers() {
        TeamMemberSpec backend = new TeamMemberSpec();
        backend.setMemberName("backend-dev");
        backend.setDisplayName("Backend Developer");
        backend.setPersona("Senior backend engineer");
        backend.setPromptHint("Check tasks and start working");

        TeamMemberSpec frontend = new TeamMemberSpec();
        frontend.setMemberName("frontend-dev");
        frontend.setDisplayName("Frontend Developer");
        frontend.setPersona("Senior frontend engineer");

        return List.of(backend, frontend);
    }

    private static String promptFor(TeamRole role, String teamMode) {
        AgentPolicy.TeamRole policyRole = role == TeamRole.LEADER
                ? AgentPolicy.TeamRole.LEADER
                : AgentPolicy.TeamRole.MEMBER;
        return AgentPolicy.buildSystemPrompt(
                policyRole,
                role == TeamRole.LEADER ? "PM" : "Dev",
                "",
                Map.of(),
                List.of(),
                role == TeamRole.LEADER ? "leader" : "teammate",
                "temporary",
                "en",
                teamMode
        );
    }

    private static Set<String> toolNames(List<Tool> tools) {
        return tools.stream()
                .map(tool -> tool.getCard().getName())
                .collect(Collectors.toSet());
    }

    private static DeepAgentSpec resolveAgentSpec(
            TeamAgentSpec spec,
            TeamRole role,
            String memberName
    ) throws Exception {
        TeamRuntimeContext context = new TeamRuntimeContext();
        context.setRole(role);
        context.setMemberName(memberName);
        Method method = TeamAgent.class.getDeclaredMethod(
                "resolveAgentSpec",
                TeamAgentSpec.class,
                TeamRuntimeContext.class
        );
        method.setAccessible(true);
        return (DeepAgentSpec) method.invoke(null, spec, context);
    }

    private static TeamAgentSpec specWithAgents(Map<String, DeepAgentSpec> agents) {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setAgents(new LinkedHashMap<>(agents));
        spec.setLeader(new LeaderSpec());
        return spec;
    }

    private static String teamName(String suffix) {
        return "predefined_" + suffix + "_" + COUNTER.incrementAndGet();
    }
}
