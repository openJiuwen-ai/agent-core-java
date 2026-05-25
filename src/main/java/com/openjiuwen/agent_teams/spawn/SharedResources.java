/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.spawn;

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
    private static Object runtime = null;
    
    /** Process-global InMemoryTeamDatabase singleton */
    private static Object memoryDb = null;
    
    /** Database instances keyed by normalized db_type + connection_string */
    private static final Map<String, Object> dbInstances = new ConcurrentHashMap<>();
    
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
    public static Object getSharedRuntime() {
        if (runtime == null) {
            // Create TeamRuntime on first call
            // Placeholder - actual implementation would create TeamRuntime
            runtime = createRuntime();
        }
        return runtime;
    }
    
    /**
     * Return a process-global database instance matching config.
     * <p>
     * - db_type == "memory" → single global InMemoryTeamDatabase.
     * - db_type != "memory" → one TeamDatabase per db_type+connection_string.
     * <p>
     * Mirrors Python: get_shared_db(config)
     *
     * @param config Database configuration
     * @return Database instance
     */
    public static Object getSharedDb(Object config) {
        // Placeholder - actual implementation would check config.db_type
        String dbType = extractDbType(config);
        
        if ("memory".equals(dbType)) {
            return getSharedMemoryDb();
        }
        
        return getSharedDbInstance(config);
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
    
    private static Object getSharedMemoryDb() {
        if (memoryDb == null) {
            memoryDb = createMemoryDb();
        }
        return memoryDb;
    }
    
    private static Object getSharedDbInstance(Object config) {
        String key = buildDbKey(config);
        return dbInstances.computeIfAbsent(key, k -> createDb(config));
    }
    
    private static String buildDbKey(Object config) {
        // Placeholder - build key from db_type + connection_string
        return config.toString();
    }
    
    private static String extractDbType(Object config) {
        // Placeholder - extract db_type from config
        return "memory";
    }
    
    private static Object createRuntime() {
        // Placeholder - create TeamRuntime
        return null;
    }
    
    private static Object createMemoryDb() {
        // Placeholder - create InMemoryTeamDatabase
        return null;
    }
    
    private static Object createDb(Object config) {
        // Placeholder - create TeamDatabase
        return null;
    }
    
    private static void cleanupInprocessBus() {
        // Placeholder - cleanup inprocess message bus
    }
}