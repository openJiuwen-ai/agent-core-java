/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams;

import com.openjiuwen.agent_teams.agent.TeamMember;
import com.openjiuwen.agent_teams.schema.task.TaskDetail;
import com.openjiuwen.agent_teams.schema.task.TaskRecord;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.tools.ApprovePlanTool;
import com.openjiuwen.agent_teams.tools.ApproveToolCallTool;
import com.openjiuwen.agent_teams.tools.BuildTeamTool;
import com.openjiuwen.agent_teams.tools.ClaimTaskTool;
import com.openjiuwen.agent_teams.tools.CleanTeamTool;
import com.openjiuwen.agent_teams.tools.CreateTaskTool;
import com.openjiuwen.agent_teams.tools.ListMembersTool;
import com.openjiuwen.agent_teams.tools.MappedToolOutput;
import com.openjiuwen.agent_teams.tools.SendMessageTool;
import com.openjiuwen.agent_teams.tools.ShutdownMemberTool;
import com.openjiuwen.agent_teams.tools.SpawnMemberTool;
import com.openjiuwen.agent_teams.tools.TeamBackend;
import com.openjiuwen.agent_teams.tools.TeamBackendRegistry;
import com.openjiuwen.agent_teams.tools.TeamTool;
import com.openjiuwen.agent_teams.tools.TeamToolOutput;
import com.openjiuwen.agent_teams.tools.ToolLocales;
import com.openjiuwen.agent_teams.tools.ToolOutput;
import com.openjiuwen.agent_teams.tools.UpdateTaskTool;
import com.openjiuwen.agent_teams.tools.ViewTaskTool;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_teams.test_team_tools}.
 *
 * <p>The Python file also contains 14 {@code @pytest.mark.skip} placeholder
 * classes for removed tools; those are intentionally not translated into
 * active Java tests.</p>
 */
class TestTeamTools {

    @BeforeEach
    void resetBackendRegistry() {
        TeamBackendRegistry.clear();
    }

    @Nested
    @DisplayName("TestBuildTeamTool")
    class TestBuildTeamTool {
        @Test
        void testInitialization() {
            TeamBackend team = agentTeamWithoutTeam();
            BuildTeamTool tool = new BuildTeamTool(team);

            assertEquals("build_team", tool.getCard().getName());
            assertEquals("team.build_team", tool.getCard().getId());
            assertSame(team, tool.getTeam());
        }

        @Test
        void testInvokeSuccess() throws Exception {
            TeamBackend team = agentTeamWithoutTeam();
            TeamToolOutput result = invoke(new BuildTeamTool(team), Map.of(
                    "display_name", "My Team",
                    "team_desc", "Test team description",
                    "leader_display_name", "Lead",
                    "leader_desc", "Project manager"
            ));

            assertTrue(result.isSuccess());
            assertNull(result.getError());
            assertEquals("My Team", team.getStore().getDisplayName());
            assertEquals("Test team description", team.getStore().getDesc());
            TeamMember leader = team.getMember("leader1");
            assertNotNull(leader);
            assertEquals("Lead", leader.getDisplayName());
        }

        @Test
        void testInvokeWithMinimalArgs() throws Exception {
            TeamBackend team = agentTeamWithoutTeam();
            TeamToolOutput result = invoke(new BuildTeamTool(team), Map.of(
                    "display_name", "Minimal Team",
                    "team_desc", "A minimal team",
                    "leader_display_name", "Lead",
                    "leader_desc", "PM"
            ));

            assertTrue(result.isSuccess());
            assertEquals("Minimal Team", team.getStore().getDisplayName());
            assertEquals("A minimal team", team.getStore().getDesc());
        }
    }

    @Nested
    @DisplayName("TestCleanTeamTool")
    class TestCleanTeamTool {
        @Test
        void testInitialization() {
            TeamBackend team = agentTeam();
            CleanTeamTool tool = new CleanTeamTool(team);

            assertEquals("clean_team", tool.getCard().getName());
            assertEquals("team.clean_team", tool.getCard().getId());
            assertSame(team, tool.getTeam());
        }

        @Test
        void testInvokeSuccess() throws Exception {
            TeamBackend team = agentTeam();
            spawnReady(team, "member1");
            team.shutdownMember("member1", true);

            TeamToolOutput result = invoke(new CleanTeamTool(team), Map.of());

            assertTrue(result.isSuccess());
            assertEquals("test_team", data(result).get("team_name"));
        }

        @Test
        void testInvokeFailsWhenMembersNotShutdown() throws Exception {
            TeamBackend team = agentTeam();
            spawnReady(team, "member1");

            TeamToolOutput result = invoke(new CleanTeamTool(team), Map.of());

            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("shutdown_member"));
        }
    }

    @Nested
    @DisplayName("TestSpawnMemberTool")
    class TestSpawnMemberTool {
        @Test
        void testInitialization() {
            TeamBackend team = agentTeam();
            SpawnMemberTool tool = new SpawnMemberTool(team);

            assertEquals("spawn_member", tool.getCard().getName());
            assertEquals("team.spawn_member", tool.getCard().getId());
            assertSame(team, tool.getTeam());
        }

        @Test
        void testInvokeSuccess() throws Exception {
            TeamToolOutput result = invoke(new SpawnMemberTool(agentTeam()), Map.of(
                    "member_name", "member1",
                    "display_name", "Member One",
                    "desc", "Test member",
                    "prompt", "Member prompt"
            ));

            assertTrue(result.isSuccess());
            assertNull(result.getError());
        }
    }

    @Nested
    @DisplayName("TestShutdownMemberTool")
    class TestShutdownMemberTool {
        @Test
        void testInitialization() {
            TeamBackend team = agentTeam();
            ShutdownMemberTool tool = new ShutdownMemberTool(team);

            assertEquals("shutdown_member", tool.getCard().getName());
            assertEquals("team.shutdown_member", tool.getCard().getId());
            assertSame(team, tool.getTeam());
        }

        @Test
        void testInvokeSuccess() throws Exception {
            TeamBackend team = agentTeam();
            spawnReady(team, "member1");

            TeamToolOutput result = invoke(new ShutdownMemberTool(team), Map.of("member_name", "member1", "force", false));

            assertTrue(result.isSuccess());
            assertNull(result.getError());
        }

        @Test
        void testInvokeWithForce() throws Exception {
            TeamBackend team = agentTeam();
            spawnReady(team, "member1");

            TeamToolOutput result = invoke(new ShutdownMemberTool(team), Map.of("member_name", "member1", "force", true));

            assertTrue(result.isSuccess());
        }

        @Test
        void testInvokeMemberNotFound() throws Exception {
            TeamToolOutput result = invoke(new ShutdownMemberTool(agentTeam()), Map.of("member_name", "nonexistent"));

            assertFalse(result.isSuccess());
            assertNotNull(result.getError());
        }
    }

    @Nested
    @DisplayName("TestApprovePlanTool")
    class TestApprovePlanTool {
        @Test
        void testInitialization() {
            TeamBackend team = agentTeam();
            ApprovePlanTool tool = new ApprovePlanTool(team);

            assertEquals("approve_plan", tool.getCard().getName());
            assertEquals("team.approve_plan", tool.getCard().getId());
            assertSame(team, tool.getTeam());
        }

        @Test
        void testInvokeApprove() throws Exception {
            TeamBackend team = agentTeam();
            spawnReady(team, "member1");

            TeamToolOutput result = invoke(new ApprovePlanTool(team), Map.of(
                    "member_name", "member1",
                    "approved", true,
                    "feedback", "Great plan!"
            ));

            assertTrue(result.isSuccess());
            assertNull(result.getError());
        }

        @Test
        void testInvokeReject() throws Exception {
            TeamBackend team = agentTeam();
            spawnReady(team, "member1");

            TeamToolOutput result = invoke(new ApprovePlanTool(team), Map.of(
                    "member_name", "member1",
                    "approved", false,
                    "feedback", "Please revise"
            ));

            assertTrue(result.isSuccess());
        }

        @Test
        void testInvokeMemberNotFound() throws Exception {
            TeamToolOutput result = invoke(new ApprovePlanTool(agentTeam()), Map.of(
                    "member_name", "nonexistent",
                    "approved", true
            ));

            assertFalse(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("TestApproveToolCallTool")
    class TestApproveToolCallTool {
        @Test
        void testInitialization() {
            TeamBackend team = agentTeam();
            ApproveToolCallTool tool = new ApproveToolCallTool(team);

            assertEquals("approve_tool", tool.getCard().getName());
            assertEquals("team.approve_tool", tool.getCard().getId());
            assertSame(team, tool.getTeam());
        }

        @Test
        void testInvokeApprove() throws Exception {
            TeamBackend team = agentTeam();
            spawnReady(team, "member1");

            TeamToolOutput result = invoke(new ApproveToolCallTool(team), Map.of(
                    "member_name", "member1",
                    "tool_call_id", "call-1",
                    "approved", true,
                    "feedback", "approved",
                    "auto_confirm", true
            ));

            assertTrue(result.isSuccess());
            assertNull(result.getError());
        }
    }

    @Nested
    @DisplayName("TestListMembersTool")
    class TestListMembersTool {
        @Test
        void testInitialization() {
            TeamBackend team = agentTeam();
            ListMembersTool tool = new ListMembersTool(team);

            assertEquals("list_members", tool.getCard().getName());
            assertEquals("team.list_members", tool.getCard().getId());
            assertSame(team, tool.getTeam());
        }

        @Test
        void testInvokeEmpty() throws Exception {
            TeamToolOutput result = invoke(new ListMembersTool(agentTeam()), Map.of());

            assertTrue(result.isSuccess());
            assertEquals(0, data(result).get("count"));
            assertEquals(List.of(), data(result).get("members"));
        }

        @Test
        void testInvokeWithMembers() throws Exception {
            TeamBackend team = agentTeam();
            spawnReady(team, "member1");
            spawnReady(team, "member2");

            TeamToolOutput result = invoke(new ListMembersTool(team), Map.of());

            assertTrue(result.isSuccess());
            assertEquals(2, data(result).get("count"));
            List<String> memberIds = rows(data(result).get("members")).stream()
                    .map(row -> String.valueOf(row.get("member_name")))
                    .toList();
            assertTrue(memberIds.contains("member1"));
            assertTrue(memberIds.contains("member2"));
        }
    }

    @Nested
    @DisplayName("TestTaskCreateTool")
    class TestTaskCreateTool {
        @Test
        void testInitialization() {
            CreateTaskTool tool = new CreateTaskTool(agentTeam());

            assertEquals("create_task", tool.getCard().getName());
            assertEquals("team.create_task", tool.getCard().getId());
        }

        @Test
        void testCreateSingleTask() throws Exception {
            TeamToolOutput result = invoke(new CreateTaskTool(agentTeam()), Map.of(
                    "tasks", List.of(Map.of("title", "Task 1", "content", "Content 1"))
            ));

            assertTrue(result.isSuccess());
            assertEquals("Task 1", data(result).get("title"));
        }

        @Test
        void testCreateBatchTasks() throws Exception {
            TeamToolOutput result = invoke(new CreateTaskTool(agentTeam()), Map.of(
                    "tasks", List.of(
                            Map.of("title", "Task 1", "content", "Content 1"),
                            Map.of("title", "Task 2", "content", "Content 2"),
                            Map.of("title", "Task 3", "content", "Content 3")
                    )
            ));

            assertTrue(result.isSuccess());
            assertEquals(3, data(result).get("count"));
            assertEquals(0, data(result).get("skipped"));
            assertEquals(3, rows(data(result).get("tasks")).size());
        }

        @Test
        void testCreateEmptyTasks() throws Exception {
            TeamToolOutput result = invoke(new CreateTaskTool(agentTeam()), Map.of("tasks", List.of()));

            assertFalse(result.isSuccess());
            assertNotNull(result.getError());
        }

        @Test
        void testCreateTaskWithDependedBy() throws Exception {
            TeamBackend team = agentTeam();
            TaskRecord base = team.getTaskManager().add("Base Task", "Base content", null, List.of());

            TeamToolOutput result = invoke(new CreateTaskTool(team), Map.of(
                    "tasks", List.of(Map.of(
                            "title", "Priority Task",
                            "content", "Priority content",
                            "depended_by", List.of(base.getTaskId())
                    ))
            ));

            assertTrue(result.isSuccess());
            assertEquals("Priority Task", data(result).get("title"));
        }
    }

    @Nested
    @DisplayName("TestUpdateTaskTool")
    class TestUpdateTaskTool {
        @Test
        void testInitialization() {
            UpdateTaskTool tool = new UpdateTaskTool(agentTeam());

            assertEquals("update_task", tool.getCard().getName());
            assertEquals("team.update_task", tool.getCard().getId());
            Map<?, ?> props = props(tool);
            assertTrue(props.containsKey("task_id"));
            assertTrue(props.containsKey("status"));
            assertTrue(props.containsKey("title"));
            assertTrue(props.containsKey("content"));
        }

        @Test
        void testUpdateContent() throws Exception {
            TeamBackend team = agentTeam();
            TaskRecord task = team.getTaskManager().add("Original", "Original Content", null, List.of());

            TeamToolOutput result = invoke(new UpdateTaskTool(team), Map.of(
                    "task_id", task.getTaskId(),
                    "title", "Updated Title",
                    "content", "Updated Content"
            ));

            assertTrue(result.isSuccess());
            assertEquals("updated", data(result).get("status"));
            assertTrue(list(data(result).get("updated_fields")).contains("title"));
            assertTrue(list(data(result).get("updated_fields")).contains("content"));
        }

        @Test
        void testCancelTask() throws Exception {
            TeamBackend team = agentTeam();
            TaskRecord task = team.getTaskManager().add("Task to Cancel", "Content", null, List.of());

            TeamToolOutput result = invoke(new UpdateTaskTool(team), Map.of("task_id", task.getTaskId(), "status", "cancelled"));

            assertTrue(result.isSuccess());
            assertEquals(task.getTaskId(), data(result).get("task_id"));
            assertEquals("cancelled", data(result).get("status"));
        }

        @Test
        void testCancelAllTasks() throws Exception {
            TeamBackend team = agentTeam();
            team.createTask("Task 1", "Content 1", "task1", List.of());
            team.createTask("Task 2", "Content 2", "task2", List.of());
            team.getTaskManager().assign("task2", "dev-1");

            TeamToolOutput result = invoke(new UpdateTaskTool(team), Map.of("task_id", "*", "status", "cancelled"));

            assertTrue(result.isSuccess());
            assertEquals(2, data(result).get("cancelled_count"));
        }

        @Test
        void testAssignTask() throws Exception {
            TeamBackend team = agentTeam();
            spawnReady(team, "dev-1");
            TaskRecord task = team.getTaskManager().add("Task", "Content", null, List.of());

            TeamToolOutput result = invoke(new UpdateTaskTool(team), Map.of("task_id", task.getTaskId(), "assignee", "dev-1"));

            assertTrue(result.isSuccess());
            assertTrue(list(data(result).get("updated_fields")).contains("assignee"));
            assertEquals("dev-1", team.getTask(task.getTaskId()).getAssignee());
        }

        @Test
        void testAssignReassignsToNewMember() throws Exception {
            TeamBackend team = agentTeam();
            spawnReady(team, "dev-1");
            spawnReady(team, "dev-2");
            TaskRecord task = team.getTaskManager().add("Task", "Content", null, List.of());
            team.getTaskManager().assign(task.getTaskId(), "dev-1");

            TeamToolOutput result = invoke(new UpdateTaskTool(team), Map.of("task_id", task.getTaskId(), "assignee", "dev-2"));

            assertTrue(result.isSuccess());
            assertTrue(list(data(result).get("updated_fields")).contains("assignee"));
            assertEquals("dev-2", team.getTask(task.getTaskId()).getAssignee());
        }

        @Test
        void testAddDependencies() throws Exception {
            TeamBackend team = agentTeam();
            TaskRecord upstream = team.getTaskManager().add("Upstream", "First", null, List.of());
            TaskRecord downstream = team.getTaskManager().add("Downstream", "Second", null, List.of());

            TeamToolOutput result = invoke(new UpdateTaskTool(team), Map.of(
                    "task_id", downstream.getTaskId(),
                    "add_blocked_by", List.of(upstream.getTaskId())
            ));

            assertTrue(result.isSuccess());
            assertTrue(list(data(result).get("updated_fields")).contains("blocked_by"));
            assertEquals("blocked", team.getTask(downstream.getTaskId()).getStatus().name().toLowerCase());
        }

        @Test
        void testNoUpdateSpecified() throws Exception {
            TeamBackend team = agentTeam();
            TaskRecord task = team.getTaskManager().add("Task", "Content", null, List.of());

            TeamToolOutput result = invoke(new UpdateTaskTool(team), Map.of("task_id", task.getTaskId()));

            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("No update specified"));
        }

        @Test
        void testAddBlockedByRejectsCycle() throws Exception {
            TeamBackend team = agentTeam();
            TaskRecord a = team.getTaskManager().add("A", "c", null, List.of());
            TaskRecord b = team.getTaskManager().add("B", "c", null, List.of(a.getTaskId()));

            TeamToolOutput result = invoke(new UpdateTaskTool(team), Map.of(
                    "task_id", a.getTaskId(),
                    "add_blocked_by", List.of(b.getTaskId())
            ));

            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("Circular dependency"));
        }

        @Test
        void testCancelUnblocksDownstream() throws Exception {
            TeamBackend team = agentTeam();
            TaskRecord upstream = team.getTaskManager().add("Up", "c", null, List.of());
            TaskRecord downstream = team.getTaskManager().add("Down", "c", null, List.of(upstream.getTaskId()));
            assertEquals("blocked", downstream.getStatus().name().toLowerCase());

            TeamToolOutput result = invoke(new UpdateTaskTool(team), Map.of("task_id", upstream.getTaskId(), "status", "cancelled"));

            assertTrue(result.isSuccess());
            assertEquals("pending", team.getTask(downstream.getTaskId()).getStatus().name().toLowerCase());
        }
    }

    @Nested
    @DisplayName("TestViewTaskToolV2")
    class TestViewTaskToolV2 {
        @Test
        void testInitialization() {
            ViewTaskTool tool = new ViewTaskTool(agentTeam());

            assertEquals("view_task", tool.getCard().getName());
            assertEquals("team.view_task", tool.getCard().getId());
        }

        @Test
        void testInvokeGetSingleTask() throws Exception {
            TeamBackend team = agentTeam();
            TaskRecord task = team.getTaskManager().add("Single Task", "Content", null, List.of());

            TeamToolOutput result = invoke(new ViewTaskTool(team), Map.of("action", "get", "task_id", task.getTaskId()));

            assertTrue(result.isSuccess());
            assertEquals(task.getTaskId(), data(result).get("task_id"));
            assertEquals("Single Task", data(result).get("title"));
            assertEquals("Content", data(result).get("content"));
            assertEquals(List.of(), data(result).get("blocked_by"));
            assertEquals(List.of(), data(result).get("blocks"));
            assertFalse(data(result).containsKey("team_id"));
        }

        @Test
        void testInvokeGetWithDependencies() throws Exception {
            TeamBackend team = agentTeam();
            TaskRecord upstream = team.getTaskManager().add("Upstream", "Do first", null, List.of());
            TaskRecord downstream = team.getTaskManager().add("Downstream", "Do second", null, List.of(upstream.getTaskId()));
            ViewTaskTool tool = new ViewTaskTool(team);

            TeamToolOutput blocked = invoke(tool, Map.of("action", "get", "task_id", downstream.getTaskId()));
            assertTrue(list(data(blocked).get("blocked_by")).contains(upstream.getTaskId()));

            TeamToolOutput blocks = invoke(tool, Map.of("action", "get", "task_id", upstream.getTaskId()));
            assertTrue(list(data(blocks).get("blocks")).contains(downstream.getTaskId()));
        }

        @Test
        void testInvokeGetTaskNotFound() throws Exception {
            TeamToolOutput result = invoke(new ViewTaskTool(agentTeam()), Map.of("action", "get", "task_id", "nonexistent"));

            assertFalse(result.isSuccess());
            assertTrue(result.getError().toLowerCase().contains("not found"));
        }

        @Test
        void testInvokeGetWithoutTaskId() throws Exception {
            TeamToolOutput result = invoke(new ViewTaskTool(agentTeam()), Map.of("action", "get"));

            assertFalse(result.isSuccess());
            assertNotNull(result.getError());
        }

        @Test
        void testInvokeListTasksByStatus() throws Exception {
            TeamBackend team = agentTeam();
            team.createTask("Task 1", "Content 1", "task1", List.of());
            team.createTask("Task 2", "Content 2", "task2", List.of());
            team.getTaskManager().assign("task2", "dev-1");
            team.createTask("Task 3", "Content 3", "task3", List.of());
            team.getTaskManager().complete("task3");

            TeamToolOutput result = invoke(new ViewTaskTool(team), Map.of("action", "list", "status", "pending"));

            assertTrue(result.isSuccess());
            assertEquals(1, data(result).get("count"));
            Map<String, Object> taskSummary = rows(data(result).get("tasks")).get(0);
            assertEquals("Task 1", taskSummary.get("title"));
            assertTrue(taskSummary.containsKey("blocked_by"));
            assertFalse(taskSummary.containsKey("content"));
            assertFalse(taskSummary.containsKey("team_id"));
        }

        @Test
        void testInvokeDefaultActionIsList() throws Exception {
            TeamBackend team = agentTeam();
            team.createTask("Task 1", "Content 1", "task1", List.of());
            team.createTask("Task 2", "Content 2", "task2", List.of());
            team.getTaskManager().assign("task2", "dev-1");
            team.createTask("Task 3", "Content 3", "task3", List.of());
            team.getTaskManager().complete("task3");

            TeamToolOutput result = invoke(new ViewTaskTool(team), Map.of());

            assertTrue(result.isSuccess());
            assertEquals(3, data(result).get("count"));
        }

        @Test
        void testInvokeClaimable() throws Exception {
            TeamBackend team = agentTeam();
            team.createTask("Task 1", "Content 1", "task1", List.of());
            team.createTask("Task 2", "Content 2", "task2", List.of());
            team.getTaskManager().assign("task2", "dev-1");
            team.createTask("Task 3", "Content 3", "task3", List.of());
            team.getTaskManager().complete("task3");

            TeamToolOutput result = invoke(new ViewTaskTool(team), Map.of("action", "claimable"));

            assertTrue(result.isSuccess());
            assertEquals(1, data(result).get("count"));
            assertEquals("task1", rows(data(result).get("tasks")).get(0).get("task_id"));
        }
    }

    @Nested
    @DisplayName("TestClaimTaskTool")
    class TestClaimTaskTool {
        @Test
        void testInitialization() {
            ClaimTaskTool tool = new ClaimTaskTool(agentTeam());

            assertEquals("claim_task", tool.getCard().getName());
            assertEquals("team.claim_task", tool.getCard().getId());
            Map<?, ?> props = props(tool);
            assertTrue(props.containsKey("task_id"));
            assertTrue(props.containsKey("status"));
            assertEquals(List.of("task_id", "status"), required(tool));
        }

        @Test
        void testClaimViaStatus() throws Exception {
            TeamBackend team = agentTeam();
            spawnReady(team, "leader1");
            TaskRecord task = team.getTaskManager().add("Test Task", "Test content", null, List.of());

            TeamToolOutput result = invoke(new ClaimTaskTool(team), Map.of("task_id", task.getTaskId(), "status", "claimed"));

            assertTrue(result.isSuccess());
            assertTrue(list(data(result).get("updated_fields")).contains("status"));
            assertEquals("claimed", data(data(result).get("status_change")).get("to"));
        }

        @Test
        void testCompleteViaStatus() throws Exception {
            TeamBackend team = agentTeam();
            spawnReady(team, "leader1");
            TaskRecord task = team.getTaskManager().add("Test Task", "Test content", null, List.of());
            team.claimTask(task.getTaskId(), "leader1");

            TeamToolOutput result = invoke(new ClaimTaskTool(team), Map.of("task_id", task.getTaskId(), "status", "completed"));

            assertTrue(result.isSuccess());
            assertTrue(list(data(result).get("updated_fields")).contains("status"));
            assertEquals("completed", data(data(result).get("status_change")).get("to"));
        }

        @Test
        void testTaskNotFound() throws Exception {
            TeamToolOutput result = invoke(new ClaimTaskTool(agentTeam()), Map.of("task_id", "nonexistent", "status", "claimed"));

            assertFalse(result.isSuccess());
            assertEquals("Task not found", result.getError());
        }
    }

    @Nested
    @DisplayName("TestMappedToolOutput")
    class TestMappedToolOutput {
        @Test
        void testStrReturnsMappedContent() {
            MappedToolOutput output = MappedToolOutput.fromOutput(
                    ToolOutput.success(Map.of("key", "value")),
                    "Custom text for LLM"
            );

            assertEquals("Custom text for LLM", output.toString());
            assertTrue(output.isSuccess());
            assertEquals(Map.of("key", "value"), output.getData());
        }

        @Test
        void testClaimTaskMapResultCompletedGuidance() {
            ClaimTaskTool tool = new ClaimTaskTool(agentTeam());
            TeamToolOutput output = new TeamToolOutput(true, Map.of(
                    "task_id", "t1",
                    "updated_fields", List.of("status"),
                    "status_change", Map.of("from", "claimed", "to", "completed")
            ), null);

            String result = tool.mapResult(output);

            assertTrue(result.contains("Task #t1 claimed \u2192 completed"));
            assertTrue(result.contains("view_task"));
        }

        @Test
        void testClaimTaskMapResultClaimedNoGuidance() {
            ClaimTaskTool tool = new ClaimTaskTool(agentTeam());
            TeamToolOutput output = new TeamToolOutput(true, Map.of(
                    "task_id", "t1",
                    "updated_fields", List.of("status"),
                    "status_change", Map.of("from", "pending", "to", "claimed")
            ), null);

            String result = tool.mapResult(output);

            assertTrue(result.contains("Task #t1 pending \u2192 claimed"));
            assertFalse(result.contains("view_task"));
        }

        @Test
        void testViewTaskMapResultList() {
            ViewTaskTool tool = new ViewTaskTool(agentTeam());
            Map<String, Object> task1 = row("task_id", "t1", "title", "Fix bug", "status", "pending",
                    "assignee", null, "blocked_by", List.of());
            Map<String, Object> task2 = row("task_id", "t2", "title", "Add test", "status", "claimed",
                    "assignee", "dev-1", "blocked_by", List.of("t1"));
            TeamToolOutput output = new TeamToolOutput(true, Map.of("tasks", List.of(task1, task2), "count", 2), null);

            String result = tool.mapResult(output);

            assertTrue(result.contains("#t1 [pending] Fix bug"));
            assertTrue(result.contains("(dev-1)"));
            assertTrue(result.contains("[blocked by #t1]"));
        }

        @Test
        void testViewTaskMapResultGet() {
            ViewTaskTool tool = new ViewTaskTool(agentTeam());
            TeamToolOutput output = new TeamToolOutput(true, Map.of(
                    "task_id", "t1",
                    "title", "Fix bug",
                    "content", "Fix the login bug",
                    "status", "claimed",
                    "assignee", "dev-1",
                    "blocked_by", List.of(),
                    "blocks", List.of("t2", "t3")
            ), null);

            String result = tool.mapResult(output);

            assertTrue(result.contains("Task #t1: Fix bug"));
            assertTrue(result.contains("Content: Fix the login bug"));
            assertTrue(result.contains("Blocks: #t2, #t3"));
        }

        @Test
        void testSendMessageMapResult() {
            SendMessageTool tool = new SendMessageTool(agentTeam());
            TeamToolOutput output = new TeamToolOutput(true, row("type", "message", "from", "leader",
                    "to", "dev-1", "summary", null), null);

            assertEquals("Message sent from leader to dev-1", tool.mapResult(output));
        }

        @Test
        void testSendMessageMapResultBroadcast() {
            SendMessageTool tool = new SendMessageTool(agentTeam());
            TeamToolOutput output = new TeamToolOutput(true, row("type", "broadcast", "from", "leader",
                    "summary", null), null);

            assertEquals("Broadcast sent from leader", tool.mapResult(output));
        }

        @Test
        void testDefaultMapResultJson() {
            ListMembersTool tool = new ListMembersTool(agentTeam());
            TeamToolOutput output = new TeamToolOutput(true, Map.of(
                    "members", List.of(Map.of("member_name", "m1", "display_name", "Dev", "status", "ready")),
                    "count", 1
            ), null);

            assertTrue(tool.mapResult(output).contains("member_name=m1 display_name=Dev status=ready"));
        }
    }

    @Nested
    @DisplayName("TestSendMessageTool")
    class TestSendMessageTool {
        @Test
        void testInitialization() {
            SendMessageTool tool = new SendMessageTool(agentTeam(), true);

            assertEquals("send_message", tool.getCard().getName());
            assertEquals("team.send_message", tool.getCard().getId());
            Map<?, ?> props = props(tool);
            assertTrue(props.containsKey("to"));
            assertTrue(props.containsKey("content"));
            assertTrue(props.containsKey("summary"));
        }

        @Test
        void testInvokePointToPoint() throws Exception {
            TeamToolOutput result = invoke(new SendMessageTool(agentTeam()), Map.of("to", "member2", "content", "Hello"));

            assertTrue(result.isSuccess());
            assertEquals("message", data(result).get("type"));
            assertEquals("member2", data(result).get("to"));
        }

        @Test
        void testInvokeBroadcast() throws Exception {
            TeamToolOutput result = invoke(new SendMessageTool(agentTeam()), Map.of("to", "*", "content", "Hello everyone"));

            assertTrue(result.isSuccess());
            assertEquals("broadcast", data(result).get("type"));
        }

        @Test
        void testInvokeWithSummary() throws Exception {
            TeamToolOutput result = invoke(new SendMessageTool(agentTeam()), Map.of(
                    "to", "member2",
                    "content", "Please claim task-1",
                    "summary", "assign task-1"
            ));

            assertTrue(result.isSuccess());
            assertEquals("assign task-1", data(result).get("summary"));
        }

        @Test
        void testInvokeEmptyTo() throws Exception {
            TeamToolOutput result = invoke(new SendMessageTool(agentTeam()), Map.of("to", "", "content", "Hello"));

            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("'to'"));
        }

        @Test
        void testInvokeEmptyContent() throws Exception {
            TeamToolOutput result = invoke(new SendMessageTool(agentTeam()), Map.of("to", "member2", "content", ""));

            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("'content'"));
        }

        @Test
        void testInvokeMemberNotFound() throws Exception {
            TeamToolOutput result = invoke(new SendMessageTool(agentTeam(), true), Map.of("to", "nonexistent", "content", "Hello"));

            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("not found"));
        }
    }

    @Nested
    @DisplayName("TestTranslator")
    class TestTranslator {
        @Test
        void testDescFromMarkdownIsReturned() throws Exception {
            String desc = ToolLocales.translate("cn", "build_team");

            assertTrue(desc.contains("build_team") || desc.contains("组建"));
        }

        @Test
        void testParamKeysReturnStringsDictEntries() throws Exception {
            String value = ToolLocales.translate("cn", "send_message", "summary");

            assertEquals("5-10 词摘要，用于消息预览和日志", value);
        }

        @Test
        void testMissingDescRaisesFileNotFound() {
            FileNotFoundException error = assertThrows(
                    FileNotFoundException.class,
                    () -> ToolLocales.translate("cn", "nonexistent_tool_for_translator_test")
            );

            assertTrue(error.getMessage().contains("nonexistent_tool_for_translator_test"));
            assertTrue(error.getMessage().contains("cn"));
        }
    }

    private static TeamBackend agentTeamWithoutTeam() {
        return new TeamBackend("test_team", "leader1", true, MemberMode.BUILD_MODE, List.of());
    }

    private static TeamBackend agentTeam() {
        TeamBackend team = agentTeamWithoutTeam();
        team.getStore().createTeam("Test Team", null, "leader1", null, null);
        return team;
    }

    private static AgentCard sampleAgentCard() {
        AgentCard card = new AgentCard();
        card.setName("TestAgent");
        card.setDescription("A test agent");
        return card;
    }

    private static TeamMember spawnReady(TeamBackend team, String memberName) {
        return team.spawnMember(
                memberName,
                memberName,
                sampleAgentCard(),
                "Test member",
                "Prompt",
                MemberStatus.READY,
                ExecutionStatus.IDLE
        );
    }

    private static TeamToolOutput invoke(TeamTool tool, Map<String, Object> inputs) throws Exception {
        return assertInstanceOf(TeamToolOutput.class, tool.invoke(inputs));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(TeamToolOutput output) {
        return (Map<String, Object>) output.getData();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return (List<Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> props(TeamTool tool) {
        return (Map<?, ?>) ((Map<?, ?>) tool.getCard().getInputParams().get("properties"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> required(TeamTool tool) {
        return (List<String>) tool.getCard().getInputParams().get("required");
    }

    private static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return result;
    }
}
