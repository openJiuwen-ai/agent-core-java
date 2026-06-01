/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.agent_teams.schema.task.GraphMutationResult;
import com.openjiuwen.agent_teams.schema.task.NewTaskSpec;
import com.openjiuwen.agent_teams.spawn.SpawnContext;
import com.openjiuwen.agent_teams.tools.database.DatabaseConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link TeamDatabase}.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent_teams/test_database.py}
 * coverage for database lifecycle and DAO wiring.</p>
 */
class TeamDatabaseTest {

    @AfterEach
    void resetSessionContext() {
        SpawnContext.resetSessionId();
    }

    @Test
    void initializeWiresDaosAndIsIdempotent() {
        SpawnContext.setSessionId("session-init");
        TeamDatabase database = new TeamDatabase(DatabaseConfig.inMemory());

        assertFalse(database.isInitialized());
        assertNull(database.getEngine());
        assertNull(database.getSessionLocal());

        database.initialize();

        Object firstEngine = database.getEngine();
        Object firstSessionLocal = database.getSessionLocal();
        Object firstTeamDao = database.getTeamDao();
        assertTrue(database.isInitialized());
        assertNotNull(firstEngine);
        assertNotNull(firstSessionLocal);
        assertNotNull(database.getTeamDao());
        assertNotNull(database.getMemberDao());
        assertNotNull(database.getTaskDao());
        assertNotNull(database.getMessageDao());

        database.initialize();

        assertSame(firstEngine, database.getEngine());
        assertSame(firstSessionLocal, database.getSessionLocal());
        assertSame(firstTeamDao, database.getTeamDao());

        database.close();

        assertFalse(database.isInitialized());
        assertNull(database.getEngine());
        assertNull(database.getSessionLocal());
        assertNull(database.getTeamDao());
        assertNull(database.getMemberDao());
        assertNull(database.getTaskDao());
        assertNull(database.getMessageDao());
    }

    @Test
    void cleanupAndDropSessionTablesMirrorPythonReturnValues() {
        SpawnContext.setSessionId("session-cleanup");
        TeamDatabase database = new TeamDatabase(DatabaseConfig.inMemory());
        database.initialize();

        assertTrue(database.getTeamDao().createTeam("team-clean", "Team Clean", "leader", null, null).join());
        assertTrue(database.getMemberDao()
                .createMember("leader", "team-clean", "Leader", "{}", "ready")
                .join());
        assertTrue(database.getTaskDao()
                .createTask("task-clean", "team-clean", "Task", "Content", "pending")
                .join());

        TeamDatabase.RuntimeCleanupResult cleanup = database.cleanupAllRuntimeState();

        assertTrue(cleanup.deletedTables().stream().anyMatch(name -> name.startsWith("team_task_")));
        assertTrue(cleanup.deletedTables().stream().anyMatch(name -> name.startsWith("team_message_")));
        assertTrue(cleanup.clearedTables().contains("team_info"));
        assertTrue(cleanup.clearedTables().contains("team_member"));
        assertTrue(database.getTeamDao().getTeam("team-clean").join().isEmpty());

        SpawnContext.setSessionId("session-drop");
        database.createCurSessionTables();
        List<String> dropped = database.dropSessionTablesById("session-drop");

        assertEquals(4, dropped.size());
        assertTrue(dropped.stream().anyMatch(name -> name.startsWith("team_task_")));
        assertTrue(dropped.stream().anyMatch(name -> name.startsWith("team_task_dependency_")));
        assertTrue(dropped.stream().anyMatch(name -> name.startsWith("team_message_")));
        assertTrue(dropped.stream().anyMatch(name -> name.startsWith("message_read_status_")));

        database.close();
    }

    @Test
    void forceDeleteTeamSessionDeletesTeamAndDropsCurrentSessionTables() {
        SpawnContext.setSessionId("session-force-delete");
        TeamDatabase database = new TeamDatabase(DatabaseConfig.inMemory());
        database.initialize();

        assertTrue(database.getTeamDao().createTeam("team-force", "Team Force", "leader", null, null).join());
        assertTrue(database.getTaskDao()
                .createTask("task-force", "team-force", "Task", "Content", "pending")
                .join());

        assertTrue(database.forceDeleteTeamSession("team-force"));
        assertTrue(database.getTeamDao().getTeam("team-force").join().isEmpty());
        assertThrows(CompletionException.class, () -> database.getTaskDao().getTask("task-force").join());

        database.close();
    }

    @Test
    void daoOperationsPreservePythonVisibleSemantics() {
        SpawnContext.setSessionId("session-daos");
        TeamDatabase database = new TeamDatabase(DatabaseConfig.inMemory());
        database.initialize();

        assertTrue(database.getTeamDao().createTeam("team", "Team", "leader", "desc", "prompt").join());
        assertFalse(database.getTeamDao().createTeam("team", "Team", "leader", null, null).join());
        assertTrue(database.getMemberDao()
                .createMember("leader", "team", "Leader", "{}", "ready")
                .join());
        assertFalse(database.getMemberDao()
                .createMember("leader", "team", "Leader", "{}", "ready")
                .join());
        assertEquals(1, database.getMemberDao().getTeamMembers("team", null).join().size());
        assertTrue(database.getMemberDao().getMembersMaxUpdatedAt("team").join() > 0);

        assertTrue(database.getTaskDao().createTask("task-a", "team", "A", "Alpha", "pending").join());
        assertTrue(database.getTaskDao().claimTask("task-a", "leader").join());
        assertFalse(database.getTaskDao().claimTask("task-a", "leader").join());
        assertTrue(database.getTaskDao().resetTask("task-a").join().isPresent());
        assertTrue(database.getTaskDao().updateTask("task-a", "A2", null).join());
        assertEquals("A2", database.getTaskDao().getTask("task-a").join().orElseThrow().getTitle());

        GraphMutationResult mutation = database.getTaskDao().mutateDependencyGraph(
                "team",
                List.of(new NewTaskSpec("task-b", "B", "Beta", "pending")),
                List.of(new TaskDatabaseEdge("task-b", "task-a")));
        assertTrue(mutation.ok());
        assertEquals("blocked", database.getTaskDao().getTask("task-b").join().orElseThrow().getStatus());

        assertTrue(database.getMessageDao()
                .createMessage("message-direct", "team", "leader", "hello", "leader", false, false)
                .join());
        assertEquals(1, database.getMessageDao().getMessages("team", "leader", true, null).join().size());
        assertFalse(database.getMessageDao().markMessageRead("message-direct", "missing").join());
        assertTrue(database.getMessageDao().markMessageRead("message-direct", "leader").join());
        assertEquals(0, database.getMessageDao().getMessages("team", "leader", true, null).join().size());

        assertTrue(database.getMessageDao()
                .createMessage("message-broadcast", "team", "leader", "broadcast", null, true, false)
                .join());
        assertEquals(0, database.getMessageDao().getBroadcastMessages("team", "leader", true, null).join().size());

        Optional<Team> team = database.getTeamDao().getTeam("team").join();
        assertTrue(team.isPresent());
        assertTrue(database.getTeamDao().deleteTeam("team").join());
        assertTrue(database.getTeamDao().getTeam("team").join().isEmpty());

        database.close();
    }
}
