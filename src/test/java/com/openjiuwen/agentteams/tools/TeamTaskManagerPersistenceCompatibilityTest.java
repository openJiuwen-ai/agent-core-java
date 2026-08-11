/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.agentteams.messager.InProcessMessager;
import com.openjiuwen.agentteams.messager.MessagerTransportConfig;
import com.openjiuwen.agentteams.tools.database.DatabaseConfig;
import com.openjiuwen.agentteams.tools.database.DatabaseType;
import com.openjiuwen.agentteams.tools.database.TeamDatabase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

class TeamTaskManagerPersistenceCompatibilityTest {
    private static final String SESSION_ID = "assignment_failure";
    private static final String TASK_TABLE = TeamDatabase.sessionTableNames(SESSION_ID).get(0);

    @AfterEach
    void cleanupMessager() {
        InProcessMessager.cleanupInprocessBus();
    }

    @Test
    void failedAssigneePersistenceShouldRemoveCreatedTask() throws SQLException {
        Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:task_assignment_failure;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        try (TeamDatabase database = createDatabase(connection)) {
            rejectPersistedAssignees(connection);
            InProcessMessager messager = new InProcessMessager(
                    MessagerTransportConfig.builder().nodeId("leader").build());
            TeamTaskManager taskManager = new TeamTaskManager(
                    "team-persistence", "leader", database, messager, SESSION_ID);

            assertThatThrownBy(() -> taskManager.add(
                    "Orphan candidate", "Assignment must fail", "orphan-task", List.of(), "worker"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Failed to persist JDBC session rows");

            assertThat(database.task.getTask("orphan-task")).isNull();
            assertPersistedTaskCount(connection, 0);
        }
    }

    private static TeamDatabase createDatabase(Connection connection) {
        TeamDatabase database = new TeamDatabase(
                DatabaseConfig.builder().dbType(DatabaseType.POSTGRESQL).build(), connection);
        database.setTeamSessionId(SESSION_ID);
        database.initialize();
        database.team.createTeam("team-persistence", "Persistence Team", "leader");
        database.member.createMember(TeamDatabase.MemberCreateParams.builder()
                .memberName("worker")
                .teamName("team-persistence")
                .displayName("Worker")
                .status("ready")
                .role("teammate")
                .build());
        return database;
    }

    private static void rejectPersistedAssignees(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + TASK_TABLE
                    + " ADD CONSTRAINT reject_assignee CHECK (assignee IS NULL)");
        }
    }

    private static void assertPersistedTaskCount(Connection connection, int expectedCount) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + TASK_TABLE)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(expectedCount);
        }
    }
}
