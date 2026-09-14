/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link TeamDatabase}.
 *
 * <p>Mirrors Python's {@code TeamDatabase} in
 * {@code openjiuwen/agent_teams/tools/database/__init__.py}.</p>
 */
class TeamDatabaseTest {

    @AfterEach
    void clearSessionContext() {
        AgentTeamsContext.resetSessionId(null);
    }

    @Test
    void initializeWiresEngineAndConcreteDaosOnlyOnce() throws Exception {
        AgentTeamsContext.setSessionId("team-db-init");
        TeamDatabase database = newDatabase("team-db-init");

        database.initialize().join();
        DatabaseEngine firstEngine = database.getEngine();
        database.initialize().join();

        assertThat(database.isInitialized()).isTrue();
        assertThat(database.getEngine()).isSameAs(firstEngine);
        assertThat(database.getSessionLocal()).isSameAs(firstEngine);
        assertThat(database.getTeam()).isInstanceOf(TeamDao.class);
        assertThat(database.getMember()).isInstanceOf(MemberDao.class);
        assertThat(database.getTask()).isInstanceOf(TaskDao.class);
        assertThat(database.getMessage()).isInstanceOf(MessageDao.class);
        assertThat(firstEngine.getTableNames()).contains("team_task_" + sessionSuffix("team-db-init"));
    }

    @Test
    void createAndDropCurrentSessionTablesDelegateWhenInitialized() throws Exception {
        AgentTeamsContext.setSessionId("team-db-tables");
        TeamDatabase database = newDatabase("team-db-tables");

        database.createCurSessionTables().join();
        assertThat(database.getEngine()).isNull();

        database.initialize().join();
        Set<String> afterInit = database.getEngine().getTableNames();
        assertThat(afterInit).contains("team_task_" + sessionSuffix("team-db-tables"));

        database.dropCurSessionTables().join();
        assertThat(database.getEngine().getTableNames()).doesNotContain("team_task_" + sessionSuffix("team-db-tables"));
    }

    @Test
    void cleanupAllRuntimeStateInitializesAndDelegatesToEngine() throws Exception {
        AgentTeamsContext.setSessionId("team-db-cleanup");
        TeamDatabase database = newDatabase("team-db-cleanup");
        database.initialize().join();
        database.getTeam().createTeam("team-a", "Team A", "leader", "desc", "prompt").join();

        TeamDatabase.RuntimeCleanupResult result = database.cleanupAllRuntimeState().join();

        assertThat(result.deletedTables()).contains("team_task_" + sessionSuffix("team-db-cleanup"));
        assertThat(result.clearedTables()).contains("team_info", "team_member");
        assertStaticRowCount(database.getEngine(), "team_info", 0);
    }

    @Test
    void forceDeleteTeamSessionDeletesTeamAndDropsCurrentSessionTables() throws Exception {
        AgentTeamsContext.setSessionId("team-db-force-delete");
        TeamDatabase database = newDatabase("team-db-force-delete");
        database.initialize().join();
        database.getTeam().createTeam("team-a", "Team A", "leader", "desc", "prompt").join();

        boolean deleted = database.forceDeleteTeamSession("team-a").join();

        assertThat(deleted).isTrue();
        assertThat(database.getTeam().getTeam("team-a").join()).isEmpty();
        assertThat(database.getEngine().getTableNames())
                .doesNotContain("team_task_" + sessionSuffix("team-db-force-delete"));
    }

    @Test
    void closeReleasesEngineAndDaos() throws Exception {
        AgentTeamsContext.setSessionId("team-db-close");
        TeamDatabase database = newDatabase("team-db-close");
        database.initialize().join();

        database.close().join();

        assertThat(database.isInitialized()).isFalse();
        assertThat(database.getEngine()).isNull();
        assertThat(database.getSessionLocal()).isNull();
        assertThat(database.getTeam()).isNull();
        assertThat(database.getMember()).isNull();
        assertThat(database.getTask()).isNull();
        assertThat(database.getMessage()).isNull();
    }

    private TeamDatabase newDatabase(String databaseName) throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1");
        DatabaseEngine engine = new DatabaseEngine(DatabaseConfig.builder().build(), connection);
        return new TeamDatabase(DatabaseConfig.builder().build(), engine);
    }

    private String sessionSuffix(String sessionId) {
        return DatabaseEngine.sanitizeSessionIdForTable(sessionId);
    }

    private void assertStaticRowCount(DatabaseEngine engine, String tableName, int count) throws Exception {
        try (Statement statement = engine.getConnection().createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            resultSet.next();
            assertThat(resultSet.getInt(1)).isEqualTo(count);
        }
    }
}
