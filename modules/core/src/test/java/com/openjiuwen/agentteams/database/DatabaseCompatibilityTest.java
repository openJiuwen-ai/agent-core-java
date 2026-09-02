
package com.openjiuwen.agentteams.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.agentteams.spawn.SpawnContext;
import com.openjiuwen.agentteams.tools.database.DatabaseConfig;
import com.openjiuwen.agentteams.tools.database.DatabaseType;
import com.openjiuwen.agentteams.tools.database.GraphMutationResult;
import com.openjiuwen.agentteams.tools.database.RuntimeCleanupResult;
import com.openjiuwen.agentteams.tools.database.TaskDependencyRecord;
import com.openjiuwen.agentteams.tools.database.TaskMutationResult;
import com.openjiuwen.agentteams.tools.database.TeamDatabase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

class DatabaseCompatibilityTest {
    @TempDir
    Path tempDir;

    private SpawnContext.SessionToken savedToken;

    @BeforeEach
    void clearSpawnContext() {
        // Reset SpawnContext's InheritableThreadLocal before each test so that
        // session-id leaks from earlier test classes (e.g. TeamAgentCompatibilityTest
        // which pins a generated UUID via TeamAgent.applySessionId) do not poison
        // the _global_ fallback relied upon by the non-SpawnContext tests below.
        savedToken = SpawnContext.setSessionId("");
    }

    @AfterEach
    void restoreSpawnContext() {
        SpawnContext.resetSessionId(savedToken);
    }

    @Test
    void databaseConfigShouldExposeExpectedDefaults() {
        DatabaseConfig config = DatabaseConfig.builder().build();
        assertThat(config.getDbType()).isEqualTo(DatabaseType.MEMORY);
        assertThat(config.getConnectionString()).isEmpty();
        assertThat(config.isDbEnableWal()).isTrue();
    }

    @Test
    void databaseShouldNormalizePostgresqlAndMysqlConnectionStringsWithoutExposingCredentials() {
        TeamDatabase postgres = new TeamDatabase(DatabaseConfig.builder().dbType(DatabaseType.POSTGRESQL)
                .connectionString("postgresql://user:pass@localhost:5432/team_db").build());
        TeamDatabase mysql = new TeamDatabase(DatabaseConfig.builder().dbType(DatabaseType.MYSQL)
                .connectionString("mysql://user:pass@localhost:3306/team_db").build());

        assertThat(postgres.normalizedJdbcConnectionString())
                .isEqualTo("jdbc:postgresql://localhost:5432/team_db");
        assertThat(mysql.normalizedJdbcConnectionString()).isEqualTo("jdbc:mysql://localhost:3306/team_db");
        assertThatThrownBy(() -> new TeamDatabase(DatabaseConfig.builder().dbType(DatabaseType.POSTGRESQL)
                .connectionString("postgresql+asyncpg://user:pass@localhost/db").build())
                .normalizedJdbcConnectionString()).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PostgreSQL connectionString");
        assertThatThrownBy(
                () -> new TeamDatabase(DatabaseConfig.builder().dbType(DatabaseType.MYSQL).connectionString("").build())
                        .normalizedJdbcConnectionString())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("MySQL requires");
    }

    @Test
    void databaseShouldCreateAndQueryTeamMemberMessageAndTask() {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().build());
        db.initialize();

        assertThat(db.team.createTeam("team-a", "Team A", "leader")).isTrue();
        assertThat(db.member.createMember(TeamDatabase.MemberCreateParams.builder()
                .memberName("leader").teamName("team-a").displayName("Leader")
                .agentCard("{}").status("busy").mode("build").build()))
                .isTrue();
        assertThat(db.message.createMessage("msg-1", "team-a", "leader", "hello", "member1", false, false)).isTrue();
        assertThat(db.task.createTask("dep-1", "team-a", "Dep", "Dependency", "pending")).isTrue();
        assertThat(db.task.createTask("task-1", "team-a", "Task", "Content", "pending")).isTrue();
        assertThat(db.task.addDependency("task-1", "dep-1")).isTrue();

        assertThat(db.team.getTeam("team-a").getDisplayName()).isEqualTo("Team A");
        assertThat(db.member.getMember("leader", "team-a").getDisplayName()).isEqualTo("Leader");
        assertThat(db.message.getMessages("team-a", "member1", false, null)).hasSize(1);
        assertThat(db.task.getDependencies("task-1")).containsExactly("dep-1");

        db.close();
    }

    @Test
    void memberDaoShouldUpdateLifecycleAndExecutionStatus() {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().build());
        db.initialize();
        db.team.createTeam("team-status", "Team Status", "leader");
        assertThat(db.member.createMember(TeamDatabase.MemberCreateParams.builder()
                .memberName("member-1").teamName("team-status").displayName("Member One")
                .agentCard("{}").status("ready").executionStatus("idle").mode("build").build())).isTrue();

        assertThat(db.member.updateMemberStatus("member-1", "team-status", "busy")).isTrue();
        assertThat(db.member.updateMemberExecutionStatus("member-1", "team-status", "running")).isTrue();

        assertThat(db.member.getMember("member-1", "team-status"))
                .extracting(member -> member.getStatus(), member -> member.getExecutionStatus())
                .containsExactly("busy", "running");
        assertThat(db.member.updateMemberStatus("ghost", "team-status", "busy")).isFalse();
        assertThat(db.member.updateMemberExecutionStatus("ghost", "team-status", "running")).isFalse();
        db.close();
    }

    @Test
    void taskDaoShouldMutateDependencyGraphAtomically() {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().build());
        db.initialize();
        db.team.createTeam("team-graph", "Team Graph", "leader");
        db.task.createTask("A", "team-graph", "A", "content", "pending");
        db.task.createTask("B", "team-graph", "B", "content", "pending");
        db.task.createTask("C", "team-graph", "C", "content", "pending");

        GraphMutationResult first = db.task.mutateDependencyGraph("team-graph", List.of(List.of("A", "B")));
        assertThat(first.isOk()).isTrue();
        assertThat(db.task.getDependencies("A")).containsExactly("B");

        GraphMutationResult cycle =
            db.task.mutateDependencyGraph("team-graph", List.of(List.of("B", "C"), List.of("C", "A")));
        assertThat(cycle.isOk()).isFalse();
        assertThat(cycle.getReason()).contains("Circular dependency");
        assertThat(db.task.getDependencies("B")).isEmpty();
        assertThat(db.task.getDependencies("C")).isEmpty();
        db.close();
    }

    @Test
    void taskDaoShouldExposeDependencyQueriesAndConsistencySweep() {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().build());
        db.initialize();
        db.team.createTeam("team-deps", "Team Deps", "leader");
        db.task.createTask("dep-done", "team-deps", "Done", "content", "completed");
        db.task.createTask("dep-open", "team-deps", "Open", "content", "pending");
        db.task.createTask("blocked", "team-deps", "Blocked", "content", "blocked");
        db.task.createTask("ready", "team-deps", "Ready", "content", "blocked");
        db.task.addDependency("blocked", "dep-done");
        db.task.addDependency("blocked", "dep-open");
        db.task.addDependency("ready", "dep-done");
        db.task.getTask("ready").setStatus("blocked");

        assertThat(db.task.getUnresolvedDependenciesCount("blocked")).isEqualTo(1);
        assertThat(db.task.getUnresolvedDependenciesCount("ready")).isZero();
        assertThat(db.task.getTasksDependingOn("dep-done")).extracting(task -> task.getTaskId())
                .containsExactlyInAnyOrder("blocked", "ready");

        List<com.openjiuwen.agentteams.tools.database.TaskRecord> refreshed =
            db.task.verifyAndFixTaskConsistency("team-deps");

        assertThat(refreshed).extracting(task -> task.getTaskId()).containsExactly("ready");
        assertThat(db.task.getTask("ready").getStatus()).isEqualTo("pending");
        assertThat(db.task.getTask("blocked").getStatus()).isEqualTo("blocked");
        db.close();
    }

    @Test
    void taskDaoMutationsShouldReturnUnblockedTasksLikePythonDao() {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().build());
        db.initialize();
        db.team.createTeam("team-mutation", "Team Mutation", "leader");
        db.task.createTask("A", "team-mutation", "A", "content", "claimed");
        db.task.createTask("B", "team-mutation", "B", "content", "blocked");
        db.task.addDependency("B", "A");

        TaskMutationResult completed = db.task.completeTaskResult("A");

        assertThat(completed).isNotNull();
        assertThat(completed.getTask().getTaskId()).isEqualTo("A");
        assertThat(completed.getUnblockedTasks()).extracting(task -> task.getTaskId()).containsExactly("B");
        assertThat(db.task.getTask("B").getStatus()).isEqualTo("pending");

        db.task.createTask("C", "team-mutation", "C", "content", "pending");
        db.task.createTask("D", "team-mutation", "D", "content", "blocked");
        db.task.addDependency("D", "C");
        TaskMutationResult cancelled = db.task.cancelTaskResult("C");

        assertThat(cancelled).isNotNull();
        assertThat(cancelled.getTask().getTaskId()).isEqualTo("C");
        assertThat(cancelled.getUnblockedTasks()).extracting(task -> task.getTaskId()).containsExactly("D");
        assertThat(db.task.getTask("D").getStatus()).isEqualTo("pending");
        db.close();
    }

    @Test
    void taskDaoShouldCreateBidirectionalDependenciesAtomicallyLikePythonDao() {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().build());
        db.initialize();
        db.team.createTeam("team-bidir", "Team Bidir", "leader");
        db.task.createTask("taskA", "team-bidir", "A", "content", "completed");
        db.task.createTask("taskB", "team-bidir", "B", "content", "pending");
        db.task.addDependency("taskB", "taskA");

        boolean created = db.task.addTaskWithBidirectionalDependencies(TeamDatabase.TaskDependencyParams.builder()
                .taskId("taskM").teamName("team-bidir").title("Middle").content("content")
                .status("blocked").dependencies(List.of("taskA")).dependentTaskIds(List.of("taskB")).build());

        assertThat(created).isTrue();
        assertThat(db.task.getTask("taskM").getStatus()).isEqualTo("pending");
        assertThat(db.task.getTaskDependencies("taskM"))
                .extracting(TaskDependencyRecord::getDependsOnTaskId, TaskDependencyRecord::isResolved)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("taskA", true));
        assertThat(db.task.getTaskDependencies("taskB")).extracting(TaskDependencyRecord::getDependsOnTaskId)
                .containsExactlyInAnyOrder("taskA", "taskM");
        assertThat(db.task.getTask("taskB").getStatus()).isEqualTo("blocked");

        boolean cycle = db.task.addTaskWithBidirectionalDependencies(TeamDatabase.TaskDependencyParams.builder()
                .taskId("taskC").teamName("team-bidir").title("Cycle").content("content")
                .status("blocked").dependencies(List.of("taskB")).dependentTaskIds(List.of("taskA")).build());

        assertThat(cycle).isFalse();
        assertThat(db.task.getTask("taskC")).isNull();
        assertThat(db.task.getTaskDependencies("taskA")).isEmpty();
        db.close();
    }

    @Test
    void taskDaoShouldDeleteTaskAndDependencyRowsLikePythonCascadeBoundary() {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().build());
        db.initialize();
        db.team.createTeam("team-delete", "Team Delete", "leader");
        db.task.createTask("dep", "team-delete", "Dep", "content", "pending");
        db.task.createTask("task", "team-delete", "Task", "content", "blocked");
        db.task.addDependency("task", "dep");

        assertThat(db.task.deleteTask("ghost")).isFalse();
        assertThat(db.task.deleteTask("dep")).isTrue();

        assertThat(db.task.getTask("dep")).isNull();
        assertThat(db.task.getTaskDependencies("task")).isEmpty();
        db.close();
    }

    @Test
    void daoShouldExposeTimestampProbeMessageLookupBroadcastFilterAndAssigneeQueries() {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().build());
        db.initialize();
        db.team.createTeam("team-dao-gap", "Team DAO Gap", "leader");
        db.member.createMember(TeamDatabase.MemberCreateParams.builder()
                .memberName("member-a").teamName("team-dao-gap").displayName("Member A")
                .agentCard("{}").status("ready").executionStatus("idle").mode("build").build());
        db.member.createMember(TeamDatabase.MemberCreateParams.builder()
                .memberName("member-b").teamName("team-dao-gap").displayName("Member B")
                .agentCard("{}").status("busy").executionStatus("running").mode("build").build());
        db.message.createMessage("direct-1", "team-dao-gap", "leader", "direct", "member-a", false, false);
        db.message.createMessage("broadcast-1", "team-dao-gap", "leader", "broadcast", null, true, false);
        db.task.createTask("assigned", "team-dao-gap", "Assigned", "content", "pending");
        db.task.claimTask("assigned", "member-a");

        assertThat(db.team.getTeamUpdatedAt("team-dao-gap")).isPositive();
        assertThat(db.team.getTeamUpdatedAt("missing-team")).isZero();
        assertThat(db.member.getMembersMaxUpdatedAt("team-dao-gap")).isPositive();
        assertThat(db.member.getMembersMaxUpdatedAt("missing-team")).isZero();
        assertThat(db.message.getMessage("direct-1").getContent()).isEqualTo("direct");
        assertThat(db.message.getTeamMessages("team-dao-gap", false)).extracting(message -> message.getMessageId())
                .containsExactly("direct-1");
        assertThat(db.message.getTeamMessages("team-dao-gap", true)).extracting(message -> message.getMessageId())
                .containsExactly("broadcast-1");
        assertThat(db.task.getTasksByAssignee("team-dao-gap", "member-a", "claimed"))
                .extracting(task -> task.getTaskId()).containsExactly("assigned");
        assertThat(db.task.getTasksByAssignee("team-dao-gap", "member-a", "pending")).isEmpty();
        db.close();
    }

    @Test
    void taskDaoShouldUpdateStatusAndRefreshDependentsLikePythonDao() {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().build());
        db.initialize();
        db.team.createTeam("team-status-update", "Team Status Update", "leader");
        db.task.createTask("dep", "team-status-update", "Dep", "content", "claimed");
        db.task.createTask("blocked", "team-status-update", "Blocked", "content", "blocked");
        db.task.addDependency("blocked", "dep");

        assertThat(db.task.updateTaskStatus("ghost", "completed")).isFalse();
        assertThat(db.task.updateTaskStatus("blocked", "completed")).isFalse();
        assertThat(db.task.updateTaskStatus("dep", "completed")).isTrue();

        assertThat(db.task.getTask("dep").getStatus()).isEqualTo("completed");
        assertThat(db.task.getTask("blocked").getStatus()).isEqualTo("pending");
        db.close();
    }

    @Test
    void databaseShouldCreateDropAndRecreateCurrentSessionTablesLikePython() {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().build());
        try {
            db.initialize();
            db.team.createTeam("team-session", "Team Session", "leader");
            assertThat(db.task.createTask("task-1", "team-session", "Task 1", "content", "pending")).isTrue();
            assertThat(db.task.getTask("task-1")).isNotNull();

            // TeamDatabase uses a process-global session key ("_global_") for in-memory rows.
            List<String> dropped = db.dropCurSessionTables();

            assertThat(dropped).containsExactlyElementsOf(TeamDatabase.sessionTableNames("_global_"));
            // Dropped sessions stay unusable until explicitly recreated (Python: "no such table").
            assertThatThrownBy(() -> db.task.getTask("task-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("_global_");

            assertThat(db.createCurSessionTables()).isTrue();
            assertThat(db.task.getTask("task-1")).isNull();
            assertThat(db.task.createTask("task-2", "team-session", "Task 2", "content", "pending")).isTrue();
            assertThat(db.task.getTask("task-2")).isNotNull();
        } finally {
            db.close();
        }
    }

    @Test
    void databaseShouldIsolateDynamicRowsAcrossSpawnContextSessionIds() {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().build());
        SpawnContext.SessionToken token = SpawnContext.setSessionId("java-session-a");
        try {
            db.initialize();
            db.team.createTeam("team-session-a", "Team A", "leader");
            db.task.createTask("task-a", "team-session-a", "Task A", "content", "pending");
            db.message.createMessage("msg-a", "team-session-a", "leader", "hello", "member", false, false);

            SpawnContext.resetSessionId(token);
            token = SpawnContext.setSessionId("java-session-b");
            assertThat(db.createCurSessionTables()).isTrue();
            db.team.createTeam("team-session-b", "Team B", "leader");
            db.task.createTask("task-b", "team-session-b", "Task B", "content", "pending");
            db.message.createMessage("msg-b", "team-session-b", "leader", "hello", "member", false, false);

            // SpawnContext session id scopes dynamic rows (parity with Python per-session tables).
            assertThat(db.task.getTask("task-b")).isNotNull();
            assertThat(db.task.getTask("task-a")).isNull();
            assertThat(db.message.getTeamMessages("team-session-b")).extracting(message -> message.getMessageId())
                    .containsExactly("msg-b");
            assertThat(db.message.getTeamMessages("team-session-a")).isEmpty();

            SpawnContext.resetSessionId(token);
            token = SpawnContext.setSessionId("java-session-a");
            assertThat(db.task.getTask("task-a")).isNotNull();
            assertThat(db.task.getTask("task-b")).isNull();
            assertThat(db.message.getTeamMessages("team-session-a")).extracting(message -> message.getMessageId())
                    .containsExactly("msg-a");
        } finally {
            db.close();
            SpawnContext.resetSessionId(token);
        }
    }

    @Test
    void databaseShouldDropSessionTablesByIdWithoutActiveContext() {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().build());
        try {
            db.initialize();
            db.team.createTeam("team-drop-target", "Team Drop Target", "leader");
            db.task.createTask("task-target", "team-drop-target", "Task", "content", "pending");

            List<String> dropped = db.dropSessionTablesById("_global_");

            assertThat(dropped).containsExactlyElementsOf(TeamDatabase.sessionTableNames("_global_"));
            assertThat(db.activeDynamicTables()).isEmpty();
            assertThatThrownBy(() -> db.task.getTask("task-target"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("_global_");
        } finally {
            db.close();
        }
    }

    @Test
    void databaseShouldCleanupAllRuntimeStateLikePythonStorageHelper() {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().build());
        try {
            db.initialize();
            db.team.createTeam("team-cleanup", "Team Cleanup", "leader");
            db.member.createMember(TeamDatabase.MemberCreateParams.builder()
                    .memberName("member").teamName("team-cleanup").displayName("Member")
                    .agentCard("{}").status("ready").mode("build").build());
            db.task.createTask("task-a", "team-cleanup", "Task A", "content", "pending");
            db.createCurSessionTables();
            db.task.createTask("task-b", "team-cleanup", "Task B", "content", "pending");

            RuntimeCleanupResult result = db.cleanupAllRuntimeState();

            assertThat(result.getDeletedTables()).containsAll(TeamDatabase.sessionTableNames("_global_"));
            assertThat(result.getClearedTables()).containsExactly("team_info", "team_member");
            assertThat(db.activeDynamicTables()).isEmpty();
            assertThat(db.team.getTeam("team-cleanup")).isNull();
            assertThat(db.member.getMember("member", "team-cleanup")).isNull();
        } finally {
            db.close();
        }
    }

    @Test
    void sqliteDatabaseShouldPersistStaticAndDynamicSessionRowsAcrossInstances() {
        Path dbPath = tempDir.resolve("team-persistent.db");
        DatabaseConfig config =
            DatabaseConfig.builder().dbType(DatabaseType.SQLITE).connectionString(dbPath.toString()).build();
        SpawnContext.SessionToken token = SpawnContext.setSessionId("sqlite-session-main");
        try {
            TeamDatabase first = new TeamDatabase(config);
            first.initialize();
            first.team.createTeam("team-sqlite", "Team SQLite", "leader", "desc", "prompt");
            first.member.createMember(TeamDatabase.MemberCreateParams.builder()
                    .memberName("leader").teamName("team-sqlite").displayName("Leader")
                    .agentCard("{}").status("ready").desc("leader desc").executionStatus("idle")
                    .mode("build").prompt("prompt").build());
            first.task.createTask("dep", "team-sqlite", "Dep", "content", "claimed");
            first.task.createTask("task", "team-sqlite", "Task", "content", "blocked");
            first.task.addDependency("task", "dep");
            first.message.createMessage("direct", "team-sqlite", "leader", "hello", "worker", false, false, 10L);
            first.message.createMessage("broadcast", "team-sqlite", "leader", "news", null, true, false, 20L);
            first.message.markMessageRead("broadcast", "leader");
            first.close();

            TeamDatabase second = new TeamDatabase(config);
            second.initialize();

            assertThat(second.team.getTeam("team-sqlite").getDisplayName()).isEqualTo("Team SQLite");
            assertThat(second.member.getMember("leader", "team-sqlite").getStatus()).isEqualTo("ready");
            assertThat(second.task.getTask("task").getDependencies()).containsExactly("dep");
            assertThat(second.message.getMessage("direct").getContent()).isEqualTo("hello");
            assertThat(second.message.getBroadcastMessages("team-sqlite", "leader", true, null)).isEmpty();

            second.task.completeTaskResult("dep");
            second.close();

            TeamDatabase third = new TeamDatabase(config);
            third.initialize();

            assertThat(third.task.getTask("dep").getStatus()).isEqualTo("completed");
            assertThat(third.task.getTask("task").getStatus()).isEqualTo("pending");
            third.close();
        } finally {
            SpawnContext.resetSessionId(token);
        }
    }
}
