/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.agent_teams.agent.AgentConfigurator;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.schema.MemberOpResult;
import com.openjiuwen.agent_teams.schema.TeamCompletionSnapshot;
import com.openjiuwen.agent_teams.schema.TeamRuntimeContext;
import com.openjiuwen.agent_teams.schema.TeamSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.schema.status.TaskStatus;
import com.openjiuwen.agent_teams.tools.database.DatabaseConfig;
import com.openjiuwen.agent_teams.tools.database.DatabaseType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Supplemental parity tests for {@link TeamBackend}.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.test_team} in
 * {@code tests/unit_tests/agent_teams/test_team.py}.</p>
 */
class TeamBackendPythonParityTest {

    private static final String TEAM_NAME = "test_team";
    private static final String LEADER_NAME = "leader1";

    @TempDir
    private Path tempDir;

    @AfterEach
    void resetContext() {
        com.openjiuwen.agent_teams.AgentTeamsContext.resetSessionId(null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pythonTestTeamCases")
    void mirrorsPythonTestTeamCases(String pythonNodeId, Scenario scenario) throws Exception {
        scenario.run(new Fixture(tempDir));
    }

    private static Stream<Arguments> pythonTestTeamCases() {
        return Stream.of(
                arg("tests/unit_tests/agent_teams/test_team.py::TestAgentTeamInit::test_agent_team_init", fixture -> {
                    assertThat(fixture.backend.getTeamName()).isEqualTo(TEAM_NAME);
                    assertThat(fixture.backend.getMemberName()).isEqualTo(LEADER_NAME);
                    assertThat(fixture.backend.getTaskManager()).isNotNull();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestAgentTeamInit::test_agent_team_with_optional_fields", fixture -> {
                    fixture.db.createTeam("team_with_optional", "Optional Team", LEADER_NAME,
                            "Team description", "Team prompt").join();
                    TeamBackend backend = fixture.backend("team_with_optional", LEADER_NAME, true);

                    Team team = join(backend.getTeamInfo()).orElseThrow();

                    assertThat(team.getDesc()).isEqualTo("Team description");
                    assertThat(team.getPrompt()).isEqualTo("Team prompt");
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestSpawnMember::test_spawn_member_success", fixture -> {
                    MemberOpResult result = join(fixture.spawnMember("member1", "Member One"));

                    assertThat(result.isOk()).isTrue();
                    assertThat(join(fixture.backend.getMember("member1"))).isPresent();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestSpawnMember::test_spawn_member_creates_in_database", fixture -> {
                    join(fixture.spawnMember("member1", "Member One"));

                    TeamMember member = join(fixture.db.getMember("member1", TEAM_NAME)).orElseThrow();

                    assertThat(member.getMemberName()).isEqualTo("member1");
                    assertThat(member.getDisplayName()).isEqualTo("Member One");
                    assertThat(member.getTeamName()).isEqualTo(TEAM_NAME);
                    assertThat(member.getStatus()).isEqualTo(MemberStatus.UNSTARTED.value());
                    assertThat(member.getExecutionStatus()).isEqualTo(ExecutionStatus.IDLE.value());
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestSpawnMember::test_spawn_member_multiple", fixture -> {
                    join(fixture.spawnMember("member1", "Member One"));
                    join(fixture.spawnMember("member2", "Member Two"));

                    assertThat(join(fixture.backend.listMembers())).extracting(TeamMember::getMemberName)
                            .containsExactly("member1", "member2");
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestSpawnMember::test_spawn_member_duplicate_returns_reason", fixture -> {
                    MemberOpResult first = join(fixture.spawnMember("member1", "Member One"));
                    MemberOpResult second = join(fixture.spawnMember("member1", "Duplicate"));

                    assertThat(first.isOk()).isTrue();
                    assertThat(second.isOk()).isFalse();
                    assertThat(second.getReason()).contains("member1", "already exists");
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestSpawnMember::test_spawn_member_with_minimal_args", fixture -> {
                    MemberOpResult result = join(fixture.spawnMember("member1", "Member One"));

                    assertThat(result.isOk()).isTrue();
                    assertThat(join(fixture.backend.listMembers())).singleElement()
                            .extracting(TeamMember::getMemberName)
                            .isEqualTo("member1");
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestApprovePlan::test_approve_plan_success", fixture -> {
                    PlanFixture plan = fixture.submitMemberPlan("plan:one");

                    boolean approved = join(fixture.backend.approvePlan(plan.planId(), true, "Plan looks good"));

                    assertThat(approved).isTrue();
                    assertThat(join(fixture.db.getTask(plan.taskId()))).get()
                            .extracting(TeamTask::getStatus)
                            .isEqualTo(TaskStatus.PLAN_APPROVED.value());
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestApprovePlan::test_approve_plan_uses_task_event_without_duplicate_message", fixture -> {
                    PlanFixture plan = fixture.submitMemberPlan("plan:one");
                    int directMessageCount = fixture.directMessages().size();

                    boolean approved = join(fixture.backend.approvePlan(plan.planId(), true, "Great plan!"));

                    assertThat(approved).isTrue();
                    assertThat(fixture.directMessages()).hasSize(directMessageCount);
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestApprovePlan::test_reject_plan_uses_task_event_without_duplicate_message", fixture -> {
                    PlanFixture plan = fixture.submitMemberPlan("plan:one");
                    int directMessageCount = fixture.directMessages().size();

                    boolean rejected = join(fixture.backend.approvePlan(plan.planId(), false, "Please revise"));

                    assertThat(rejected).isTrue();
                    assertThat(fixture.directMessages()).hasSize(directMessageCount);
                    assertThat(join(fixture.db.getTask(plan.taskId()))).get()
                            .extracting(TeamTask::getStatus)
                            .isEqualTo(TaskStatus.CLAIMED.value());
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestApprovePlan::test_approve_plan_missing_plan", fixture -> {
                    assertThat(join(fixture.backend.approvePlan("missing-plan", true, null))).isFalse();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestApprovePlan::test_approve_plan_without_feedback", fixture -> {
                    PlanFixture plan = fixture.submitMemberPlan("plan:one");

                    boolean approved = join(fixture.backend.approvePlan(plan.planId()));

                    assertThat(approved).isTrue();
                    assertThat(join(fixture.db.getTask(plan.taskId()))).get()
                            .extracting(TeamTask::getStatus)
                            .isEqualTo(TaskStatus.PLAN_APPROVED.value());
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestApproveTool::test_approve_tool_success", fixture -> {
                    join(fixture.spawnMember("member1", "Member One"));

                    boolean result = join(fixture.backend.approveTool("member1", "call-1", true, "Looks safe", true));

                    assertThat(result).isTrue();
                    assertThat(fixture.messager.eventTypes()).contains("tool_approval_result");
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestApproveTool::test_approve_tool_member_not_found", fixture -> {
                    assertThat(join(fixture.backend.approveTool("missing", "call-1", false, null, false))).isFalse();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestShutdownMember::test_shutdown_member_success", fixture -> {
                    join(fixture.spawnMember("member1", "Member One", MemberStatus.READY));

                    assertThat(join(fixture.backend.shutdownMember("member1")).isOk()).isTrue();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestShutdownMember::test_shutdown_member_updates_status", fixture -> {
                    join(fixture.spawnMember("member1", "Member One", MemberStatus.READY));

                    join(fixture.backend.shutdownMember("member1"));

                    assertThat(join(fixture.db.getMember("member1", TEAM_NAME))).get()
                            .extracting(TeamMember::getStatus)
                            .isEqualTo(MemberStatus.SHUTDOWN_REQUESTED.value());
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestShutdownMember::test_shutdown_member_already_shutdown", fixture -> {
                    fixture.seedMember("member1", MemberStatus.SHUTDOWN, MemberMode.BUILD_MODE);

                    MemberOpResult result = join(fixture.backend.shutdownMember("member1"));

                    assertThat(result.isOk()).isTrue();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestShutdownMember::test_shutdown_member_not_found", fixture -> {
                    MemberOpResult result = join(fixture.backend.shutdownMember("nonexistent_member"));

                    assertThat(result.isOk()).isFalse();
                    assertThat(result.getReason()).contains("not found");
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestCancelMember::test_cancel_member_success", fixture -> {
                    join(fixture.spawnMember("member1", "Member One"));

                    assertThat(join(fixture.backend.cancelMember("member1"))).isTrue();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestCancelMember::test_cancel_member_when_busy", fixture -> {
                    fixture.seedMember("member1", MemberStatus.BUSY, MemberMode.BUILD_MODE);

                    boolean result = join(fixture.backend.cancelMember("member1"));

                    assertThat(result).isTrue();
                    assertThat(fixture.directMessages()).extracting(TeamMessage::getToMemberName)
                            .contains("member1");
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestCancelMember::test_cancel_member_when_not_busy", fixture -> {
                    fixture.seedMember("member1", MemberStatus.READY, MemberMode.BUILD_MODE);

                    boolean result = join(fixture.backend.cancelMember("member1"));

                    assertThat(result).isTrue();
                    assertThat(fixture.directMessages()).isEmpty();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestCancelMember::test_cancel_member_not_found", fixture -> {
                    assertThat(join(fixture.backend.cancelMember("nonexistent_member"))).isFalse();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestCancelMember::test_cancel_member_resets_claimed_tasks", fixture -> {
                    fixture.seedMember("member1", MemberStatus.BUSY, MemberMode.BUILD_MODE);
                    TeamTaskManager memberTaskManager = fixture.memberTaskManager("member1");
                    memberTaskManager.add("Task 1", "Content 1", "task1", null).toCompletableFuture().join();
                    memberTaskManager.add("Task 2", "Content 2", "task2", null).toCompletableFuture().join();
                    memberTaskManager.add("Task 3", "Content 3", "task3", null).toCompletableFuture().join();
                    join(memberTaskManager.claim("task1"));
                    join(memberTaskManager.claim("task2"));

                    boolean result = join(fixture.backend.cancelMember("member1"));

                    assertThat(result).isTrue();
                    assertThat(join(fixture.db.getTask("task1"))).get()
                            .extracting(TeamTask::getStatus, TeamTask::getAssignee)
                            .containsExactly(TaskStatus.PENDING.value(), null);
                    assertThat(join(fixture.db.getTask("task2"))).get()
                            .extracting(TeamTask::getStatus, TeamTask::getAssignee)
                            .containsExactly(TaskStatus.PENDING.value(), null);
                    assertThat(join(fixture.db.getTask("task3"))).get()
                            .extracting(TeamTask::getStatus, TeamTask::getAssignee)
                            .containsExactly(TaskStatus.PENDING.value(), null);
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestCancelMember::test_cancel_member_no_claimed_tasks", fixture -> {
                    fixture.seedMember("member1", MemberStatus.BUSY, MemberMode.BUILD_MODE);

                    boolean result = join(fixture.backend.cancelMember("member1"));

                    assertThat(result).isTrue();
                    assertThat(fixture.directMessages()).singleElement()
                            .extracting(TeamMessage::getToMemberName)
                            .isEqualTo("member1");
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestCleanTeam::test_clean_team_success", fixture -> {
                    fixture.seedMember("member1", MemberStatus.SHUTDOWN, MemberMode.BUILD_MODE);

                    boolean result = join(fixture.backend.cleanTeam());

                    assertThat(result).isTrue();
                    assertThat(join(fixture.db.getTeam(TEAM_NAME))).isEmpty();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestCleanTeam::test_clean_team_fails_when_members_not_shutdown", fixture -> {
                    join(fixture.spawnMember("member1", "Member One"));

                    assertThat(join(fixture.backend.cleanTeam())).isFalse();
                    assertThat(join(fixture.db.getTeam(TEAM_NAME))).isPresent();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestCleanTeam::test_clean_team_partial_shutdown", fixture -> {
                    fixture.seedMember("member1", MemberStatus.SHUTDOWN, MemberMode.BUILD_MODE);
                    fixture.seedMember("member2", MemberStatus.READY, MemberMode.BUILD_MODE);

                    assertThat(join(fixture.backend.cleanTeam())).isFalse();
                    assertThat(join(fixture.db.getTeam(TEAM_NAME))).isPresent();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestGetMember::test_get_member_success", fixture -> {
                    join(fixture.spawnMember("member1", "Member One"));

                    TeamMember member = join(fixture.backend.getMember("member1")).orElseThrow();

                    assertThat(member.getMemberName()).isEqualTo("member1");
                    assertThat(member.getDisplayName()).isEqualTo("Member One");
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestGetMember::test_get_member_not_found", fixture -> {
                    assertThat(join(fixture.backend.getMember("nonexistent_member"))).isEmpty();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestListMembers::test_list_members_empty", fixture -> {
                    assertThat(join(fixture.backend.listMembers())).isEmpty();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestListMembers::test_list_members_with_members", fixture -> {
                    join(fixture.spawnMember("member1", "Member One"));
                    join(fixture.spawnMember("member2", "Member Two"));

                    assertThat(join(fixture.backend.listMembers())).extracting(TeamMember::getMemberName)
                            .containsExactly("member1", "member2");
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestGetTeamInfo::test_get_team_info_success", fixture -> {
                    Team team = join(fixture.backend.getTeamInfo()).orElseThrow();

                    assertThat(team.getTeamName()).isEqualTo(TEAM_NAME);
                    assertThat(team.getDisplayName()).isEqualTo("Test Team");
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestGetTeamInfo::test_get_team_info_with_optional_fields", fixture -> {
                    fixture.db.createTeam("optional_info", "Optional Team", LEADER_NAME,
                            "Team description", "Team prompt").join();
                    Team team = join(fixture.backend("optional_info", LEADER_NAME, true).getTeamInfo()).orElseThrow();

                    assertThat(team.getDesc()).isEqualTo("Team description");
                    assertThat(team.getPrompt()).isEqualTo("Team prompt");
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestGetTeamInfo::test_get_team_info_not_found", fixture -> {
                    TeamBackend backend = fixture.backend("nonexistent_team", LEADER_NAME, true);

                    assertThat(join(backend.getTeamInfo())).isEmpty();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestCancelTask::test_cancel_task_success", fixture -> {
                    fixture.db.createTask("task1", TEAM_NAME, "Test Task", "Task content", TaskStatus.PENDING.value()).join();

                    boolean result = join(fixture.backend.cancelTask("task1"));

                    assertThat(result).isTrue();
                    assertThat(join(fixture.db.getTask("task1"))).get()
                            .extracting(TeamTask::getStatus)
                            .isEqualTo(TaskStatus.CANCELLED.value());
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestCancelTask::test_cancel_task_not_found", fixture -> {
                    assertThat(join(fixture.backend.cancelTask("nonexistent_task"))).isFalse();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestCancelTask::test_cancel_task_already_cancelled", fixture -> {
                    fixture.db.createTask("task1", TEAM_NAME, "Test Task", "Task content", TaskStatus.CANCELLED.value()).join();

                    assertThat(join(fixture.backend.cancelTask("task1"))).isTrue();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestCancelTask::test_cancel_task_with_assignee_sends_notification", fixture -> {
                    fixture.seedMember("member1", MemberStatus.READY, MemberMode.BUILD_MODE);
                    fixture.db.createTask("task1", TEAM_NAME, "Test Task", "Task content", TaskStatus.PENDING.value()).join();
                    fixture.db.claimTask("task1", "member1").join();

                    boolean result = join(fixture.backend.cancelTask("task1"));

                    assertThat(result).isTrue();
                    assertThat(fixture.directMessages()).extracting(TeamMessage::getToMemberName)
                            .contains("member1");
                    assertThat(fixture.directMessages()).extracting(TeamMessage::getContent)
                            .anySatisfy(content -> assertThat(content).contains("cancelled", "Test Task"));
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestCancelTask::test_cancel_task_without_assignee_no_notification", fixture -> {
                    fixture.db.createTask("task1", TEAM_NAME, "Test Task", "Task content", TaskStatus.PENDING.value()).join();

                    boolean result = join(fixture.backend.cancelTask("task1"));

                    assertThat(result).isTrue();
                    assertThat(fixture.directMessages()).isEmpty();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestCancelAllTasks::test_cancel_all_tasks_success", fixture -> {
                    fixture.db.createTask("task1", TEAM_NAME, "Task 1", "Content 1", TaskStatus.PENDING.value()).join();
                    fixture.db.createTask("task2", TEAM_NAME, "Task 2", "Content 2", TaskStatus.PENDING.value()).join();
                    fixture.db.createTask("task3", TEAM_NAME, "Task 3", "Content 3", TaskStatus.PENDING.value()).join();

                    int count = join(fixture.backend.cancelAllTasks());

                    assertThat(count).isEqualTo(3);
                    assertThat(join(fixture.db.getTask("task1"))).get()
                            .extracting(TeamTask::getStatus)
                            .isEqualTo(TaskStatus.CANCELLED.value());
                    assertThat(fixture.broadcastMessages()).singleElement()
                            .extracting(TeamMessage::getContent)
                            .asString()
                            .contains("All tasks (3) have been cancelled");
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestCancelAllTasks::test_cancel_all_tasks_mixed_status", fixture -> {
                    fixture.db.createTask("task1", TEAM_NAME, "Task 1", "Content 1", TaskStatus.PENDING.value()).join();
                    fixture.db.createTask("task2", TEAM_NAME, "Task 2", "Content 2", TaskStatus.PENDING.value()).join();
                    fixture.db.claimTask("task2", "member1").join();
                    fixture.db.createTask("task3", TEAM_NAME, "Task 3", "Content 3", TaskStatus.CANCELLED.value()).join();
                    fixture.db.createTask("task4", TEAM_NAME, "Task 4", "Content 4", TaskStatus.COMPLETED.value()).join();

                    int count = join(fixture.backend.cancelAllTasks());

                    assertThat(count).isEqualTo(2);
                    assertThat(join(fixture.db.getTask("task1"))).get()
                            .extracting(TeamTask::getStatus)
                            .isEqualTo(TaskStatus.CANCELLED.value());
                    assertThat(join(fixture.db.getTask("task2"))).get()
                            .extracting(TeamTask::getStatus)
                            .isEqualTo(TaskStatus.CANCELLED.value());
                    assertThat(join(fixture.db.getTask("task4"))).get()
                            .extracting(TeamTask::getStatus)
                            .isEqualTo(TaskStatus.COMPLETED.value());
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestCancelAllTasks::test_cancel_all_tasks_no_active_tasks", fixture -> {
                    fixture.db.createTask("task1", TEAM_NAME, "Task 1", "Content 1", TaskStatus.CANCELLED.value()).join();
                    fixture.db.createTask("task2", TEAM_NAME, "Task 2", "Content 2", TaskStatus.COMPLETED.value()).join();

                    int count = join(fixture.backend.cancelAllTasks());

                    assertThat(count).isZero();
                    assertThat(fixture.broadcastMessages()).isEmpty();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestCancelAllTasks::test_cancel_all_tasks_empty_team", fixture -> {
                    assertThat(join(fixture.backend.cancelAllTasks())).isZero();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestTeamRuntimeContextDbConfig::test_runtime_context_with_database_config", fixture -> {
                    DatabaseConfig config = DatabaseConfig.builder()
                            .dbType(DatabaseType.SQLITE)
                            .connectionString(":memory:")
                            .build();
                    TeamRuntimeContext context = runtimeContext(config);

                    assertThat(context.getDbConfig()).containsEntry("db_type", "sqlite");
                    assertThat(context.getDbConfig()).containsEntry("connection_string", ":memory:");
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestTeamRuntimeContextDbConfig::test_runtime_context_with_memory_database_config", fixture -> {
                    MemoryDatabaseConfig config = new MemoryDatabaseConfig();
                    TeamRuntimeContext context = runtimeContext(null);
                    context.setDbConfig(Map.of(
                            "db_type", config.getDbType(),
                            "connection_string", config.getConnectionString()));

                    assertThat(context.getDbConfig()).containsEntry("db_type", "memory");
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::TestTeamRuntimeContextDbConfig::test_runtime_context_default_database_config", fixture -> {
                    TeamRuntimeContext context = runtimeContext(null);

                    assertThat(context.getDbConfig()).containsEntry("db_type", "sqlite");
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::test_is_team_completed_all_conditions_met", fixture -> {
                    fixture.seedMember(LEADER_NAME, MemberStatus.READY, MemberMode.BUILD_MODE);
                    fixture.seedMember("member1", MemberStatus.READY, MemberMode.BUILD_MODE);
                    fixture.drainOneTask();

                    Optional<TeamCompletionSnapshot> snapshot = join(fixture.backend.isTeamCompleted());

                    assertThat(snapshot).isPresent();
                    assertThat(snapshot.orElseThrow().getMemberCount()).isEqualTo(2);
                    assertThat(snapshot.orElseThrow().getTaskCount()).isEqualTo(1);
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::test_is_team_completed_member_busy_returns_none", fixture -> {
                    fixture.seedMember(LEADER_NAME, MemberStatus.READY, MemberMode.BUILD_MODE);
                    fixture.seedMember("member1", MemberStatus.BUSY, MemberMode.BUILD_MODE);
                    fixture.drainOneTask();

                    assertThat(join(fixture.backend.isTeamCompleted())).isEmpty();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::test_is_team_completed_leader_busy_returns_none", fixture -> {
                    fixture.seedMember(LEADER_NAME, MemberStatus.BUSY, MemberMode.BUILD_MODE);
                    fixture.seedMember("member1", MemberStatus.READY, MemberMode.BUILD_MODE);
                    fixture.drainOneTask();

                    assertThat(join(fixture.backend.isTeamCompleted())).isEmpty();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::test_is_team_completed_pending_task_returns_none", fixture -> {
                    fixture.seedMember(LEADER_NAME, MemberStatus.READY, MemberMode.BUILD_MODE);
                    fixture.seedMember("member1", MemberStatus.READY, MemberMode.BUILD_MODE);
                    fixture.backend.getTaskManager().add("T", "c", "task", null).toCompletableFuture().join();

                    assertThat(join(fixture.backend.isTeamCompleted())).isEmpty();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::test_is_team_completed_empty_task_list_returns_none", fixture -> {
                    fixture.seedMember(LEADER_NAME, MemberStatus.READY, MemberMode.BUILD_MODE);
                    fixture.seedMember("member1", MemberStatus.READY, MemberMode.BUILD_MODE);

                    assertThat(join(fixture.backend.isTeamCompleted())).isEmpty();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::test_is_team_completed_unread_message_returns_none", fixture -> {
                    fixture.seedMember(LEADER_NAME, MemberStatus.READY, MemberMode.BUILD_MODE);
                    fixture.seedMember("member1", MemberStatus.READY, MemberMode.BUILD_MODE);
                    fixture.drainOneTask();
                    fixture.backend.getMessageManager().sendMessage("ping", "member1").toCompletableFuture().join();

                    assertThat(join(fixture.backend.isTeamCompleted())).isEmpty();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::test_is_team_completed_blocks_on_unread_broadcast", fixture -> {
                    fixture.seedMember(LEADER_NAME, MemberStatus.READY, MemberMode.BUILD_MODE);
                    fixture.seedMember("member1", MemberStatus.READY, MemberMode.BUILD_MODE);
                    fixture.drainOneTask();
                    fixture.backend.getMessageManager().broadcastMessage("announce").toCompletableFuture().join();

                    assertThat(join(fixture.backend.isTeamCompleted())).isEmpty();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::test_clean_team_fires_callback_on_success", fixture -> {
                    AtomicInteger calls = new AtomicInteger();
                    fixture.db.createTeam("cb_team", "Callback Team", LEADER_NAME, null, null).join();
                    TeamBackend backend = fixture.backend("cb_team", LEADER_NAME, true,
                            () -> {
                                calls.incrementAndGet();
                                return CompletableFuture.completedFuture(null);
                            },
                            null);

                    boolean result = join(backend.cleanTeam());

                    assertThat(result).isTrue();
                    assertThat(calls).hasValue(1);
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::test_build_team_fires_callback_on_success", fixture -> {
                    AtomicInteger calls = new AtomicInteger();
                    TeamBackend backend = fixture.backend("build_cb_team", LEADER_NAME, true, null,
                            () -> {
                                calls.incrementAndGet();
                                return CompletableFuture.completedFuture(null);
                            });

                    join(backend.buildTeam("Build Callback Team", "Callback Team", "Leader", "Leader persona"));

                    assertThat(calls).hasValue(1);
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::test_clean_team_skips_callback_on_failure", fixture -> {
                    AtomicInteger calls = new AtomicInteger();
                    fixture.db.createTeam("cb_fail_team", "Callback Fail Team", LEADER_NAME, null, null).join();
                    TeamBackend backend = fixture.backend("cb_fail_team", LEADER_NAME, true,
                            () -> {
                                calls.incrementAndGet();
                                return CompletableFuture.completedFuture(null);
                            },
                            null);
                    join(backend.spawnMember("member1", "Member One", fixture.card(), null, null,
                            MemberStatus.UNSTARTED, ExecutionStatus.IDLE, MemberMode.BUILD_MODE, null, TeamRole.TEAMMATE));

                    boolean result = join(backend.cleanTeam());

                    assertThat(result).isFalse();
                    assertThat(calls).hasValue(0);
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::test_clean_team_callback_failure_does_not_break_clean", fixture -> {
                    fixture.db.createTeam("cb_raise_team", "Callback Raise Team", LEADER_NAME, null, null).join();
                    TeamBackend backend = fixture.backend("cb_raise_team", LEADER_NAME, true,
                            () -> {
                                throw new IllegalStateException("boom");
                            },
                            null);

                    boolean result = join(backend.cleanTeam());

                    assertThat(result).isTrue();
                    assertThat(join(backend.getTeamInfo())).isEmpty();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::test_clean_team_no_callback_is_noop", fixture -> {
                    fixture.db.createTeam("no_cb_team", "No Callback Team", LEADER_NAME, null, null).join();
                    TeamBackend backend = fixture.backend("no_cb_team", LEADER_NAME, true);

                    assertThat(join(backend.cleanTeam())).isTrue();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::test_startup_member_transitions_to_starting_then_spawns", fixture -> {
                    fixture.db.createTeam("startup_team", "Startup Team", LEADER_NAME, null, null).join();
                    TeamBackend backend = fixture.backend("startup_team", LEADER_NAME, true);
                    join(backend.spawnMember("dev-1", "Dev 1", fixture.card(), null, null,
                            MemberStatus.UNSTARTED, ExecutionStatus.IDLE, MemberMode.BUILD_MODE, null, TeamRole.TEAMMATE));
                    AtomicInteger calls = new AtomicInteger();

                    boolean result = join(backend.startupMember("dev-1", memberName -> {
                        calls.incrementAndGet();
                        return CompletableFuture.completedFuture(null);
                    }));

                    assertThat(result).isTrue();
                    assertThat(calls).hasValue(1);
                    assertThat(join(fixture.db.getMember("dev-1", "startup_team"))).get()
                            .extracting(TeamMember::getStatus)
                            .isEqualTo(MemberStatus.STARTING.value());
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::test_startup_member_returns_false_if_not_unstarted", fixture -> {
                    fixture.db.createTeam("skip_team", "Skip Team", LEADER_NAME, null, null).join();
                    TeamBackend backend = fixture.backend("skip_team", LEADER_NAME, true);
                    join(backend.spawnMember("dev-1", "Dev 1", fixture.card(), null, null,
                            MemberStatus.STARTING, ExecutionStatus.IDLE, MemberMode.BUILD_MODE, null, TeamRole.TEAMMATE));
                    AtomicInteger calls = new AtomicInteger();

                    boolean result = join(backend.startupMember("dev-1", memberName -> {
                        calls.incrementAndGet();
                        return CompletableFuture.completedFuture(null);
                    }));

                    assertThat(result).isFalse();
                    assertThat(calls).hasValue(0);
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::test_startup_member_returns_false_for_unknown_member", fixture -> {
                    fixture.db.createTeam("unknown_team", "Unknown Team", LEADER_NAME, null, null).join();
                    TeamBackend backend = fixture.backend("unknown_team", LEADER_NAME, true);

                    assertThat(join(backend.startupMember("ghost", memberName -> CompletableFuture.completedFuture(null))))
                            .isFalse();
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::test_startup_member_rollback_on_spawn_failure", fixture -> {
                    fixture.db.createTeam("rollback_team", "Rollback Team", LEADER_NAME, null, null).join();
                    TeamBackend backend = fixture.backend("rollback_team", LEADER_NAME, true);
                    join(backend.spawnMember("dev-1", "Dev 1", fixture.card(), null, null,
                            MemberStatus.UNSTARTED, ExecutionStatus.IDLE, MemberMode.BUILD_MODE, null, TeamRole.TEAMMATE));

                    assertThatThrownBy(() -> join(backend.startupMember(
                            "dev-1",
                            memberName -> CompletableFuture.failedFuture(new IllegalStateException("spawn crashed")))))
                            .hasRootCauseMessage("spawn crashed");
                    assertThat(join(fixture.db.getMember("dev-1", "rollback_team"))).get()
                            .extracting(TeamMember::getStatus)
                            .isEqualTo(MemberStatus.UNSTARTED.value());
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::test_startup_delegates_to_startup_member", fixture -> {
                    fixture.db.createTeam("batch_team", "Batch Team", LEADER_NAME, null, null).join();
                    TeamBackend backend = fixture.backend("batch_team", LEADER_NAME, true);
                    join(backend.spawnMember("dev-1", "Dev 1", fixture.card(), null, null,
                            MemberStatus.UNSTARTED, ExecutionStatus.IDLE, MemberMode.BUILD_MODE, null, TeamRole.TEAMMATE));
                    join(backend.spawnMember("dev-2", "Dev 2", fixture.card(), null, null,
                            MemberStatus.UNSTARTED, ExecutionStatus.IDLE, MemberMode.BUILD_MODE, null, TeamRole.TEAMMATE));
                    List<String> created = new ArrayList<>();

                    List<String> started = join(backend.startup(memberName -> {
                        created.add(memberName);
                        return CompletableFuture.completedFuture(null);
                    }));

                    assertThat(started).containsExactly("dev-1", "dev-2");
                    assertThat(created).containsExactly("dev-1", "dev-2");
                }),
                arg("tests/unit_tests/agent_teams/test_team.py::test_try_transition_member_status_atomic_cas", fixture -> {
                    fixture.db.createTeam("cas_team", "CAS Team", LEADER_NAME, null, null).join();
                    fixture.db.createMember("dev-1", "cas_team", "Dev 1", "{}",
                            MemberStatus.UNSTARTED.value(), TeamRole.TEAMMATE.value(), null,
                            ExecutionStatus.IDLE.value(), MemberMode.BUILD_MODE.value(), null, null).join();

                    boolean ok1 = fixture.db.updateMemberStatus(
                            "dev-1", "cas_team", MemberStatus.STARTING.value()).join();
                    boolean ok2 = fixture.db.updateMemberStatus(
                            "dev-1", "cas_team", MemberStatus.STARTING.value()).join();

                    assertThat(ok1).isTrue();
                    assertThat(ok2).isFalse();
                })
        );
    }

    private static Arguments arg(String pythonNodeId, Scenario scenario) {
        return Arguments.of(pythonNodeId, scenario);
    }

    private static TeamRuntimeContext runtimeContext(DatabaseConfig dbConfig) {
        TeamRuntimeContext context = new TeamRuntimeContext();
        context.setRole(TeamRole.LEADER);
        context.setMemberName(LEADER_NAME);
        context.setTeamSpec(new TeamSpec(TEAM_NAME, "Test Team", LEADER_NAME));
        if (dbConfig != null) {
            context.setDbConfig(dbConfig);
        }
        return context;
    }

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    /**
     * Functional test scenario for one Python pytest node.
     *
     * <p>Mirrors Python's callable test body in
     * {@code tests/unit_tests/agent_teams/test_team.py}.</p>
     */
    @FunctionalInterface
    private interface Scenario {
        void run(Fixture fixture) throws Exception;
    }

    /**
     * Per-invocation fixture matching the Python pytest fixtures.
     *
     * <p>Mirrors Python's {@code db}, {@code message_bus}, and {@code agent_team} fixtures in
     * {@code tests/unit_tests/agent_teams/test_team.py}.</p>
     */
    private static final class Fixture {
        private final Path tempDir;
        private final InMemoryTeamDatabase db = new InMemoryTeamDatabase();
        private final RecordingMessager messager = new RecordingMessager();
        private final TeamBackend backend;

        private Fixture(Path tempDir) {
            this.tempDir = tempDir;
            db.createTeam(TEAM_NAME, "Test Team", LEADER_NAME, null, null).join();
            backend = backend(TEAM_NAME, LEADER_NAME, true);
        }

        private AgentConfigurator.AgentCard card() {
            return new AgentConfigurator.AgentCard("TestAgent", "TestAgent", "A test agent");
        }

        private CompletionStage<MemberOpResult> spawnMember(String name, String displayName) {
            return spawnMember(name, displayName, MemberStatus.UNSTARTED);
        }

        private CompletionStage<MemberOpResult> spawnMember(String name, String displayName, MemberStatus status) {
            return backend.spawnMember(name, displayName, card(), "Test member", "Member prompt",
                    status, ExecutionStatus.IDLE, MemberMode.BUILD_MODE, null, TeamRole.TEAMMATE);
        }

        private void seedMember(String name, MemberStatus status, MemberMode mode) {
            db.createMember(name, TEAM_NAME, name, "{}", status.value(), TeamRole.TEAMMATE.value(), null,
                    ExecutionStatus.IDLE.value(), mode.value(), null, null).join();
        }

        private TeamTaskManager memberTaskManager(String memberName) {
            return new TeamTaskManager(TEAM_NAME, memberName, db, messager, tempDir, "team-plan", LEADER_NAME);
        }

        private PlanFixture submitMemberPlan(String planId) throws Exception {
            join(backend.spawnMember("member1", "Member One", card(), "Test member", "Member prompt",
                    MemberStatus.UNSTARTED, ExecutionStatus.IDLE, MemberMode.PLAN_MODE, null, TeamRole.TEAMMATE));
            backend.getTaskManager().add("Plan task", "Do work", "plan-task", null).toCompletableFuture().join();
            TeamTaskManager planner = memberTaskManager("member1");
            Path planPath = tempDir.resolve("draft-plan.md");
            Files.writeString(planPath, "1. inspect\n2. implement\n");
            Map<String, Object> submit = planner.submitPlan("plan-task", planPath.toString(), planId, "tool-1")
                    .toCompletableFuture()
                    .join();
            assertThat(submit).containsEntry("success", true);
            return new PlanFixture("plan-task", String.valueOf(submit.get("plan_id")));
        }

        private void drainOneTask() {
            TeamTaskManager manager = backend.getTaskManager();
            manager.add("T", "c", "task-1", null).toCompletableFuture().join();
            join(manager.claim("task-1"));
            join(manager.complete("task-1"));
        }

        private TeamBackend backend(String teamName, String memberName, boolean leader) {
            return backend(teamName, memberName, leader, null, null);
        }

        private TeamBackend backend(
                String teamName,
                String memberName,
                boolean leader,
                java.util.function.Supplier<CompletionStage<Void>> onTeamCleaned,
                java.util.function.Supplier<CompletionStage<Void>> onTeamBuilt) {
            return new TeamBackend(
                    teamName,
                    memberName,
                    leader,
                    db,
                    messager,
                    MemberMode.BUILD_MODE,
                    List.of(),
                    null,
                    null,
                    false,
                    false,
                    List.of(),
                    onTeamCleaned,
                    onTeamBuilt,
                    tempDir,
                    "team-plan",
                    LEADER_NAME
            );
        }

        private List<TeamMessage> directMessages() {
            return db.getTeamMessages(TEAM_NAME, false).join();
        }

        private List<TeamMessage> broadcastMessages() {
            return db.getTeamMessages(TEAM_NAME, true).join();
        }
    }

    /**
     * Submitted plan identifiers used by plan approval scenarios.
     *
     * <p>Mirrors Python's submitted task and plan pair in
     * {@code tests/unit_tests/agent_teams/test_team.py}.</p>
     */
    private record PlanFixture(String taskId, String planId) {
    }

    /**
     * Recording messager collaborator for backend parity checks.
     *
     * <p>Mirrors Python's {@code AsyncMock(spec=Messager)} fixture in
     * {@code tests/unit_tests/agent_teams/test_team.py}.</p>
     */
    private static final class RecordingMessager implements Messager {
        private final List<EventMessage> publishedMessages = new ArrayList<>();

        @Override
        public CompletionStage<Void> start() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> stop() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> publish(String topicId, EventMessage message) {
            publishedMessages.add(message);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> subscribe(String topicId, MessagerHandler handler) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unsubscribe(String topicId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> send(String agentId, EventMessage message) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> registerDirectMessageHandler(MessagerHandler handler) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregisterDirectMessageHandler() {
            return CompletableFuture.completedFuture(null);
        }

        private List<String> eventTypes() {
            return publishedMessages.stream().map(EventMessage::getEventType).toList();
        }
    }
}
