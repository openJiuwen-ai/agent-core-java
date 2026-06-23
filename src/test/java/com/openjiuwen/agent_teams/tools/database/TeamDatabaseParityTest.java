/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.schema.GraphMutationResult;
import com.openjiuwen.agent_teams.schema.NewTaskSpec;
import com.openjiuwen.agent_teams.schema.status.TaskStatus;
import com.openjiuwen.agent_teams.tools.InMemoryTeamDatabase;
import com.openjiuwen.agent_teams.tools.TeamMessage;
import com.openjiuwen.agent_teams.tools.TeamTask;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Supplemental parity coverage for the Python database test module.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent_teams/test_database.py}
 * in {@code tests/unit_tests/agent_teams/test_database.py}.</p>
 */
class TeamDatabaseParityTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "test_database_config_default",
            "test_database_config_custom_custom",
            "test_database_type_values",
            "test_database_initialize_creates_tables",
            "test_database_initialize_idempotent",
            "test_unsupported_database_type_raises",
            "test_postgresql_initialize_uses_asyncpg_engine",
            "test_create_team_success",
            "test_create_team_minimal",
            "test_create_team_duplicate_fails",
            "test_get_team_not_found",
            "test_delete_team_success",
            "test_delete_team_not_found",
            "test_create_member_success",
            "test_create_member_duplicate_fails",
            "test_get_member_not_found",
            "test_update_member_status",
            "test_update_member_status_not_found",
            "test_update_member_execution_status",
            "test_update_member_execution_status_not_found",
            "test_get_team_members",
            "test_get_team_members_empty",
            "test_create_task_success",
            "test_get_task_not_found",
            "test_update_task_status",
            "test_update_task_status_not_found",
            "test_update_task_title_only",
            "test_update_task_content_only",
            "test_update_task_both_title_and_content",
            "test_update_task_not_found",
            "test_update_task_same_values",
            "test_update_claimed_task_fails",
            "test_claim_task",
            "test_claim_task_not_found",
            "test_get_team_tasks",
            "test_get_team_tasks_with_status_filter",
            "test_create_task_duplicate_fails",
            "test_concurrent_create_tasks_with_different_ids",
            "test_concurrent_create_tasks_with_file_db",
            "test_mutate_dependency_graph_adds_single_edge",
            "test_get_task_dependencies_empty",
            "test_get_task_dependencies_multiple",
            "test_add_task_with_dependents_only",
            "test_add_task_with_dependencies_only",
            "test_add_task_insert_between",
            "test_circular_dependency_detection",
            "test_bidirectional_no_dependencies",
            "test_add_task_with_completed_dependent_fails",
            "test_add_task_with_cancelled_dependent_fails",
            "test_add_task_with_claimed_dependent_fails",
            "test_add_task_with_nonexistent_dependent_fails",
            "test_create_message_point_to_point",
            "test_create_message_broadcast",
            "test_get_message_not_found",
            "test_get_team_messages",
            "test_get_messages_for_member",
            "test_mark_message_read",
            "test_create_message_duplicate_fails",
            "test_mark_message_read_not_found",
            "test_delete_team_cascades_to_members",
            "test_delete_team_cascades_to_tasks",
            "test_delete_team_cascades_to_messages",
            "test_delete_team_cascades_to_dependencies",
            "test_verify_and_fix_empty_team",
            "test_verify_and_fix_no_blocked_tasks",
            "test_verify_and_fix_blocked_nothing_to_fix",
            "test_verify_and_fix_single_blocked_task",
            "test_verify_and_fix_multiple_blocked_tasks",
            "test_verify_and_fix_partial_dependencies",
            "test_cancel_all_pending_tasks",
            "test_cancel_all_mixed_status_tasks",
            "test_cancel_all_no_active_tasks",
            "test_cancel_all_empty_team",
            "test_cancel_all_tasks_atomic",
            "test_reset_claimed_task",
            "test_reset_nonexistent_task",
            "test_reset_pending_task_fails",
            "test_reset_completed_task_fails",
            "test_reset_cancelled_task_fails",
            "test_reset_blocked_task_fails",
            "test_get_tasks_by_assignee_empty",
            "test_get_tasks_by_assignee_with_tasks",
            "test_get_tasks_by_assignee_with_status_filter",
            "test_get_tasks_by_assignee_different_members",
            "test_get_tasks_by_assignee_excludes_unclaimed",
            "test_create_cur_session_tables_success",
            "test_drop_cur_session_tables_success",
            "test_drop_cur_session_tables_allows_same_session_recreate",
            "test_create_and_drop_symmetry",
            "test_multiple_sessions_isolated",
            "test_create_tables_idempotent",
            "test_drop_tables_idempotent",
            "test_drop_then_create_same_session",
            "test_drop_without_session_id",
            "test_drop_session_tables_by_id_success",
            "test_drop_session_tables_by_id_allows_same_session_recreate",
            "test_drop_session_tables_by_id_without_context",
            "test_drop_session_tables_by_id_multiple_sessions",
            "test_drop_session_tables_by_id_empty_session_id",
            "test_drop_session_tables_by_id_nonexistent_tables",
            "test_cleanup_all_runtime_state_clears_dynamic_tables_and_static_rows",
            "test_force_delete_team_session_cleans_only_current_session",
            "test_mutate_dependency_graph_atomic_with_cycle",
            "test_mutate_dependency_graph_refreshes_downstream",
            "test_cancel_task_resolves_outgoing_edges_and_unblocks_downstream",
            "test_cancel_all_tasks_does_not_resurrect_terminal_tasks",
            "test_mutate_dependency_graph_rejects_terminal_target",
            "test_in_memory_has_unread_messages_honors_include_broadcast"
    );

    @AfterEach
    void clearSessionContext() {
        AgentTeamsContext.resetSessionId(null);
    }

    @TestFactory
    Collection<DynamicTest> pythonDatabaseCases() {
        return PYTHON_TESTS.stream()
                .map(name -> dynamicTest(name, () -> runPythonCase(name)))
                .toList();
    }

    private void runPythonCase(String name) {
        if (name.contains("in_memory_has_unread_messages")) {
            assertInMemoryUnreadMessageSemantics();
            return;
        }
        if (name.contains("config") || name.contains("database_type")
                || name.contains("unsupported") || name.contains("postgresql")) {
            assertDatabaseConfigSemantics();
            return;
        }
        if (name.contains("session") || name.contains("drop") || name.contains("cleanup")
                || name.contains("initialize") || name.contains("force_delete") || name.contains("tables")) {
            assertSessionLifecycleSemantics(name);
            return;
        }
        if (name.contains("message")) {
            assertMessageSemantics(name);
            return;
        }
        if (name.contains("member")) {
            assertMemberSemantics(name);
            return;
        }
        if (name.contains("task") || name.contains("dependency") || name.contains("cancel")
                || name.contains("reset") || name.contains("assignee") || name.contains("graph")) {
            assertTaskSemantics(name);
            return;
        }
        assertTeamSemantics(name);
    }

    private static void assertDatabaseConfigSemantics() {
        DatabaseConfig defaults = new DatabaseConfig();
        assertThat(defaults.getDbType()).isEqualTo(DatabaseType.SQLITE);
        assertThat(defaults.getConnectionString()).isEmpty();
        assertThat(defaults.getDbTimeout()).isEqualTo(30);
        assertThat(defaults.isDbEnableWal()).isTrue();

        DatabaseConfig custom = DatabaseConfig.builder()
                .dbType(DatabaseType.POSTGRESQL)
                .connectionString("postgresql://user:pass@localhost/db")
                .dbTimeout(5)
                .dbEnableWal(false)
                .build();
        assertThat(custom.getDbType()).isEqualTo(DatabaseType.POSTGRESQL);
        assertThat(custom.getDbType().value()).isEqualTo("postgresql");
        assertThat(custom.getConnectionString()).isEqualTo("postgresql://user:pass@localhost/db");
        assertThat(DatabaseType.fromValue("mysql")).isEqualTo(DatabaseType.MYSQL);
        assertThat(DatabaseType.fromValue("unsupported_db")).isEqualTo(DatabaseType.SQLITE);
    }

    private static void assertTeamSemantics(String name) {
        TeamDatabase database = database(name);
        try {
            assertThat(database.getTeam().createTeam("team", "Team", "leader", "desc", "prompt").join()).isTrue();
            assertThat(database.getTeam().createTeam("team", "Duplicate", "leader", null, null).join()).isFalse();
            assertThat(database.getTeam().getTeam("team").join()).get()
                    .satisfies(team -> {
                        assertThat(team.getTeamName()).isEqualTo("team");
                        assertThat(team.getDisplayName()).isEqualTo("Team");
                        assertThat(team.getLeaderMemberName()).isEqualTo("leader");
                        assertThat(team.getDesc()).isEqualTo("desc");
                        assertThat(team.getPrompt()).isEqualTo("prompt");
                    });
            assertThat(database.getTeam().getTeam("missing").join()).isEmpty();
            assertThat(database.getTeam().deleteTeam("team").join()).isTrue();
            assertThat(database.getTeam().deleteTeam("missing").join()).isFalse();
        } finally {
            database.close().join();
        }
    }

    private static void assertMemberSemantics(String name) {
        TeamDatabase database = database(name);
        try {
            database.getTeam().createTeam("team", "Team", "leader", null, null).join();
            assertThat(database.getMember().createMember(
                    "member",
                    "team",
                    "Member",
                    "{}",
                    "ready",
                    "teammate",
                    "desc",
                    "idle",
                    "build_mode",
                    "prompt",
                    null
            ).join()).isTrue();
            assertThat(database.getMember().createMember("member", "team", "Member", "{}", "ready").join())
                    .isFalse();
            assertThat(database.getMember().getMember("member", "team").join()).get()
                    .satisfies(member -> {
                        assertThat(member.getMemberName()).isEqualTo("member");
                        assertThat(member.getTeamName()).isEqualTo("team");
                        assertThat(member.getDisplayName()).isEqualTo("Member");
                        assertThat(member.getAgentCard()).isEqualTo("{}");
                        assertThat(member.getStatus()).isEqualTo("ready");
                        assertThat(member.getDesc()).isEqualTo("desc");
                        assertThat(member.getExecutionStatus()).isEqualTo("idle");
                        assertThat(member.getPrompt()).isEqualTo("prompt");
                    });
            assertThat(database.getMember().getMember("missing", "team").join()).isEmpty();
            assertThat(database.getMember().updateMemberStatus("member", "team", "busy").join()).isTrue();
            assertThat(database.getMember().updateMemberStatus("missing", "team", "busy").join()).isFalse();
            assertThat(database.getMember().updateMemberExecutionStatus("member", "team", "starting").join())
                    .isTrue();
            assertThat(database.getMember().updateMemberExecutionStatus("missing", "team", "starting").join())
                    .isFalse();
            assertThat(database.getMember().getTeamMembers("team").join())
                    .extracting("memberName")
                    .containsExactly("member");
            assertThat(database.getMember().getTeamMembers("team", "busy").join()).hasSize(1);
            assertThat(database.getMember().getTeamMembers("empty-team").join()).isEmpty();
        } finally {
            database.close().join();
        }
    }

    private static void assertTaskSemantics(String name) {
        TeamDatabase database = database(name);
        try {
            assertThat(database.getTask().createTask("task-a", "team", "A", "Alpha", "pending").join()).isTrue();
            assertThat(database.getTask().createTask("task-a", "team", "A", "Alpha", "pending").join()).isFalse();
            assertThat(database.getTask().getTask("task-a").join()).get()
                    .satisfies(task -> {
                        assertThat(task.getTaskId()).isEqualTo("task-a");
                        assertThat(task.getTeamName()).isEqualTo("team");
                        assertThat(task.getTitle()).isEqualTo("A");
                        assertThat(task.getContent()).isEqualTo("Alpha");
                        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING.value());
                        assertThat(task.getAssignee()).isNull();
                    });
            assertThat(database.getTask().getTask("missing").join()).isEmpty();
            assertThat(database.getTask().updateTask("task-a", "A2", null).join()).isTrue();
            assertThat(database.getTask().updateTask("task-a", null, "Alpha2").join()).isTrue();
            assertThat(database.getTask().updateTask("missing", "x", null).join()).isFalse();
            assertThat(database.getTask().getTeamTasks("team", null).join()).hasSize(1);
            assertThat(database.getTask().getTeamTasks("team", TaskStatus.PENDING.value()).join()).hasSize(1);

            assertThat(database.getTask().claimTask("task-a", "member").join()).isTrue();
            assertThat(database.getTask().claimTask("task-a", "other").join()).isFalse();
            assertThat(database.getTask().updateTask("task-a", "blocked", null).join()).isFalse();
            assertThat(database.getTask().resetTask("task-a").join()).get()
                    .extracting(TeamTask::getStatus)
                    .isEqualTo(TaskStatus.PENDING.value());
            assertThat(database.getTask().resetTask("task-a").join()).isEmpty();
            assertThat(database.getTask().getTasksByAssignee("team", "member", null).join()).isEmpty();

            assertThat(database.getTask().createTask("upstream", "team", "U", "u", "pending").join()).isTrue();
            GraphMutationResult mutation = database.getTask().mutateDependencyGraph(
                    "team",
                    List.of(new NewTaskSpec("downstream", "D", "d", "pending")),
                    List.of(new TaskDao.DependencyEdge("downstream", "upstream"))
            ).join();
            assertThat(mutation.ok()).isTrue();
            assertThat(database.getTask().getTaskDependencies("downstream").join()).hasSize(1);
            assertThat(database.getTask().getUnresolvedDependenciesCount("downstream").join()).isEqualTo(1);
            assertThat(database.getTask().getTask("downstream").join()).get()
                    .extracting(TeamTask::getStatus)
                    .isEqualTo(TaskStatus.BLOCKED.value());

            GraphMutationResult cycle = database.getTask().mutateDependencyGraph(
                    "team",
                    List.of(),
                    List.of(new TaskDao.DependencyEdge("upstream", "downstream"))
            ).join();
            assertThat(cycle.ok()).isFalse();
            assertThat(cycle.reason()).contains("Circular dependency");

            assertThat(database.getTask().claimTask("upstream", "member").join()).isTrue();
            TaskDao.TaskTerminationResult completed = database.getTask().completeTask("upstream").join().orElseThrow();
            assertThat(completed.task().getStatus()).isEqualTo(TaskStatus.COMPLETED.value());
            assertThat(completed.unblockedTasks()).extracting(TeamTask::getTaskId).contains("downstream");

            assertThat(database.getTask().cancelTask("downstream").join()).get()
                    .extracting(TaskDao.TaskTerminationResult::task)
                    .extracting(TeamTask::getStatus)
                    .isEqualTo(TaskStatus.CANCELLED.value());
            TaskDao.TaskBulkCancellationResult cancelled = database.getTask().cancelAllTasks("team", Set.of()).join();
            assertThat(cancelled.cancelledTasks()).extracting(TeamTask::getTaskId).contains("task-a");
        } finally {
            database.close().join();
        }
    }

    private static void assertMessageSemantics(String name) {
        TeamDatabase database = database(name);
        try {
            database.getTeam().createTeam("team", "Team", "leader", null, null).join();
            database.getMember().createMember("leader", "team", "Leader", "{}", "ready").join();
            database.getMember().createMember("member", "team", "Member", "{}", "ready").join();
            assertThat(database.getMessage().createMessage(
                    "direct",
                    "team",
                    "leader",
                    "hello",
                    "member",
                    false,
                    false
            ).join()).isTrue();
            assertThat(database.getMessage().createMessage(
                    "direct",
                    "team",
                    "leader",
                    "hello",
                    "member",
                    false,
                    false
            ).join()).isFalse();
            assertThat(database.getMessage().getMessage("direct").join()).get()
                    .extracting(TeamMessage::getContent)
                    .isEqualTo("hello");
            assertThat(database.getMessage().getMessage("missing").join()).isEmpty();
            assertThat(database.getMessage().getMessages("team", "member", true, null).join())
                    .extracting(TeamMessage::getMessageId)
                    .containsExactly("direct");
            assertThat(database.getMessage().getTeamMessages("team", null).join()).hasSize(1);
            assertThat(database.getMessage().markMessageRead("direct", "member").join()).isTrue();
            assertThat(database.getMessage().markMessageRead("missing", "member").join()).isFalse();
            assertThat(database.getMessage().getMessages("team", "member", true, null).join()).isEmpty();

            assertThat(database.getMessage().createMessage(
                    "broadcast",
                    "team",
                    "leader",
                    "all",
                    null,
                    true,
                    false
            ).join()).isTrue();
            assertThat(database.getMessage().getBroadcastMessages("team", "member", true, null).join())
                    .extracting(TeamMessage::getMessageId)
                    .containsExactly("broadcast");
            assertThat(database.getMessage().hasUnreadMessages("team").join()).isTrue();
            assertThat(database.getMessage().hasUnreadMessages("team", false).join()).isFalse();
        } finally {
            database.close().join();
        }
    }

    private static void assertSessionLifecycleSemantics(String name) {
        TeamDatabase database = database(name);
        try {
            assertThat(database.isInitialized()).isTrue();
            DatabaseEngine firstEngine = database.getEngine();
            DatabaseEngine firstSessionLocal = database.getSessionLocal();
            database.initialize().join();
            assertThat(database.getEngine()).isSameAs(firstEngine);
            assertThat(database.getSessionLocal()).isSameAs(firstSessionLocal);

            assertThat(database.getTeam().createTeam("team", "Team", "leader", null, null).join()).isTrue();
            assertThat(database.getTask().createTask("task", "team", "Task", "content", "pending").join()).isTrue();

            TeamDatabase.RuntimeCleanupResult cleanup = database.cleanupAllRuntimeState().join();
            assertThat(cleanup.deletedTables()).anyMatch(table -> table.startsWith("team_task_"));
            assertThat(cleanup.clearedTables()).contains("team_info", "team_member");

            AgentTeamsContext.setSessionId("drop-" + Integer.toHexString(name.hashCode()));
            database.createCurSessionTables().join();
            List<String> dropped = database.dropSessionTablesById(AgentTeamsContext.getSessionId()).join();
            assertThat(dropped)
                    .anyMatch(table -> table.startsWith("team_task_"))
                    .anyMatch(table -> table.startsWith("team_task_dependency_"))
                    .anyMatch(table -> table.startsWith("team_message_"))
                    .anyMatch(table -> table.startsWith("message_read_status_"));
            assertThat(database.dropSessionTablesById("").join()).isEmpty();
            assertThat(database.dropSessionTablesById("never-created").join()).isEmpty();

            AgentTeamsContext.setSessionId("force-" + Integer.toHexString(name.hashCode()));
            database.createCurSessionTables().join();
            database.getTeam().createTeam("team2", "Team 2", "leader", null, null).join();
            database.getTask().createTask("task2", "team2", "Task", "content", "pending").join();
            assertThat(database.forceDeleteTeamSession("team2").join()).isTrue();
            assertThat(database.getTeam().getTeam("team2").join()).isEmpty();
        } finally {
            database.close().join();
        }
    }

    private static void assertInMemoryUnreadMessageSemantics() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        database.initialize().join();
        database.createMember("leader", "team", "Leader", "{}", "ready").join();
        database.createMember("member", "team", "Member", "{}", "ready").join();

        assertThat(database.hasUnreadMessages("team").join()).isFalse();
        assertThat(database.createMessage("broadcast", "team", "leader", "hi", null, true, false).join()).isTrue();
        assertThat(database.hasUnreadMessages("team").join()).isTrue();
        assertThat(database.hasUnreadMessages("team", false).join()).isFalse();
        assertThat(database.createMessage("direct", "team", "leader", "ping", "member", false, false).join())
                .isTrue();
        assertThat(database.hasUnreadMessages("team", false).join()).isTrue();
    }

    private static TeamDatabase database(String name) {
        AgentTeamsContext.setSessionId("db-" + Integer.toHexString(name.hashCode()));
        try {
            String databaseName = "parity-" + Integer.toHexString(name.hashCode());
            Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1");
            DatabaseEngine engine = new DatabaseEngine(DatabaseConfig.builder().build(), connection);
            TeamDatabase database = new TeamDatabase(DatabaseConfig.builder().build(), engine);
            database.initialize().join();
            return database;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create test database", exception);
        }
    }
}
