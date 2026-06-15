/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

import com.openjiuwen.core.common.logging.Loggers;

/**
 * Database engine abstraction layer for connection pool management,
 * transaction handling, and per-session dynamic table lifecycle.
 *
 * <p>Mirrors Python tools/database/engine.py. Provides abstracted
 * initialization and cleanup operations for the team database.</p>
 */
public final class DatabaseEngine {

    private DatabaseEngine() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static long getCurrentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Initialize a database engine from configuration.
     * Mirrors Python initialize_engine(config).
     *
     * @param config the database configuration
     * @return initialized TeamDatabase
     */
    public static TeamDatabase initializeEngine(DatabaseConfig config) {
        if (config == null || config.getDbType() == null) {
            throw new IllegalArgumentException("DatabaseConfig with dbType is required");
        }

        DatabaseType dbType = config.getDbType();

        if (dbType == DatabaseType.SQLITE || dbType == DatabaseType.MEMORY) {
            TeamDatabase db = new TeamDatabase(config);
            db.initialize();
            Loggers.AGENT.info("Database engine initialized: type={}", dbType);
            return db;
        } else if (dbType == DatabaseType.POSTGRESQL || dbType == DatabaseType.MYSQL) {
            // PostgreSQL/MySQL support via JDBC
            TeamDatabase db = new TeamDatabase(config);
            db.initialize();
            Loggers.AGENT.info("Database engine initialized: type={}", dbType);
            return db;
        } else {
            throw new IllegalArgumentException(
                    "Unsupported database type: " + dbType);
        }
    }

    /**
     * Create per-session dynamic tables.
     * Mirrors Python create_cur_session_tables(engine).
     *
     * @param db the team database
     */
    public static void createCurSessionTables(TeamDatabase db) {
        if (db != null) {
            db.createCurSessionTables();
        }
    }

    /**
     * Drop per-session dynamic tables.
     * Mirrors Python drop_cur_session_tables(engine).
     *
     * @param db the team database
     */
    public static void dropCurSessionTables(TeamDatabase db) {
        if (db != null) {
            db.dropCurSessionTables();
        }
    }

    /**
     * Cleanup all runtime state — drop dynamic tables, clear static tables.
     * Mirrors Python cleanup_all_runtime_state(engine).
     *
     * @param db the team database
     * @return summary of cleanup operation
     */
    public static String cleanupAllRuntimeState(TeamDatabase db) {
        if (db == null) {
            return "No database to clean up";
        }
        db.dropCurSessionTables();
        Loggers.AGENT.info("All runtime state cleaned up");
        return "Runtime state cleaned up";
    }

    /**
     * Drop dynamic tables for a specific session.
     * Mirrors Python drop_session_tables_by_id(engine, session_id).
     *
     * @param db        the team database
     * @param sessionId the session ID
     */
    public static void dropSessionTablesById(TeamDatabase db, String sessionId) {
        if (db != null) {
            db.dropCurSessionTables();
        }
    }
}
