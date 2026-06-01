/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.agent_teams.tools.database.DatabaseConfig;
import com.openjiuwen.agent_teams.tools.database.DatabaseEngine;
import com.openjiuwen.agent_teams.tools.database.MemberDao;
import com.openjiuwen.agent_teams.tools.database.MessageDao;
import com.openjiuwen.agent_teams.tools.database.TaskDao;
import com.openjiuwen.agent_teams.tools.database.TeamDao;
import com.openjiuwen.agent_teams.tools.database.TeamDatabaseState;

import java.util.List;
import java.util.logging.Logger;

/**
 * Team database manager.
 *
 * <p>Mirrors Python's {@code TeamDatabase} in
 * {@code openjiuwen.agent_teams.tools.database}.</p>
 *
 * <p>Owns the engine lifecycle and cross-table transactions. Single-table
 * operations live on the DAO attributes ({@code team} / {@code member} /
 * {@code task} / {@code message}) and are exposed through DAO getters.</p>
 */
public class TeamDatabase {

    private static final Logger teamLogger = Logger.getLogger(TeamDatabase.class.getName());

    private final DatabaseConfig config;
    private final Object initLock = new Object();
    private final TeamDatabaseState state;
    private DatabaseEngine engine;
    private Object sessionLocal;
    private boolean initialized;
    private TeamDao team;
    private MemberDao member;
    private TaskDao task;
    private MessageDao message;

    /**
     * Initialize database manager with config.
     *
     * @param config database configuration
     */
    public TeamDatabase(DatabaseConfig config) {
        this.config = config != null ? config : DatabaseConfig.inMemory();
        this.state = new TeamDatabaseState(this.config);
    }

    public DatabaseConfig getConfig() {
        return config;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public DatabaseEngine getEngine() {
        return engine;
    }

    public Object getSessionLocal() {
        return sessionLocal;
    }

    public TeamDao getTeamDao() {
        return team;
    }

    public MemberDao getMemberDao() {
        return member;
    }

    public TaskDao getTaskDao() {
        return task;
    }

    public MessageDao getMessageDao() {
        return message;
    }

    /**
     * Initialize engine ownership, create current-session tables, and wire DAOs.
     */
    public void initialize() {
        if (initialized) {
            return;
        }
        synchronized (initLock) {
            if (initialized) {
                return;
            }
            this.engine = new DatabaseEngine(config);
            this.sessionLocal = state;
            state.createCurrentSessionTables();
            this.team = new TeamDao(state);
            this.member = new MemberDao(state);
            this.task = new TaskDao(state);
            this.message = new MessageDao(state);
            this.initialized = true;
            teamLogger.info("Team database initialized");
        }
    }

    /**
     * Create dynamic tables for the current session.
     */
    public void createCurSessionTables() {
        if (engine == null) {
            return;
        }
        state.createCurrentSessionTables();
    }

    /**
     * Drop dynamic tables for the current session.
     */
    public void dropCurSessionTables() {
        if (engine == null) {
            return;
        }
        state.dropCurrentSessionTables();
    }

    /**
     * Delete all dynamic team tables and clear static team tables.
     *
     * @return deleted dynamic table names and cleared static table names
     */
    public RuntimeCleanupResult cleanupAllRuntimeState() {
        ensureInitialized();
        if (engine == null) {
            return new RuntimeCleanupResult(List.of(), List.of());
        }
        TeamDatabaseState.RuntimeStateCleanup cleanup = state.cleanupAllRuntimeState();
        return new RuntimeCleanupResult(cleanup.deletedTables(), cleanup.clearedTables());
    }

    /**
     * Drop dynamic tables for a specific session without active context.
     *
     * @param sessionId session identifier
     * @return dropped table names
     */
    public List<String> dropSessionTablesById(String sessionId) {
        ensureInitialized();
        if (engine == null) {
            return List.of();
        }
        return state.dropSessionTablesById(sessionId);
    }

    /**
     * Delete a team's row and drop current session tables.
     *
     * @param teamName team name
     * @return true if current session table drop succeeds
     */
    public boolean forceDeleteTeamSession(String teamName) {
        ensureInitialized();
        team.deleteTeam(teamName).join();
        try {
            state.dropCurrentSessionTables();
        } catch (Exception e) {
            teamLogger.severe(String.format(
                    "Failed to drop current session tables for team %s: %s", teamName, e.getMessage()));
            return false;
        }
        teamLogger.info(String.format("Force deleted team session data for %s", teamName));
        return true;
    }

    /**
     * Close the database engine and release all connections.
     */
    public void close() {
        if (engine == null) {
            return;
        }
        engine.close();
        state.close();
        engine = null;
        sessionLocal = null;
        initialized = false;
        team = null;
        member = null;
        task = null;
        message = null;
        teamLogger.info("Team database closed");
    }

    public static long getCurrentTime() {
        return DatabaseEngine.getCurrentTime();
    }

    private void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }

    /**
     * Java representation of Python's {@code tuple[list[str], list[str]]}.
     *
     * @param deletedTables dropped dynamic table names
     * @param clearedTables cleared static table names
     */
    public record RuntimeCleanupResult(List<String> deletedTables, List<String> clearedTables) {
        public RuntimeCleanupResult {
            deletedTables = deletedTables != null ? List.copyOf(deletedTables) : List.of();
            clearedTables = clearedTables != null ? List.copyOf(clearedTables) : List.of();
        }
    }
}
