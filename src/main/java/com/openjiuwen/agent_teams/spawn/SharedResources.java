/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.spawn;

import com.openjiuwen.agent_teams.messager.InProcessMessager;
import com.openjiuwen.agent_teams.tools.TeamDatabase;
import com.openjiuwen.agent_teams.tools.database.DatabaseConfig;
import com.openjiuwen.agent_teams.tools.database.DatabaseType;
import com.openjiuwen.core.multiagent.teamruntime.TeamRuntime;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-global shared resources for agent teams.
 * <p>
 * Database and TeamRuntime are process-global singletons — they are
 * NOT partitioned by team_name or session_id. Multiple teams and sessions
 * coexist inside the same instance; data isolation is handled internally
 * via team_name / session_id fields.
 * <p>
 * Mirrors Python's {@code shared_resources.py} in
 * {@code openjiuwen.agent_teams.spawn.shared_resources}.
 */
public final class SharedResources {
    
    /** Process-global TeamRuntime singleton */
    private static TeamRuntime runtime = null;
    
    /** Process-global in-memory TeamDatabase singleton (for ":memory:" mode) */
    private static TeamDatabase memoryDb = null;
    
    /** Database instances keyed by normalized db_type + connection_string */
    private static final Map<String, TeamDatabase> dbInstances = new ConcurrentHashMap<>();
    
    private SharedResources() {
        // Utility class
    }
    
    /**
     * Return the process-global TeamRuntime, creating it on first call.
     * <p>
     * Mirrors Python: get_shared_runtime()
     *
     * @return TeamRuntime instance
     */
    public static TeamRuntime getSharedRuntime() {
        if (runtime == null) {
            runtime = createRuntime();
        }
        return runtime;
    }
    
    /**
     * Return a process-global database instance matching config.
     * <p>
     * - db_type == "memory" or connection_string == ":memory:" → single global in-memory TeamDatabase.
     * - otherwise → one TeamDatabase per db_type+connection_string.
     * <p>
     * Mirrors Python: get_shared_db(config)
     *
     * @param config Database configuration (DatabaseConfig)
     * @return TeamDatabase instance
     */
    public static TeamDatabase getSharedDb(DatabaseConfig config) {
        if (config == null) {
            return getSharedMemoryDb();
        }
        
        String connectionString = config.getConnectionString();
        // Check for in-memory mode
        if (":memory:".equals(connectionString) || 
            (config.getDbType() == DatabaseType.SQLITE && connectionString.isEmpty())) {
            return getSharedMemoryDb();
        }
        
        return getSharedDbInstance(config);
    }
    
    /**
     * Legacy method accepting Object config for backward compatibility.
     *
     * @param config Database configuration (can be DatabaseConfig or Object)
     * @return TeamDatabase instance
     */
    public static Object getSharedDb(Object config) {
        if (config instanceof DatabaseConfig) {
            return getSharedDb((DatabaseConfig) config);
        }
        // Fallback for unknown config types
        return getSharedMemoryDb();
    }
    
    /**
     * Reset all process-global singletons (e.g. between test runs).
     * <p>
     * Mirrors Python: cleanup_shared_resources()
     */
    public static void cleanupSharedResources() {
        runtime = null;
        memoryDb = null;
        dbInstances.clear();
        
        // Cleanup inprocess bus
        cleanupInprocessBus();
    }
    
    // -- Internal methods --
    
    private static TeamDatabase getSharedMemoryDb() {
        if (memoryDb == null) {
            memoryDb = createMemoryDb();
        }
        return memoryDb;
    }
    
    private static TeamDatabase getSharedDbInstance(DatabaseConfig config) {
        String key = buildDbKey(config);
        return dbInstances.computeIfAbsent(key, k -> createDb(config));
    }
    
    private static String buildDbKey(DatabaseConfig config) {
        // Build key from db_type + connection_string, matching Python logic:
        // key = f"{db_type}::{conn_str}"
        String dbType = config.getDbType() != null ? config.getDbType().getValue() : "sqlite";
        String connStr = config.getConnectionString() != null ? config.getConnectionString() : "";
        return dbType + "::" + connStr;
    }
    
    private static String buildDbKey(Object config) {
        if (config instanceof DatabaseConfig) {
            return buildDbKey((DatabaseConfig) config);
        }
        return config.toString();
    }
    
    private static TeamRuntime createRuntime() {
        // Create TeamRuntime with default config
        return new TeamRuntime();
    }
    
    private static TeamDatabase createMemoryDb() {
        // Create in-memory TeamDatabase (using ":memory:" connection string)
        return new TeamDatabase(DatabaseConfig.inMemory());
    }
    
    private static TeamDatabase createDb(DatabaseConfig config) {
        // Create TeamDatabase with specified config
        return new TeamDatabase(config);
    }
    
    private static TeamDatabase createDb(Object config) {
        if (config instanceof DatabaseConfig) {
            return createDb((DatabaseConfig) config);
        }
        return createMemoryDb();
    }
    
    private static void cleanupInprocessBus() {
        // Cleanup inprocess message bus by calling InProcessMessager.cleanupBus()
        InProcessMessager.cleanupBus();
    }
}