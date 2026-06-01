/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams;

import com.openjiuwen.agent_teams.agent.TeamMember;
import com.openjiuwen.agent_teams.constants.TeamConstants;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.task.TaskRecord;
import com.openjiuwen.agent_teams.schema.task.TaskStatus;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.tools.BuildTeamTool;
import com.openjiuwen.agent_teams.tools.Team;
import com.openjiuwen.agent_teams.tools.TeamBackend;
import com.openjiuwen.agent_teams.tools.TeamBackendRegistry;
import com.openjiuwen.agent_teams.tools.TeamToolOutput;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.agent_teams.tools.team.TeamBackend}.
 */
class TeamBackendTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void resetBackendRegistry() {
        TeamBackendRegistry.clear();
    }

    @Test
    void buildTeamRegistersLeaderPredefinedMembersAndHumanAgents() {
        TeamBackend backend = new TeamBackend(
                "team-build",
                "leader",
                true,
                MemberMode.PLAN_MODE,
                List.of(teammate("dev-1"), human("human_pm", "Human PM"))
        );

        backend.buildTeam("Build Team", "deliver product", "Leader", "Lead persona", false);

        Team teamInfo = backend.getTeamInfo();
        assertNotNull(teamInfo);
        assertEquals("team-build", teamInfo.getTeamName());
        assertEquals("Build Team", teamInfo.getDisplayName());
        assertEquals("deliver product", teamInfo.getDesc());
        assertTrue(backend.getTeamUpdatedAt() > 0L);
        assertTrue(backend.getMembersMaxUpdatedAt() > 0L);

        TeamMember leader = backend.getMember("leader");
        assertNotNull(leader);
        assertEquals(MemberStatus.BUSY, leader.getStatus());
        assertEquals(ExecutionStatus.RUNNING, leader.getExecutionStatus());

        assertEquals(MemberStatus.UNSTARTED, backend.getMember("dev-1").getStatus());
        assertEquals(MemberStatus.READY, backend.getMember("human_pm").getStatus());
        assertTrue(backend.isHumanAgent("human_pm"));
        assertTrue(backend.hittEnabled());
        assertFalse(backend.hasMember(TeamConstants.HUMAN_AGENT_MEMBER_NAME));
        assertFalse(backend.listMembers().stream().anyMatch(member -> "leader".equals(member.getMemberName())));
    }

    @Test
    void buildTeamToolForwardsEnableHittToBackend() throws Exception {
        TeamBackend backend = new TeamBackend("team-tool", "leader", true, MemberMode.BUILD_MODE, List.of());

        TeamToolOutput output = assertInstanceOf(TeamToolOutput.class, new BuildTeamTool(backend).invoke(Map.of(
                "display_name", "HITT Team",
                "team_desc", "with human",
                "leader_display_name", "Leader",
                "leader_desc", "persona",
                "enable_hitt", true
        )));

        assertTrue(output.isSuccess());
        assertTrue(Boolean.TRUE.equals(((Map<?, ?>) output.getData()).get("enable_hitt")));
        assertTrue(backend.hittEnabled());
        assertTrue(backend.isHumanAgent(TeamConstants.HUMAN_AGENT_MEMBER_NAME));
        assertEquals(MemberStatus.READY, backend.getMember(TeamConstants.HUMAN_AGENT_MEMBER_NAME).getStatus());
        assertNull(backend.deliverMessage("hello", TeamConstants.HUMAN_AGENT_MEMBER_NAME, "user").get("runtime_result"));
    }

    @Test
    void cancelMemberResetsOnlyClaimedTasksForBusyMember() {
        TeamBackend backend = backendWithTeam("team-cancel");
        spawn(backend, "worker", MemberStatus.BUSY);
        TaskRecord task1 = backend.createTask("Task 1", "Content 1", "t1", List.of());
        TaskRecord task2 = backend.createTask("Task 2", "Content 2", "t2", List.of());
        TaskRecord task3 = backend.createTask("Task 3", "Content 3", "t3", List.of());
        assertTrue(backend.claimTask(task1.getTaskId(), "worker"));
        assertTrue(backend.claimTask(task2.getTaskId(), "worker"));

        assertTrue(backend.cancelMember("worker"));

        assertEquals(TaskStatus.PENDING, backend.getTask(task1.getTaskId()).getStatus());
        assertNull(backend.getTask(task1.getTaskId()).getAssignee());
        assertEquals(TaskStatus.PENDING, backend.getTask(task2.getTaskId()).getStatus());
        assertNull(backend.getTask(task2.getTaskId()).getAssignee());
        assertEquals(TaskStatus.PENDING, backend.getTask(task3.getTaskId()).getStatus());
        assertNull(backend.getTask(task3.getTaskId()).getAssignee());
    }

    @Test
    void forceCleanTeamClearsRuntimeStateAndCleanupPaths() throws Exception {
        TeamBackend backend = backendWithTeam("team-force");
        Path cleanup = tempDir.resolve("team-force-workspace");
        Files.createDirectories(cleanup.resolve("nested"));
        backend.registerCleanupPath(cleanup.toString());
        spawn(backend, "worker", MemberStatus.READY);
        backend.createTask("Task", "Content", "t1", List.of());
        backend.sendMessage("hi", "worker", "leader");

        assertTrue(backend.forceCleanTeam(false));

        assertFalse(Files.exists(cleanup));
        assertNull(backend.getTeamInfo());
        assertTrue(backend.listMembers().isEmpty());
        assertTrue(backend.listTasks().isEmpty());
        assertFalse(backend.hittEnabled());
    }

    @Test
    void hardcodedReturnBranchesMatchPythonFailureAndNoneSemantics() {
        TeamBackend backend = backendWithTeam("team-branches");

        assertNull(backend.ensureMemberSession("", sampleAgentCard()));
        assertNull(backend.rebindMemberSession("missing"));
        assertNull(backend.ensureMemberRuntime(""));
        assertNull(backend.ensureMemberRuntime("missing"));
        assertNull(backend.runMember("missing", "content"));
        assertFalse(backend.shutdownMember("missing", false));
        assertFalse(backend.approveTool("missing", "tool-call", true, null));
        assertNull(backend.claimAndRunTask("missing-task", "missing"));
        assertFalse(backend.assignTask("missing-task", "missing"));

        spawn(backend, "worker", MemberStatus.READY);
        assertFalse(backend.canCleanTeam());
    }

    private static TeamBackend backendWithTeam(String teamName) {
        TeamBackend backend = new TeamBackend(teamName, "leader", true, MemberMode.BUILD_MODE, List.of());
        backend.getStore().createTeam("Test Team", null, "leader", null, null);
        return backend;
    }

    private static TeamMember spawn(TeamBackend backend, String memberName, MemberStatus status) {
        return backend.spawnMember(
                memberName,
                memberName,
                sampleAgentCard(),
                "desc",
                "prompt",
                status,
                ExecutionStatus.IDLE
        );
    }

    private static TeamMemberSpec teammate(String memberName) {
        TeamMemberSpec spec = new TeamMemberSpec();
        spec.setMemberName(memberName);
        spec.setDisplayName("Developer");
        spec.setPersona("Builds features");
        spec.setPromptHint("Implement tasks");
        spec.setRoleType(TeamRole.TEAMMATE);
        return spec;
    }

    private static TeamMemberSpec human(String memberName, String displayName) {
        TeamMemberSpec spec = new TeamMemberSpec();
        spec.setMemberName(memberName);
        spec.setDisplayName(displayName);
        spec.setPersona("Human collaborator");
        spec.setRoleType(TeamRole.HUMAN_AGENT);
        return spec;
    }

    private static AgentCard sampleAgentCard() {
        AgentCard card = new AgentCard();
        card.setName("TestAgent");
        card.setDescription("A test agent");
        return card;
    }
}
