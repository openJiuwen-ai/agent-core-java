/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class JdbcTeamStoreCompatibilityTest {
    @ParameterizedTest
    @EnumSource(value = DatabaseType.class, names = {"POSTGRESQL", "MYSQL"})
    void jdbcStoreShouldPersistAndCleanupStaticAndSessionRows(DatabaseType databaseType) throws SQLException {
        try (Connection connection = DriverManager.getConnection(h2Url(databaseType, "store"));
                JdbcTeamStore store = JdbcTeamStore.forConnection(databaseType, connection)) {
            TeamRecord team = teamRecord();
            MemberRecord member = memberRecord("team-jdbc");
            store.replaceStaticRows(List.of(team), List.of(member));

            Map<String, TeamRecord> loadedTeams = new ConcurrentHashMap<>();
            Map<String, MemberRecord> loadedMembers = new ConcurrentHashMap<>();
            store.loadStaticRows(loadedTeams, loadedMembers);
            assertThat(loadedTeams.get("team-jdbc").getDisplayName()).isEqualTo("JDBC Team");
            assertThat(loadedMembers.get("team-jdbc::leader").getRole()).isEqualTo("leader");

            String sessionId = "jdbc-session";
            store.replaceSessionRows(
                    sessionId, taskRecords(), List.of(messageRecord()), Map.of("team-jdbc::leader", 18L));
            verifyLoadedSession(store, sessionId);

            team.setDisplayName("Updated JDBC Team");
            member.setStatus("busy");
            store.replaceStaticRows(List.of(team), List.of(member));
            verifyLoadedSession(store, sessionId);
            store.loadStaticRows(loadedTeams, loadedMembers);
            assertThat(loadedTeams.get("team-jdbc").getDisplayName()).isEqualTo("Updated JDBC Team");
            assertThat(loadedMembers.get("team-jdbc::leader").getStatus()).isEqualTo("busy");

            List<String> deletedTables = store.cleanupAllRuntimeState();
            assertThat(deletedTables).containsAll(TeamDatabase.sessionTableNames(sessionId));
            store.loadStaticRows(loadedTeams, loadedMembers);
            assertThat(loadedTeams).isEmpty();
            assertThat(loadedMembers).isEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(value = DatabaseType.class, names = {"POSTGRESQL", "MYSQL"})
    void jdbcStoreShouldRollbackFailedSnapshot(DatabaseType databaseType) throws SQLException {
        try (Connection connection = DriverManager.getConnection(h2Url(databaseType, "rollback"));
                JdbcTeamStore store = JdbcTeamStore.forConnection(databaseType, connection)) {
            store.replaceStaticRows(List.of(teamRecord()), List.of(memberRecord("team-jdbc")));

            assertThatThrownBy(() -> store.replaceStaticRows(
                    List.of(), List.of(memberRecord("missing-team"))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Failed to persist JDBC team database rows");

            Map<String, TeamRecord> teams = new ConcurrentHashMap<>();
            Map<String, MemberRecord> members = new ConcurrentHashMap<>();
            store.loadStaticRows(teams, members);
            assertThat(teams).containsKey("team-jdbc");
            assertThat(members).containsKey("team-jdbc::leader");
        }
    }

    @ParameterizedTest
    @EnumSource(value = DatabaseType.class, names = {"POSTGRESQL", "MYSQL"})
    void teamDatabaseShouldPersistAcrossInjectedJdbcConnections(DatabaseType databaseType) throws SQLException {
        String jdbcUrl = h2Url(databaseType, "team_database");
        DatabaseConfig config = DatabaseConfig.builder().dbType(databaseType).build();
        Connection firstConnection = DriverManager.getConnection(jdbcUrl);
        TeamDatabase first = new TeamDatabase(config, firstConnection);
        try {
            first.initialize();
            first.team.createTeam("team-jdbc", "JDBC Team", "leader", "description", "prompt");
            first.member.createMember(TeamDatabase.MemberCreateParams.builder()
                    .memberName("leader")
                    .teamName("team-jdbc")
                    .displayName("Leader")
                    .status("ready")
                    .role("leader")
                    .build());
            first.task.createTask("task", "team-jdbc", "Task", "content", "pending");
            first.message.createMessage("message", "team-jdbc", "leader", "hello", "worker", false, false);
        } finally {
            first.close();
        }
        assertThat(firstConnection.isClosed()).isTrue();

        TeamDatabase second = new TeamDatabase(config, DriverManager.getConnection(jdbcUrl));
        try {
            second.initialize();
            assertThat(second.team.getTeam("team-jdbc").getDisplayName()).isEqualTo("JDBC Team");
            assertThat(second.member.getMember("leader", "team-jdbc").getRole()).isEqualTo("leader");
            assertThat(second.task.getTask("task").getContent()).isEqualTo("content");
            assertThat(second.message.getMessage("message").getContent()).isEqualTo("hello");
            second.cleanupAllRuntimeState();
        } finally {
            second.close();
        }
    }

    @ParameterizedTest
    @EnumSource(value = DatabaseType.class, names = {"POSTGRESQL", "MYSQL"})
    void teamDatabaseShouldOwnInjectedConnectionBeforeInitialization(DatabaseType databaseType) throws SQLException {
        Connection connection = DriverManager.getConnection(h2Url(databaseType, "connection_ownership"));
        TeamDatabase database = new TeamDatabase(
                DatabaseConfig.builder().dbType(databaseType).build(), connection);

        database.close();

        assertThat(connection.isClosed()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = DatabaseType.class, names = {"POSTGRESQL", "MYSQL"})
    void teamDatabaseShouldCloseInjectedConnectionWhenInitialLoadFails(DatabaseType databaseType)
            throws SQLException {
        Connection connection = DriverManager.getConnection(h2Url(databaseType, "failed_load"));
        try (java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE team_info (team_name VARCHAR(255) PRIMARY KEY)");
        }
        TeamDatabase database = new TeamDatabase(
                DatabaseConfig.builder().dbType(databaseType).build(), connection);

        assertThatThrownBy(database::initialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to load JDBC team database rows");
        assertThat(connection.isClosed()).isTrue();
    }

    private static void verifyLoadedSession(JdbcTeamStore store, String sessionId) {
        Map<String, TaskRecord> tasks = new ConcurrentHashMap<>();
        Map<String, MessageRecord> messages = new ConcurrentHashMap<>();
        Map<String, Long> readStatuses = new ConcurrentHashMap<>();
        store.loadSessionRows(sessionId, tasks, messages, readStatuses);

        assertThat(tasks.get("task").getDependencies()).containsExactly("dependency");
        assertThat(messages.get("message").getContent()).isEqualTo("hello");
        assertThat(readStatuses).containsEntry("team-jdbc::leader", 18L);
    }

    private static String h2Url(DatabaseType databaseType, String purpose) {
        String mode = databaseType == DatabaseType.POSTGRESQL ? "PostgreSQL" : "MySQL";
        return "jdbc:h2:mem:jdbc_store_" + databaseType.name().toLowerCase(java.util.Locale.ROOT)
                + "_" + purpose + ";MODE=" + mode + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    }

    private static TeamRecord teamRecord() {
        return TeamRecord.builder()
                .teamName("team-jdbc")
                .displayName("JDBC Team")
                .leaderMemberName("leader")
                .desc("description")
                .prompt("prompt")
                .created(10L)
                .updatedAt(11L)
                .build();
    }

    private static MemberRecord memberRecord(String teamName) {
        return MemberRecord.builder()
                .memberName("leader")
                .teamName(teamName)
                .displayName("Leader")
                .agentCard("{}")
                .status("ready")
                .desc("description")
                .executionStatus("idle")
                .mode("build")
                .prompt("prompt")
                .modelRefJson("{}")
                .updatedAt(12L)
                .role("leader")
                .build();
    }

    private static List<TaskRecord> taskRecords() {
        TaskRecord dependency = TaskRecord.builder()
                .taskId("dependency")
                .teamName("team-jdbc")
                .title("Dependency")
                .content("content")
                .status("completed")
                .updatedAt(13L)
                .build();
        TaskRecord task = TaskRecord.builder()
                .taskId("task")
                .teamName("team-jdbc")
                .title("Task")
                .content("content")
                .status("blocked")
                .dependencies(new ArrayList<>(List.of("dependency")))
                .updatedAt(14L)
                .build();
        return List.of(dependency, task);
    }

    private static MessageRecord messageRecord() {
        return MessageRecord.builder()
                .messageId("message")
                .teamName("team-jdbc")
                .fromMemberName("leader")
                .toMemberName("worker")
                .content("hello")
                .timestamp(15L)
                .broadcast(false)
                .isRead(false)
                .build();
    }
}
