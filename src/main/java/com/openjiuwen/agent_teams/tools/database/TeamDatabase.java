/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous team database manager.
 *
 * <p>Mirrors Python's {@code TeamDatabase} in
 * {@code openjiuwen/agent_teams/tools/database/__init__.py}.</p>
 */
public class TeamDatabase {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;

    private final DatabaseConfig config;
    private final Object initLock = new Object();
    private final DatabaseEngine injectedEngine;

    private DatabaseEngine engine;
    private DatabaseEngine sessionLocal;
    private boolean initialized;
    private TeamDao team;
    private MemberDao member;
    private TaskDao task;
    private MessageDao message;

    public TeamDatabase(DatabaseConfig config) {
        this(config, null);
    }

    TeamDatabase(DatabaseConfig config, DatabaseEngine injectedEngine) {
        this.config = config == null ? new DatabaseConfig() : config;
        this.injectedEngine = injectedEngine;
    }

    public DatabaseConfig getConfig() {
        return config;
    }

    public DatabaseEngine getEngine() {
        return engine;
    }

    public DatabaseEngine getSessionLocal() {
        return sessionLocal;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public TeamDao getTeam() {
        return team;
    }

    public MemberDao getMember() {
        return member;
    }

    public TaskDao getTask() {
        return task;
    }

    public MessageDao getMessage() {
        return message;
    }

    public static long getCurrentTime() {
        return DatabaseEngine.getCurrentTime();
    }

    public CompletableFuture<Void> initialize() {
        if (initialized) {
            return CompletableFuture.completedFuture(null);
        }
        synchronized (initLock) {
            if (initialized) {
                return CompletableFuture.completedFuture(null);
            }
            DatabaseEngine nextEngine = injectedEngine == null ? new DatabaseEngine(config) : injectedEngine;
            nextEngine.initialize().join();
            nextEngine.createCurrentSessionTables().join();

            this.engine = nextEngine;
            this.sessionLocal = nextEngine;
            this.team = new TeamDao(nextEngine);
            this.member = new MemberDao(nextEngine);
            this.task = new TaskDao(nextEngine);
            this.message = new MessageDao(nextEngine);
            this.initialized = true;
            TEAM_LOGGER.info("Team database initialized");
            return CompletableFuture.completedFuture(null);
        }
    }

    public CompletableFuture<Void> createCurSessionTables() {
        if (engine == null) {
            return CompletableFuture.completedFuture(null);
        }
        return engine.createCurrentSessionTables();
    }

    public CompletableFuture<Void> dropCurSessionTables() {
        if (engine == null) {
            return CompletableFuture.completedFuture(null);
        }
        return engine.dropCurrentSessionTables();
    }

    public CompletableFuture<RuntimeCleanupResult> cleanupAllRuntimeState() {
        return ensureInitialized().thenCompose(ignored -> {
            if (engine == null) {
                return CompletableFuture.completedFuture(new RuntimeCleanupResult(List.of(), List.of()));
            }
            return engine.cleanupAllRuntimeState()
                    .thenApply(result -> new RuntimeCleanupResult(result.deletedTables(), result.clearedTables()));
        });
    }

    public CompletableFuture<List<String>> dropSessionTablesById(String sessionId) {
        return ensureInitialized().thenCompose(ignored -> {
            if (engine == null) {
                return CompletableFuture.completedFuture(List.of());
            }
            return engine.dropSessionTablesById(sessionId);
        });
    }

    public CompletableFuture<Boolean> forceDeleteTeamSession(String teamName) {
        String sessionId = AgentTeamsContext.getSessionId();
        return ensureInitialized()
                .thenCompose(ignored -> team.deleteTeam(teamName))
                .thenCompose(ignored -> dropSessionTablesForContext(sessionId).thenApply(unused -> true))
                .exceptionally(exception -> {
                    TEAM_LOGGER.error(
                            "Failed to drop current session tables for team {}: {}",
                            teamName,
                            exception.getMessage()
                    );
                    return false;
                })
                .thenApply(success -> {
                    if (success) {
                        TEAM_LOGGER.info("Force deleted team session data for {}", teamName);
                    }
                    return success;
        });
    }

    public CompletableFuture<Void> close() {
        if (engine == null) {
            return CompletableFuture.completedFuture(null);
        }
        engine.close();
        engine = null;
        sessionLocal = null;
        initialized = false;
        team = null;
        member = null;
        task = null;
        message = null;
        TEAM_LOGGER.info("Team database closed");
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> ensureInitialized() {
        if (initialized) {
            return CompletableFuture.completedFuture(null);
        }
        return initialize();
    }

    private CompletableFuture<Void> dropSessionTablesForContext(String sessionId) {
        if (engine == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (sessionId == null || sessionId.isEmpty()) {
            return engine.dropCurrentSessionTables();
        }
        return engine.dropSessionTablesById(sessionId).thenApply(ignored -> null);
    }

    /**
     * Java representation of Python's {@code tuple[list[str], list[str]]} cleanup result.
     *
     * <p>Mirrors Python's {@code TeamDatabase.cleanup_all_runtime_state} return value in
     * {@code openjiuwen/agent_teams/tools/database/__init__.py}.</p>
     */
    public record RuntimeCleanupResult(List<String> deletedTables, List<String> clearedTables) {
        public RuntimeCleanupResult {
            deletedTables = deletedTables == null ? List.of() : List.copyOf(deletedTables);
            clearedTables = clearedTables == null ? List.of() : List.copyOf(clearedTables);
        }
    }
}
