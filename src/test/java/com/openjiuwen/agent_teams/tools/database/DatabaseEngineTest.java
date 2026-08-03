/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's database-engine lifecycle in
 * {@code openjiuwen/agent_teams/tools/database/engine.py}.
 */
class DatabaseEngineTest {

    @AfterEach
    void clearSessionContext() {
        AgentTeamsContext.resetSessionId(null);
    }

    @Test
    void sanitizeSessionIdMatchesPythonBlake2sSuffix() {
        assertThat(DatabaseEngine.sanitizeSessionIdForTable("session-123"))
                .isEqualTo("9b8de3acbdc8b597");
    }

    @Test
    void ensureTeamMemberRoleColumnBackfillsLegacyTable() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:legacy-role;DB_CLOSE_DELAY=-1")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE team_member (member_name VARCHAR(255), team_name VARCHAR(255), display_name VARCHAR(255), agent_card CLOB, status VARCHAR(255), mode VARCHAR(255))");
            }

            DatabaseEngine engine = new DatabaseEngine(DatabaseConfig.builder().build(), connection);
            engine.initialize().join();

            boolean roleFound = false;
            try (ResultSet resultSet = connection.getMetaData().getColumns(null, null, "TEAM_MEMBER", "ROLE")) {
                roleFound = resultSet.next();
            }
            assertThat(roleFound).isTrue();
        }
    }

    @Test
    void createAndDropCurrentSessionTablesUseContextSessionId() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:session-tables;DB_CLOSE_DELAY=-1")) {
            DatabaseEngine engine = new DatabaseEngine(DatabaseConfig.builder().build(), connection);
            engine.initialize().join();

            AgentTeamsContext.setSessionId("session-123");
            engine.createCurrentSessionTables().join();

            String suffix = DatabaseEngine.sanitizeSessionIdForTable("session-123");
            Set<String> tableNames = engine.getTableNames();
            assertThat(tableNames).contains(
                    "team_task_" + suffix,
                    "team_task_dependency_" + suffix,
                    "team_message_" + suffix,
                    "message_read_status_" + suffix
            );

            engine.dropCurrentSessionTables().join();
            assertThat(engine.getTableNames()).doesNotContain(
                    "team_task_" + suffix,
                    "team_task_dependency_" + suffix,
                    "team_message_" + suffix,
                    "message_read_status_" + suffix
            );
        }
    }

    @Test
    void cleanupAllRuntimeStateDropsDynamicTablesAndClearsStaticRows() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:cleanup;DB_CLOSE_DELAY=-1")) {
            DatabaseEngine engine = new DatabaseEngine(DatabaseConfig.builder().build(), connection);
            engine.initialize().join();

            String suffix = DatabaseEngine.sanitizeSessionIdForTable("cleanup-session");
            try (Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO team_info(team_name, display_name, leader_member_name, created) VALUES ('t1', 'Team', 'lead', 1)");
                statement.execute("INSERT INTO team_member(member_name, team_name, display_name, agent_card, status, mode, role) VALUES ('m1', 't1', 'Member', '{}', 'ready', 'planner', 'teammate')");
                statement.execute("CREATE TABLE \"team_task_" + suffix + "\" (task_id VARCHAR(255) PRIMARY KEY)");
            }

            DatabaseEngine.CleanupResult result = engine.cleanupAllRuntimeState().join();

            assertThat(result.deletedTables()).containsExactly("team_task_" + suffix);
            assertThat(result.clearedTables()).containsExactly("team_info", "team_member");
            assertThat(engine.getTableNames()).doesNotContain("team_task_" + suffix);

            try (Statement statement = connection.createStatement()) {
                ResultSet teamInfoCount = statement.executeQuery("SELECT COUNT(*) FROM team_info");
                teamInfoCount.next();
                assertThat(teamInfoCount.getInt(1)).isZero();
                ResultSet teamMemberCount = statement.executeQuery("SELECT COUNT(*) FROM team_member");
                teamMemberCount.next();
                assertThat(teamMemberCount.getInt(1)).isZero();
            }
        }
    }

    @Test
    void dropSessionTablesByIdRemovesExpectedDynamicTablesOnly() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:drop-session;DB_CLOSE_DELAY=-1")) {
            DatabaseEngine engine = new DatabaseEngine(DatabaseConfig.builder().build(), connection);
            engine.initialize().join();

            String suffix = DatabaseEngine.sanitizeSessionIdForTable("session-abc");
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE \"team_task_" + suffix + "\" (task_id VARCHAR(255) PRIMARY KEY)");
                statement.execute("CREATE TABLE \"team_message_" + suffix + "\" (message_id VARCHAR(255) PRIMARY KEY)");
            }

            List<String> dropped = engine.dropSessionTablesById("session-abc").join();

            assertThat(dropped).containsExactlyInAnyOrder(
                    "team_task_" + suffix,
                    "team_message_" + suffix
            );
            assertThat(engine.getTableNames()).doesNotContain(
                    "team_task_" + suffix,
                    "team_message_" + suffix
            );
        }
    }
}
