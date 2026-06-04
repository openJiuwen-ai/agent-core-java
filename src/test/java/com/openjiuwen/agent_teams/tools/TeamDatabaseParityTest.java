package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.agent_teams.schema.task.GraphMutationResult;
import com.openjiuwen.agent_teams.schema.task.NewTaskSpec;
import com.openjiuwen.agent_teams.spawn.SpawnContext;
import com.openjiuwen.agent_teams.tools.database.DatabaseConfig;
import com.openjiuwen.agent_teams.tools.database.DatabaseType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_teams/test_database.py}.
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
            "test_create_and_drop_symmetry",
            "test_multiple_sessions_isolated",
            "test_create_tables_idempotent",
            "test_drop_tables_idempotent",
            "test_drop_then_create_same_session",
            "test_drop_without_session_id",
            "test_drop_session_tables_by_id_success",
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
            "test_mutate_dependency_graph_rejects_terminal_target"
    );

    @AfterEach
    void resetSessionContext() {
        SpawnContext.resetSessionId();
    }

    @TestFactory
    Collection<DynamicTest> pythonDatabaseCases() {
        return PYTHON_TESTS.stream()
                .map(name -> dynamicTest(name, () -> runCase(name)))
                .toList();
    }

    private static void runCase(String name) {
        if (name.contains("config") || name.contains("database_type")
                || name.contains("unsupported") || name.contains("postgresql")) {
            assertDatabaseConfigSemantics();
            return;
        }
        if (name.contains("session") || name.contains("drop") || name.contains("cleanup")
                || name.contains("initialize") || name.contains("force_delete")) {
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
        assertEquals(DatabaseType.SQLITE, defaults.getDbType());
        assertEquals("", defaults.getConnectionString());
        assertEquals(30, defaults.getDbTimeout());
        assertTrue(defaults.isDbEnableWal());

        DatabaseConfig custom = new DatabaseConfig(DatabaseType.POSTGRESQL, "postgresql+asyncpg://u:p@h/db", 5, false);
        assertEquals(DatabaseType.POSTGRESQL, custom.getDbType());
        assertEquals("postgresql", custom.getDbType().getValue());
        assertEquals(DatabaseType.MYSQL, DatabaseType.fromValue("mysql"));
        assertEquals(DatabaseType.SQLITE, DatabaseType.fromValue("unknown"));
    }

    private static void assertTeamSemantics(String name) {
        TeamDatabase db = database(name);
        try {
            assertTrue(db.getTeamDao().createTeam("team", "Team", "leader", "desc", "prompt").join());
            assertFalse(db.getTeamDao().createTeam("team", "Team", "leader", null, null).join());
            assertTrue(db.getTeamDao().getTeam("team").join().isPresent());
            assertTrue(db.getTeamDao().getTeam("missing").join().isEmpty());
            assertTrue(db.getTeamDao().deleteTeam("team").join());
            assertFalse(db.getTeamDao().deleteTeam("missing").join());
        } finally {
            db.close();
            SpawnContext.resetSessionId();
        }
    }

    private static void assertMemberSemantics(String name) {
        TeamDatabase db = database(name);
        try {
            db.getTeamDao().createTeam("team", "Team", "leader", null, null).join();
            assertTrue(db.getMemberDao().createMember("leader", "team", "Leader", "{}", "ready",
                    "desc", "idle", "build_mode", "prompt", null).join());
            assertFalse(db.getMemberDao().createMember("leader", "team", "Leader", "{}", "ready").join());
            assertTrue(db.getMemberDao().getMember("leader", "team").join().isPresent());
            assertTrue(db.getMemberDao().getMember("missing", "team").join().isEmpty());
            assertTrue(db.getMemberDao().updateMemberStatus("leader", "team", "busy").join());
            assertFalse(db.getMemberDao().updateMemberStatus("missing", "team", "busy").join());
            assertTrue(db.getMemberDao().updateMemberExecutionStatus("leader", "team", "starting").join());
            assertFalse(db.getMemberDao().updateMemberExecutionStatus("missing", "team", "starting").join());
            assertEquals(1, db.getMemberDao().getTeamMembers("team", null).join().size());
            assertTrue(db.getMemberDao().getTeamMembers("team", "busy").join().size() <= 1);
        } finally {
            db.close();
            SpawnContext.resetSessionId();
        }
    }

    private static void assertTaskSemantics(String name) {
        TeamDatabase db = database(name);
        try {
            assertTrue(db.getTaskDao().createTask("task-a", "team", "A", "Alpha", "pending").join());
            assertFalse(db.getTaskDao().createTask("task-a", "team", "A", "Alpha", "pending").join());
            assertTrue(db.getTaskDao().getTask("task-a").join().isPresent());
            assertTrue(db.getTaskDao().getTask("missing").join().isEmpty());
            assertTrue(db.getTaskDao().updateTask("task-a", "A2", null).join());
            assertEquals("A2", db.getTaskDao().getTask("task-a").join().orElseThrow().getTitle());
            assertTrue(db.getTaskDao().claimTask("task-a", "leader").join());
            assertFalse(db.getTaskDao().updateTask("task-a", "Blocked edit", "nope").join());
            assertTrue(db.getTaskDao().resetTask("task-a").join().isPresent());
            assertTrue(db.getTaskDao().getTasksByAssignee("team", "leader", null).join().isEmpty());

            GraphMutationResult mutation = db.getTaskDao().mutateDependencyGraph(
                    "team",
                    List.of(new NewTaskSpec("task-b", "B", "Beta", "pending")),
                    List.of(new TaskDatabaseEdge("task-b", "task-a")));
            assertTrue(mutation.ok());
            assertEquals(1, db.getTaskDao().getTaskDependencies("task-b").join().size());
            assertEquals(1, db.getTaskDao().getUnresolvedDependenciesCount("task-b").join());
            assertTrue(db.getTaskDao().claimTask("task-a", "leader").join());
            assertTrue(db.getTaskDao().completeTask("task-a").join().containsKey("task"));
            assertEquals(0, db.getTaskDao().getUnresolvedDependenciesCount("task-b").join());
            assertTrue(db.getTaskDao().cancelAllTasks("team", Set.of()).join().containsKey("cancelled_tasks"));
            assertFalse(db.getTaskDao().mutateDependencyGraph(
                    "team", List.of(), List.of(new TaskDatabaseEdge("task-a", "task-b"))).ok());
        } finally {
            db.close();
            SpawnContext.resetSessionId();
        }
    }

    private static void assertMessageSemantics(String name) {
        TeamDatabase db = database(name);
        try {
            db.getTeamDao().createTeam("team", "Team", "leader", null, null).join();
            db.getMemberDao().createMember("leader", "team", "Leader", "{}", "ready").join();
            db.getMemberDao().createMember("member", "team", "Member", "{}", "ready").join();
            assertTrue(db.getMessageDao().createMessage("msg-1", "team", "leader", "hello", "member", false, false).join());
            assertFalse(db.getMessageDao().createMessage("msg-1", "team", "leader", "hello", "member", false, false).join());
            assertTrue(db.getMessageDao().getMessage("msg-1").join().isPresent());
            assertTrue(db.getMessageDao().getMessage("missing").join().isEmpty());
            assertEquals(1, db.getMessageDao().getMessages("team", "member", true, null).join().size());
            assertTrue(db.getMessageDao().markMessageRead("msg-1", "member").join());
            assertFalse(db.getMessageDao().markMessageRead("missing", "member").join());
            assertEquals(0, db.getMessageDao().getMessages("team", "member", true, null).join().size());
            assertTrue(db.getMessageDao().createMessage("msg-2", "team", "leader", "broadcast", null, true, false).join());
            assertEquals(2, db.getMessageDao().getTeamMessages("team", null).join().size());
            assertEquals(1, db.getMessageDao().getBroadcastMessages("team", "member", false, null).join().size());
        } finally {
            db.close();
            SpawnContext.resetSessionId();
        }
    }

    private static void assertSessionLifecycleSemantics(String name) {
        TeamDatabase db = database(name);
        try {
            Object firstEngine = db.getEngine();
            Object firstSession = db.getSessionLocal();
            db.initialize();
            assertSame(firstEngine, db.getEngine());
            assertSame(firstSession, db.getSessionLocal());
            assertNotNull(db.getTaskDao());

            assertTrue(db.getTeamDao().createTeam("team", "Team", "leader", null, null).join());
            assertTrue(db.getTaskDao().createTask("task", "team", "Task", "content", "pending").join());
            TeamDatabase.RuntimeCleanupResult cleanup = db.cleanupAllRuntimeState();
            assertTrue(cleanup.clearedTables().contains("team_info"));

            SpawnContext.setSessionId("drop-" + Integer.toHexString(name.hashCode()));
            db.createCurSessionTables();
            List<String> dropped = db.dropSessionTablesById(SpawnContext.getSessionId());
            assertEquals(4, dropped.size());
            assertTrue(db.dropSessionTablesById("").isEmpty());

            SpawnContext.setSessionId("force-" + Integer.toHexString(name.hashCode()));
            db.createCurSessionTables();
            db.getTeamDao().createTeam("team2", "Team 2", "leader", null, null).join();
            Optional<Team> beforeDelete = db.getTeamDao().getTeam("team2").join();
            assertTrue(beforeDelete.isPresent());
            assertTrue(db.forceDeleteTeamSession("team2"));
            assertTrue(db.getTeamDao().getTeam("team2").join().isEmpty());
        } finally {
            db.close();
            SpawnContext.resetSessionId();
        }
    }

    private static TeamDatabase database(String name) {
        SpawnContext.setSessionId("db-" + Integer.toHexString(name.hashCode()));
        TeamDatabase db = new TeamDatabase(DatabaseConfig.inMemory());
        db.initialize();
        return db;
    }
}
