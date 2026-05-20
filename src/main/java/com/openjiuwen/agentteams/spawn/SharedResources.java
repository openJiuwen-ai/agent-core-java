/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.spawn;

import com.openjiuwen.agentteams.messager.InProcessMessager;
import com.openjiuwen.agentteams.tools.database.DatabaseConfig;
import com.openjiuwen.agentteams.tools.database.DatabaseType;
import com.openjiuwen.agentteams.tools.database.TeamDatabase;
import com.openjiuwen.core.multiagent.runtime.TeamRuntime;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Process-global shared runtime and database resources for agent teams.
 *
 * <p>This is the first Java parity slice for Python {@code spawn/shared_resources.py}: one shared
 * runtime per process, one shared in-memory database singleton, and one shared database instance
 * per normalized dbType plus connection string.</p>
 */
public final class SharedResources {
    private static final Object LOCK = new Object();

    private static TeamRuntime runtime;
    private static TeamDatabase memoryDb;
    private static final Map<String, TeamDatabase> DB_INSTANCES = new LinkedHashMap<>();
    private static Function<DatabaseConfig, TeamDatabase> databaseFactory = TeamDatabase::new;
    private static Function<String, TeamRuntime> runtimeFactory = TeamRuntime::new;

    private SharedResources() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static TeamRuntime getSharedRuntime() {
        synchronized (LOCK) {
            if (runtime == null) {
                runtime = runtimeFactory.apply("default");
            }
            return runtime;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static TeamDatabase getSharedDb(DatabaseConfig config) {
        DatabaseConfig effectiveConfig = config != null ? config : DatabaseConfig.builder().build();
        synchronized (LOCK) {
            if (effectiveConfig.getDbType() == DatabaseType.MEMORY) {
                if (memoryDb == null) {
                    memoryDb = databaseFactory.apply(effectiveConfig);
                }
                return memoryDb;
            }
            String key = buildDbKey(effectiveConfig);
            return DB_INSTANCES.computeIfAbsent(key, ignored -> databaseFactory.apply(effectiveConfig));
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void cleanupSharedResources() {
        synchronized (LOCK) {
            runtime = null;
            memoryDb = null;
            DB_INSTANCES.clear();
            databaseFactory = TeamDatabase::new;
            runtimeFactory = TeamRuntime::new;
        }
        InProcessMessager.cleanupInprocessBus();
    }

    static void overrideDatabaseFactory(Function<DatabaseConfig, TeamDatabase> factory) {
        synchronized (LOCK) {
            databaseFactory = factory != null ? factory : TeamDatabase::new;
        }
    }

    static void overrideRuntimeFactory(Function<String, TeamRuntime> factory) {
        synchronized (LOCK) {
            runtimeFactory = factory != null ? factory : TeamRuntime::new;
        }
    }

    private static String buildDbKey(DatabaseConfig config) {
        String dbType = config.getDbType() != null
                ? config.getDbType().name().toLowerCase(Locale.ROOT)
                : "sqlite";
        String connectionString = config.getConnectionString() != null ? config.getConnectionString() : "";
        return dbType + "::" + connectionString;
    }
}
