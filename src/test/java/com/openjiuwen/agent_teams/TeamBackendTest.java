/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams;

import com.openjiuwen.agent_teams.agent.TeamMember;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.TeamRuntimeContext;
import com.openjiuwen.agent_teams.schema.TeamSpec;
import com.openjiuwen.agent_teams.schema.message.MessageRecord;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.schema.task.TaskDetail;
import com.openjiuwen.agent_teams.schema.task.TaskRecord;
import com.openjiuwen.agent_teams.schema.task.TaskStatus;
import com.openjiuwen.agent_teams.schema.task.TaskSummary;
import com.openjiuwen.agent_teams.tools.Team;
import com.openjiuwen.agent_teams.tools.TeamBackend;
import com.openjiuwen.agent_teams.tools.database.DatabaseConfig;
import com.openjiuwen.agent_teams.tools.database.DatabaseType;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.rails.SecurityRail;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's tests.unit_tests.agent_teams.test_team.
 * Unit tests for AgentTeam module.
 */
class TeamBackendTest {

    @Test
    void teamRoleEnumValues() {
        TeamRole[] roles = TeamRole.values();
        assertArrayEquals(new TeamRole[] {TeamRole.LEADER, TeamRole.TEAMMATE, TeamRole.HUMAN_AGENT}, roles);
    }

    @Test
    void executionStatusEnumValues() {
        ExecutionStatus[] statuses = ExecutionStatus.values();
        assertArrayEquals(
                new ExecutionStatus[] {
                        ExecutionStatus.IDLE,
                        ExecutionStatus.STARTING,
                        ExecutionStatus.RUNNING,
                        ExecutionStatus.CANCEL_REQUESTED,
                        ExecutionStatus.CANCELLING,
                        ExecutionStatus.CANCELLED,
                        ExecutionStatus.COMPLETING,
                        ExecutionStatus.COMPLETED,
                        ExecutionStatus.FAILED,
                        ExecutionStatus.TIMED_OUT
                },
                statuses
        );
    }

    @Test
    void taskStatusEnumValues() {
        com.openjiuwen.agent_teams.schema.TaskStatus[] statuses =
                com.openjiuwen.agent_teams.schema.TaskStatus.values();
        assertEquals(com.openjiuwen.agent_teams.schema.TaskStatus.PENDING,
                com.openjiuwen.agent_teams.schema.TaskStatus.fromValue("pending"));
        assertTrue(statuses.length > 0);
    }

    @Test
    void memberStatusEnumValues() {
        MemberStatus[] statuses = MemberStatus.values();
        assertArrayEquals(
                new MemberStatus[] {
                        MemberStatus.UNSTARTED,
                        MemberStatus.READY,
                        MemberStatus.BUSY,
                        MemberStatus.RESTARTING,
                        MemberStatus.SHUTDOWN_REQUESTED,
                        MemberStatus.SHUTDOWN,
                        MemberStatus.ERROR
                },
                statuses
        );
    }

    @Test
    void testAgentTeamInit() {
        TeamBackend backend = backendWithTeam("test_team");

        assertEquals("test_team", backend.getTeamName());
        assertEquals("leader1", backend.getMemberName());
        assertTrue(backend.isLeader());
        assertNotNull(backend.getTaskManager());
        assertNotNull(backend.getMessageManager());
    }

    @Test
    void testAgentTeamWithOptionalFields() {
        TeamBackend backend = new TeamBackend("team_with_optional", "leader1", true, MemberMode.BUILD_MODE, List.of());
        backend.getStore().createTeam("Optional Team", "Team description", "leader1", "Leader", "Leader desc");

        Team teamInfo = backend.getTeamInfo();

        assertNotNull(teamInfo);
        assertEquals("Team description", teamInfo.getDesc());
        assertEquals("Optional Team", teamInfo.getDisplayName());
    }

    @Test
    void testSpawnMemberSuccess() {
        TeamBackend backend = backendWithTeam("spawn_success");

        TeamMember member = spawn(backend, "member1");

        assertNotNull(member);
        assertSame(member, backend.getMember("member1"));
    }

    @Test
    void testSpawnMemberCreatesInDatabase() {
        TeamBackend backend = backendWithTeam("spawn_database");

        backend.spawnMember("member1", "Member One", sampleAgentCard(), null, null,
                MemberStatus.UNSTARTED, ExecutionStatus.IDLE);
        TeamMember member = backend.getStore().getMembers().get("member1");

        assertNotNull(member);
        assertEquals("member1", member.getMemberName());
        assertEquals("Member One", member.getDisplayName());
        assertEquals("spawn_database", member.getTeamName());
        assertEquals(MemberStatus.UNSTARTED, member.getStatus());
        assertEquals(ExecutionStatus.IDLE, member.getExecutionStatus());
    }

    @Test
    void testSpawnMemberMultiple() {
        TeamBackend backend = backendWithTeam("spawn_multiple");

        spawn(backend, "member1");
        spawn(backend, "member2");

        assertEquals(2, backend.listMembers().size());
    }

    @Test
    void testSpawnMemberDuplicateReturnsReason() {
        TeamBackend backend = backendWithTeam("spawn_duplicate");

        TeamMember first = backend.spawnMember("member1", "Member One", sampleAgentCard(), null, null,
                MemberStatus.UNSTARTED, ExecutionStatus.IDLE);
        TeamMember second = backend.spawnMember("member1", "Duplicate", sampleAgentCard(), null, null,
                MemberStatus.READY, ExecutionStatus.RUNNING);

        assertSame(first, second);
        assertEquals("Member One", second.getDisplayName());
        assertEquals(MemberStatus.UNSTARTED, second.getStatus());
    }

    @Test
    void testSpawnMemberWithMinimalArgs() {
        TeamBackend backend = backendWithTeam("spawn_minimal");

        TeamMember member = backend.spawnMember("member1", "Member One", sampleAgentCard(), null, null, null, null);

        assertNotNull(member);
        assertEquals(1, backend.listMembers().size());
        assertEquals("member1", backend.listMembers().get(0).getMemberName());
        assertEquals(MemberStatus.UNSTARTED, member.getStatus());
        assertEquals(ExecutionStatus.IDLE, member.getExecutionStatus());
    }

    @Test
    void testApprovePlanSuccess() {
        TeamBackend backend = backendWithTeam("approve_plan_success");
        spawn(backend, "member1");

        boolean result = backend.approvePlan("member1", true, "Plan looks good");

        assertTrue(result);
    }

    @Test
    void testApprovePlanSendsMessage() {
        TeamBackend backend = backendWithTeam("approve_plan_message");
        spawn(backend, "member1");
        backend.createTask("Plan task", "Task content", "plan-task", List.of());
        assertTrue(backend.claimTask("plan-task", "member1"));

        assertTrue(backend.approvePlan("member1", true, "Great plan!"));

        assertEquals(TaskStatus.PLAN_APPROVED, backend.getTask("plan-task").getStatus());
        List<MessageRecord> messages = backend.getMessages("member1", false, "leader1");
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).getContent().contains("APPROVED"));
        assertTrue(messages.get(0).getContent().contains("1 task(s)"));
        assertTrue(messages.get(0).getContent().contains("Great plan!"));
        assertEquals(ExecutionStatus.IDLE, backend.getMember("member1").getExecutionStatus());
    }

    @Test
    void testRejectPlanSendsMessage() {
        TeamBackend backend = backendWithTeam("reject_plan_message");
        spawn(backend, "member1");

        assertTrue(backend.approvePlan("member1", false, "Please revise"));

        List<MessageRecord> messages = backend.getMessages("member1", false, "leader1");
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).getContent().contains("REJECTED"));
        assertTrue(messages.get(0).getContent().contains("Please revise"));
        assertEquals(ExecutionStatus.IDLE, backend.getMember("member1").getExecutionStatus());
    }

    @Test
    void testApprovePlanMemberNotFound() {
        TeamBackend backend = backendWithTeam("approve_plan_missing");

        boolean result = backend.approvePlan("nonexistent_member", true, null);

        assertFalse(result);
    }

    @Test
    void testApprovePlanWithoutFeedback() {
        TeamBackend backend = backendWithTeam("approve_plan_no_feedback");
        spawn(backend, "member1");

        boolean result = backend.approvePlan("member1", true, null);

        assertTrue(result);
        List<MessageRecord> messages = backend.getMessages("member1", false, "leader1");
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).getContent().contains("Your plan has been APPROVED"));
        assertFalse(messages.get(0).getContent().contains("Feedback:"));
        assertEquals(ExecutionStatus.IDLE, backend.getMember("member1").getExecutionStatus());
    }

    @Test
    void testApproveToolSuccess() {
        TeamBackend backend = backendWithTeam("approve_tool_success");
        spawn(backend, "member1");
        Session session = backend.getMemberSession("member1");
        session.updateState(Map.of(SecurityRail.PENDING_APPROVAL_STATE_KEY, Map.of("tool_name", "shell")));

        boolean result = backend.approveTool("member1", "call-1", true, "Looks safe", true);

        assertTrue(result);
        assertEquals(ExecutionStatus.IDLE, backend.getMember("member1").getExecutionStatus());
        Map<?, ?> pending = assertInstanceOf(Map.class, session.getState(SecurityRail.PENDING_APPROVAL_STATE_KEY));
        Map<?, ?> decision = assertInstanceOf(Map.class, pending.get("decision"));
        assertEquals("shell", pending.get("tool_name"));
        assertEquals("leader1", pending.get("approved_by"));
        assertEquals("call-1", pending.get("tool_call_id"));
        assertEquals(Boolean.TRUE, decision.get("approved"));
        assertEquals(Boolean.TRUE, decision.get("auto_confirm"));
        assertEquals("Looks safe", decision.get("feedback"));
    }

    @Test
    void testApproveToolMemberNotFound() {
        TeamBackend backend = backendWithTeam("approve_tool_missing");

        boolean result = backend.approveTool("missing", "call-1", false, null);

        assertFalse(result);
    }

    @Test
    void testShutdownMemberSuccess() {
        TeamBackend backend = backendWithTeam("shutdown_success");
        spawn(backend, "member1", MemberStatus.READY);

        boolean result = backend.shutdownMember("member1", false);

        assertTrue(result);
    }

    @Test
    void testShutdownMemberUpdatesStatus() {
        TeamBackend backend = backendWithTeam("shutdown_status");
        spawn(backend, "member1", MemberStatus.READY);

        backend.shutdownMember("member1", false);

        assertEquals(MemberStatus.SHUTDOWN_REQUESTED, backend.getMember("member1").getStatus());
        assertEquals(ExecutionStatus.IDLE, backend.getMember("member1").getExecutionStatus());
    }

    @Test
    void testShutdownMemberAlreadyShutdown() {
        TeamBackend backend = backendWithTeam("shutdown_existing");
        spawn(backend, "member1", MemberStatus.SHUTDOWN);

        boolean result = backend.shutdownMember("member1", false);

        assertTrue(result);
    }

    @Test
    void testShutdownMemberNotFound() {
        TeamBackend backend = backendWithTeam("shutdown_missing");

        boolean result = backend.shutdownMember("nonexistent_member", false);

        assertFalse(result);
    }

    @Test
    void testCancelMemberSuccess() {
        TeamBackend backend = backendWithTeam("cancel_success");
        spawn(backend, "member1");

        boolean result = backend.cancelMember("member1");

        assertTrue(result);
    }

    @Test
    void testCancelMemberWhenBusy() {
        TeamBackend backend = backendWithTeam("cancel_busy");
        spawn(backend, "member1", MemberStatus.BUSY);

        boolean result = backend.cancelMember("member1");

        assertTrue(result);
    }

    @Test
    void testCancelMemberWhenNotBusy() {
        TeamBackend backend = backendWithTeam("cancel_not_busy");
        spawn(backend, "member1", MemberStatus.READY);

        boolean result = backend.cancelMember("member1");

        assertTrue(result);
    }

    @Test
    void testCancelMemberNotFound() {
        TeamBackend backend = backendWithTeam("cancel_missing");

        boolean result = backend.cancelMember("nonexistent_member");

        assertFalse(result);
    }

    @Test
    void testCancelMemberResetsClaimedTasks() {
        TeamBackend backend = backendWithTeam("cancel_resets_tasks");
        spawn(backend, "member1", MemberStatus.BUSY);
        TaskRecord task1 = backend.createTask("Task 1", "Content 1", "task1", List.of());
        TaskRecord task2 = backend.createTask("Task 2", "Content 2", "task2", List.of());
        TaskRecord task3 = backend.createTask("Task 3", "Content 3", "task3", List.of());
        assertTrue(backend.claimTask(task1.getTaskId(), "member1"));
        assertTrue(backend.claimTask(task2.getTaskId(), "member1"));

        boolean result = backend.cancelMember("member1");

        assertTrue(result);
        assertEquals(TaskStatus.PENDING, backend.getTask(task1.getTaskId()).getStatus());
        assertNull(backend.getTask(task1.getTaskId()).getAssignee());
        assertEquals(TaskStatus.PENDING, backend.getTask(task2.getTaskId()).getStatus());
        assertNull(backend.getTask(task2.getTaskId()).getAssignee());
        assertEquals(TaskStatus.PENDING, backend.getTask(task3.getTaskId()).getStatus());
        assertNull(backend.getTask(task3.getTaskId()).getAssignee());
    }

    @Test
    void testCancelMemberNoClaimedTasks() {
        TeamBackend backend = backendWithTeam("cancel_no_claimed");
        spawn(backend, "member1", MemberStatus.BUSY);
        TaskRecord task1 = backend.createTask("Task 1", "Content 1", "task1", List.of());
        TaskRecord task2 = backend.createTask("Task 2", "Content 2", "task2", List.of());

        boolean result = backend.cancelMember("member1");

        assertTrue(result);
        assertEquals(TaskStatus.PENDING, backend.getTask(task1.getTaskId()).getStatus());
        assertNull(backend.getTask(task1.getTaskId()).getAssignee());
        assertEquals(TaskStatus.PENDING, backend.getTask(task2.getTaskId()).getStatus());
        assertNull(backend.getTask(task2.getTaskId()).getAssignee());
    }

    @Test
    void testCleanTeamSuccess() {
        TeamBackend backend = backendWithTeam("clean_success");
        spawn(backend, "member1", MemberStatus.SHUTDOWN);
        spawn(backend, "member2", MemberStatus.SHUTDOWN);

        assertTrue(backend.canCleanTeam());
        List<String> removed = backend.cleanTeam();

        assertNotNull(removed);
        assertNull(backend.getTeamInfo());
        assertTrue(backend.listMembers().isEmpty());
    }

    @Test
    void testCleanTeamFailsWhenMembersNotShutdown() {
        TeamBackend backend = backendWithTeam("clean_not_shutdown");
        spawn(backend, "member1", MemberStatus.BUSY);

        boolean canClean = backend.canCleanTeam();

        assertFalse(canClean);
        assertNotNull(backend.getTeamInfo());
    }

    @Test
    void testCleanTeamPartialShutdown() {
        TeamBackend backend = backendWithTeam("clean_partial");
        spawn(backend, "member1", MemberStatus.SHUTDOWN);
        spawn(backend, "member2", MemberStatus.READY);

        boolean canClean = backend.canCleanTeam();

        assertFalse(canClean);
        assertNotNull(backend.getMember("member1"));
        assertNotNull(backend.getMember("member2"));
    }

    @Test
    void testGetMemberSuccess() {
        TeamBackend backend = backendWithTeam("get_member_success");
        spawn(backend, "member1");

        TeamMember member = backend.getMember("member1");

        assertNotNull(member);
        assertEquals("member1", member.getMemberName());
        assertEquals("Member One", member.getDisplayName());
        assertEquals("get_member_success", member.getTeamName());
    }

    @Test
    void testGetMemberNotFound() {
        TeamBackend backend = backendWithTeam("get_member_missing");

        TeamMember member = backend.getMember("nonexistent_member");

        assertNull(member);
    }

    @Test
    void testListMembersEmpty() {
        TeamBackend backend = backendWithTeam("list_empty");

        List<TeamMember> members = backend.listMembers();

        assertTrue(members.isEmpty());
    }

    @Test
    void testListMembersWithMembers() {
        TeamBackend backend = backendWithTeam("list_members");
        spawn(backend, "member1");
        spawn(backend, "member2");

        List<TeamMember> members = backend.listMembers();

        assertEquals(2, members.size());
        assertTrue(members.stream().map(TeamMember::getMemberName).toList().contains("member1"));
        assertTrue(members.stream().map(TeamMember::getMemberName).toList().contains("member2"));
    }

    @Test
    void testGetTeamInfoSuccess() {
        TeamBackend backend = backendWithTeam("get_team_info");

        Team teamInfo = backend.getTeamInfo();

        assertNotNull(teamInfo);
        assertEquals("get_team_info", teamInfo.getTeamName());
        assertEquals("Test Team", teamInfo.getDisplayName());
        assertEquals("leader1", teamInfo.getLeaderMemberName());
    }

    @Test
    void testGetTeamInfoWithOptionalFields() {
        TeamBackend backend = new TeamBackend("full_team", "leader1", true, MemberMode.BUILD_MODE, List.of());
        backend.getStore().createTeam("Full Team", "Full description", "leader1", "Leader", "Leader desc");

        Team teamInfo = backend.getTeamInfo();

        assertNotNull(teamInfo);
        assertEquals("full_team", teamInfo.getTeamName());
        assertEquals("Full Team", teamInfo.getDisplayName());
        assertEquals("leader1", teamInfo.getLeaderMemberName());
        assertEquals("Full description", teamInfo.getDesc());
        assertTrue(teamInfo.getCreated() > 0L);
    }

    @Test
    void testGetTeamInfoNotFound() {
        TeamBackend backend = new TeamBackend("nonexistent_team", "leader1", true, MemberMode.BUILD_MODE, List.of());

        Team teamInfo = backend.getTeamInfo();

        assertNull(teamInfo);
    }

    @Test
    void testCancelTaskSuccess() {
        TeamBackend backend = backendWithTeam("cancel_task_success");
        backend.createTask("Test Task", "Task content", "task1", List.of());

        boolean result = backend.cancelTask("task1");

        assertTrue(result);
        assertEquals(TaskStatus.CANCELLED, backend.getTask("task1").getStatus());
    }

    @Test
    void testCancelTaskNotFound() {
        TeamBackend backend = backendWithTeam("cancel_task_missing");

        boolean result = backend.cancelTask("nonexistent_task");

        assertFalse(result);
    }

    @Test
    void testCancelTaskAlreadyCancelled() {
        TeamBackend backend = backendWithTeam("cancel_task_existing");
        backend.createTask("Test Task", "Task content", "task1", List.of());
        assertTrue(backend.cancelTask("task1"));

        boolean result = backend.cancelTask("task1");

        assertTrue(result);
        assertEquals(TaskStatus.CANCELLED, backend.getTask("task1").getStatus());
    }

    @Test
    void testCancelTaskWithAssigneeSendsNotification() {
        TeamBackend backend = backendWithTeam("cancel_task_assignee");
        spawn(backend, "member1", MemberStatus.READY);
        backend.createTask("Test Task", "Task content", "task1", List.of());
        assertTrue(backend.claimTask("task1", "member1"));

        boolean result = backend.cancelTask("task1");

        assertTrue(result);
        assertEquals(TaskStatus.CANCELLED, backend.getTask("task1").getStatus());
        assertEquals("member1", backend.getTask("task1").getAssignee());
    }

    @Test
    void testCancelTaskWithoutAssigneeNoNotification() {
        TeamBackend backend = backendWithTeam("cancel_task_no_assignee");
        backend.createTask("Test Task", "Task content", "task1", List.of());

        boolean result = backend.cancelTask("task1");

        assertTrue(result);
        assertTrue(backend.getMessages(null, false, null).isEmpty());
    }

    @Test
    void testCancelAllTasksSuccess() {
        TeamBackend backend = backendWithTeam("cancel_all_success");
        backend.createTask("Task 1", "Content 1", "task1", List.of());
        backend.createTask("Task 2", "Content 2", "task2", List.of());
        backend.createTask("Task 3", "Content 3", "task3", List.of());

        int count = backend.cancelAllTasks();

        assertEquals(3, count);
        assertEquals(TaskStatus.CANCELLED, backend.getTask("task1").getStatus());
        assertEquals(TaskStatus.CANCELLED, backend.getTask("task2").getStatus());
        assertEquals(TaskStatus.CANCELLED, backend.getTask("task3").getStatus());
        assertEquals(1, backend.getBroadcastMessages(false, "leader1").size());
        assertTrue(backend.getBroadcastMessages(false, "leader1").get(0).getContent()
                .contains("All tasks (3) have been cancelled"));
    }

    @Test
    void testCancelAllTasksMixedStatus() {
        TeamBackend backend = backendWithTeam("cancel_all_mixed");
        backend.createTask("Task 1", "Content 1", "task1", List.of());
        backend.createTask("Task 2", "Content 2", "task2", List.of());
        backend.createTask("Task 3", "Content 3", "task3", List.of());
        backend.createTask("Task 4", "Content 4", "task4", List.of());
        spawn(backend, "member1", MemberStatus.READY);
        assertTrue(backend.claimTask("task2", "member1"));
        assertTrue(backend.cancelTask("task3"));
        assertTrue(backend.completeTask("task4"));

        int count = backend.cancelAllTasks();

        assertEquals(2, count);
        assertEquals(TaskStatus.CANCELLED, backend.getTask("task1").getStatus());
        assertEquals(TaskStatus.CANCELLED, backend.getTask("task2").getStatus());
        assertEquals(TaskStatus.CANCELLED, backend.getTask("task3").getStatus());
        assertEquals(TaskStatus.COMPLETED, backend.getTask("task4").getStatus());
    }

    @Test
    void testCancelAllTasksNoActiveTasks() {
        TeamBackend backend = backendWithTeam("cancel_all_none_active");
        backend.createTask("Task 1", "Content 1", "task1", List.of());
        backend.createTask("Task 2", "Content 2", "task2", List.of());
        assertTrue(backend.cancelTask("task1"));
        assertTrue(backend.completeTask("task2"));

        int count = backend.cancelAllTasks();

        assertEquals(0, count);
        assertTrue(backend.getBroadcastMessages(false, "leader1").isEmpty());
    }

    @Test
    void testCancelAllTasksEmptyTeam() {
        TeamBackend backend = backendWithTeam("cancel_all_empty");

        int count = backend.cancelAllTasks();

        assertEquals(0, count);
    }

    @Test
    void testRuntimeContextWithDatabaseConfig() {
        DatabaseConfig dbConfig = new DatabaseConfig(DatabaseType.SQLITE, ":memory:", 30, false);
        TeamRuntimeContext context = runtimeContextWithMetadata(Map.of("db_config", dbConfig));

        assertSame(dbConfig, context.getMetadata().get("db_config"));
        assertEquals(DatabaseType.SQLITE, dbConfig.getDbType());
    }

    @Test
    void testRuntimeContextWithMemoryDatabaseConfig() {
        DatabaseConfig dbConfig = DatabaseConfig.inMemory();
        TeamRuntimeContext context = runtimeContextWithMetadata(Map.of("db_config", dbConfig));

        assertSame(dbConfig, context.getMetadata().get("db_config"));
        assertEquals(":memory:", dbConfig.getConnectionString());
    }

    @Test
    void testRuntimeContextDefaultDatabaseConfig() {
        TeamRuntimeContext context = new TeamRuntimeContext();

        assertEquals(TeamRole.LEADER, context.getRole());
        assertTrue(context.getMetadata().isEmpty());
    }

    @Test
    void buildTeamRegistersLeaderAndPredefinedMembers() {
        TeamBackend backend = new TeamBackend("build_team", "leader1", true, MemberMode.PLAN_MODE,
                List.of(teammate("member1"), teammate("member2")));

        backend.buildTeam("Build Team", "Team desc", "Leader", "Leader desc");

        assertNotNull(backend.getTeamInfo());
        assertEquals(MemberStatus.BUSY, backend.getMember("leader1").getStatus());
        assertEquals(ExecutionStatus.RUNNING, backend.getMember("leader1").getExecutionStatus());
        assertEquals(MemberStatus.UNSTARTED, backend.getMember("member1").getStatus());
        assertEquals(MemberStatus.UNSTARTED, backend.getMember("member2").getStatus());
        assertEquals(2, backend.listMembers().size());
    }

    @Test
    void buildTeamWithHumanAgentEnablesHitt() {
        TeamBackend backend = new TeamBackend("build_hitt", "leader1", true, MemberMode.BUILD_MODE,
                List.of(human("human_pm", "Human PM")));

        backend.buildTeam("Human Team", "Team desc", "Leader", "Leader desc");

        assertTrue(backend.hittEnabled());
        assertTrue(backend.isHumanAgent("human_pm"));
        assertEquals(List.of("human_pm"), backend.humanAgentNames());
    }

    @Test
    void startupStartsUnstartedMembers() {
        TeamBackend backend = backendWithTeam("startup_members");
        spawn(backend, "member1", MemberStatus.UNSTARTED);
        spawn(backend, "member2", MemberStatus.READY);

        List<String> started = backend.startup();

        assertEquals(List.of("member1"), started);
        assertEquals(MemberStatus.READY, backend.getMember("member1").getStatus());
        assertEquals(MemberStatus.READY, backend.getMember("member2").getStatus());
    }

    @Test
    void taskDetailIncludesDependenciesAndBlocks() {
        TeamBackend backend = backendWithTeam("task_dependencies");
        backend.createTask("Task 1", "Content 1", "task1", List.of());
        backend.createTask("Task 2", "Content 2", "task2", List.of("task1"));

        TaskDetail task2 = backend.getTask("task2");
        TaskDetail task1 = backend.getTask("task1");

        assertEquals(TaskStatus.BLOCKED, task2.getStatus());
        assertEquals(List.of("task1"), task2.getBlockedBy());
        assertEquals(List.of("task2"), task1.getBlocks());
    }

    @Test
    void addBlockedByRejectsCircularDependency() {
        TeamBackend backend = backendWithTeam("task_cycle");
        backend.createTask("Task 1", "Content 1", "task1", List.of());
        backend.createTask("Task 2", "Content 2", "task2", List.of("task1"));

        boolean result = backend.addBlockedBy("task1", List.of("task2"));

        assertFalse(result);
        assertTrue(backend.getTask("task1").getBlockedBy().isEmpty());
    }

    @Test
    void completeTaskUnblocksDependents() {
        TeamBackend backend = backendWithTeam("complete_unblocks");
        backend.createTask("Task 1", "Content 1", "task1", List.of());
        backend.createTask("Task 2", "Content 2", "task2", List.of("task1"));

        assertTrue(backend.completeTask("task1"));

        assertEquals(TaskStatus.COMPLETED, backend.getTask("task1").getStatus());
        assertEquals(TaskStatus.PENDING, backend.getTask("task2").getStatus());
        assertTrue(backend.getTask("task2").getBlockedBy().isEmpty());
    }

    @Test
    void listTasksCanFilterByStatus() {
        TeamBackend backend = backendWithTeam("list_tasks_status");
        backend.createTask("Task 1", "Content 1", "task1", List.of());
        backend.createTask("Task 2", "Content 2", "task2", List.of("task1"));

        List<TaskSummary> pending = backend.listTasks(TaskStatus.PENDING);
        List<TaskSummary> blocked = backend.listTasks(TaskStatus.BLOCKED);

        assertEquals(1, pending.size());
        assertEquals("task1", pending.get(0).getTaskId());
        assertEquals(1, blocked.size());
        assertEquals("task2", blocked.get(0).getTaskId());
    }

    @Test
    void sendAndReadDirectMessages() {
        TeamBackend backend = backendWithTeam("messages_direct");

        String messageId = backend.sendMessage("hello", "member1", "leader1");
        List<MessageRecord> messages = backend.getMessages("member1", true, "leader1");

        assertEquals(1, messages.size());
        assertEquals(messageId, messages.get(0).getMessageId());
        assertEquals("hello", messages.get(0).getContent());
        assertFalse(messages.get(0).isBroadcast());
        assertTrue(backend.getMessageManager().markMessageRead(messageId));
        assertTrue(backend.getMessages("member1", true, "leader1").isEmpty());
    }

    @Test
    void broadcastMessagesAreListedSeparately() {
        TeamBackend backend = backendWithTeam("messages_broadcast");

        String messageId = backend.broadcastMessage("team update", "leader1");
        List<MessageRecord> messages = backend.getBroadcastMessages(true, "leader1");

        assertEquals(1, messages.size());
        assertEquals(messageId, messages.get(0).getMessageId());
        assertTrue(messages.get(0).isBroadcast());
        assertNull(messages.get(0).getToMemberName());
    }

    @Test
    void sharedStoreIsVisibleAcrossBackendsForSameTeam() {
        TeamBackend leader = backendWithTeam("shared_store");
        TeamBackend observer = new TeamBackend("shared_store", "observer", false, MemberMode.BUILD_MODE, List.of());
        spawn(leader, "member1");

        assertSame(leader.getMember("member1"), observer.getMember("member1"));
        assertEquals("Test Team", observer.getTeamInfo().getDisplayName());
    }

    @Test
    void forceCleanTeamClearsSharedState() {
        TeamBackend backend = backendWithTeam("force_clean");
        spawn(backend, "member1", MemberStatus.READY);
        backend.createTask("Task", "Content", "task1", List.of());
        backend.sendMessage("hello", "member1", "leader1");

        assertTrue(backend.forceCleanTeam(false));

        assertNull(backend.getTeamInfo());
        assertTrue(backend.listMembers().isEmpty());
        assertTrue(backend.listTasks().isEmpty());
        assertTrue(backend.getMessages(null, false, null).isEmpty());
    }

    private static TeamBackend backendWithTeam(String teamName) {
        TeamBackend backend = new TeamBackend(teamName, "leader1", true, MemberMode.BUILD_MODE, List.of());
        backend.getStore().createTeam("Test Team", null, "leader1", "Leader", "Leader desc");
        return backend;
    }

    private static TeamMember spawn(TeamBackend backend, String memberName) {
        return spawn(backend, memberName, MemberStatus.UNSTARTED);
    }

    private static TeamMember spawn(TeamBackend backend, String memberName, MemberStatus status) {
        return backend.spawnMember(
                memberName,
                "member1".equals(memberName) ? "Member One" : memberName,
                sampleAgentCard(),
                "Test description",
                "Member prompt",
                status,
                ExecutionStatus.IDLE
        );
    }

    private static AgentCard sampleAgentCard() {
        AgentCard card = new AgentCard();
        card.setName("TestAgent");
        card.setDescription("A test agent");
        return card;
    }

    private static TeamMemberSpec teammate(String memberName) {
        TeamMemberSpec spec = new TeamMemberSpec();
        spec.setMemberName(memberName);
        spec.setDisplayName(memberName);
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

    private static TeamRuntimeContext runtimeContextWithMetadata(Map<String, Object> metadata) {
        TeamRuntimeContext context = new TeamRuntimeContext();
        TeamSpec spec = new TeamSpec();
        spec.setTeamName("test_team");
        spec.setDisplayName("Test Team");
        context.setRole(TeamRole.LEADER);
        context.setMemberName("leader1");
        context.setTeamSpec(spec);
        context.setMetadata(new LinkedHashMap<>(metadata));
        return context;
    }
}
