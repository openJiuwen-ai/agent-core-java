/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.spawn;

import com.openjiuwen.agent_teams.messager.InProcessMessager;
import com.openjiuwen.agent_teams.runtime.TeamRuntimeManager;
import com.openjiuwen.agent_teams.tools.database.DatabaseConfig;
import com.openjiuwen.agent_teams.tools.database.DatabaseEngine;
import com.openjiuwen.agent_teams.tools.database.DatabaseType;
import com.openjiuwen.agent_teams.tools.database.MemberDao;
import com.openjiuwen.agent_teams.tools.database.MessageDao;
import com.openjiuwen.agent_teams.tools.database.TaskDao;
import com.openjiuwen.agent_teams.tools.database.TeamDao;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-global shared resources for agent teams.
 *
 * <p>Mirrors Python's module {@code shared_resources} in
 * {@code openjiuwen/agent_teams/spawn/shared_resources.py}.</p>
 */
public final class SharedResources {

    private static TeamRuntimeManager runtime;
    private static volatile SharedDatabase memoryDb;
    private static final Map<String, SharedDatabase> DB_INSTANCES = new ConcurrentHashMap<>();
    private static DatabaseFactory databaseFactory = SharedTeamDatabase::new;

    private SharedResources() {
    }

    public static synchronized TeamRuntimeManager getSharedRuntime() {
        if (runtime == null) {
            runtime = new TeamRuntimeManager();
        }
        return runtime;
    }

    public static synchronized SharedDatabase getSharedDb(DatabaseConfig config) {
        Objects.requireNonNull(config, "config");
        return getSharedDb(new SharedDbConfig(
                config.getDbType() == null ? null : config.getDbType().value(),
                config.getConnectionString()
        ));
    }

    public static SharedDatabase getSharedDb(SharedDbConfig config) {
        SharedDbConfig effectiveConfig = Objects.requireNonNull(config, "config");
        if ("memory".equals(effectiveConfig.dbType())) {
            return getSharedMemoryDb();
        }
        return getSharedDbInstance(effectiveConfig);
    }

    public static synchronized void cleanupSharedResources() {
        runtime = null;
        memoryDb = null;
        DB_INSTANCES.clear();
        InProcessMessager.cleanupInprocessBus();
    }

    static synchronized void setDatabaseFactoryForTests(DatabaseFactory factory) {
        databaseFactory = factory == null ? SharedTeamDatabase::new : factory;
    }

    static synchronized void resetDatabaseFactoryForTests() {
        databaseFactory = SharedTeamDatabase::new;
    }

    static synchronized int databaseInstanceCountForTests() {
        return DB_INSTANCES.size();
    }

    private static SharedDatabase getSharedMemoryDb() {
        SharedDatabase cached = memoryDb;
        if (cached != null) {
            return cached;
        }
        SharedDatabase created = databaseFactory.create(SharedDbConfig.memory());
        synchronized (SharedResources.class) {
            if (memoryDb == null) {
                memoryDb = created;
                return created;
            }
            return memoryDb;
        }
    }

    private static SharedDatabase getSharedDbInstance(SharedDbConfig config) {
        String key = config.dbType() + "::" + config.connectionString();
        SharedDatabase cached = DB_INSTANCES.get(key);
        if (cached != null) {
            return cached;
        }
        SharedDatabase created = databaseFactory.create(config);
        SharedDatabase existing = DB_INSTANCES.putIfAbsent(key, created);
        return existing != null ? existing : created;
    }

    /**
     * Minimal shared-database surface returned by {@link SharedResources}.
     *
     * <p>Mirrors Python's {@code TeamDatabase | InMemoryTeamDatabase} return boundary in
     * {@code openjiuwen/agent_teams/spawn/shared_resources.py}.</p>
     */
    public interface SharedDatabase {
        SharedDbConfig config();
    }

    /**
     * Database config boundary used by the shared-resource cache.
     *
     * <p>Mirrors Python's dynamic {@code config.db_type} and
     * {@code config.connection_string} access in
     * {@code openjiuwen/agent_teams/spawn/shared_resources.py}.</p>
     */
    public record SharedDbConfig(String dbType, String connectionString) {
        public SharedDbConfig {
            dbType = dbType == null ? "" : dbType;
            connectionString = connectionString == null ? "" : connectionString;
        }

        public static SharedDbConfig memory() {
            return new SharedDbConfig("memory", "");
        }
    }

    /**
     * Current Java facade for the translated database engine and available DAOs.
     *
     * <p>Mirrors Python's {@code TeamDatabase} process-global instance in
     * {@code openjiuwen/agent_teams/spawn/shared_resources.py}.</p>
     */
    public static class SharedTeamDatabase implements SharedDatabase {
        private final SharedDbConfig config;
        private final DatabaseEngine engine;
        private final TeamDao team;
        private final MemberDao member;
        private final TaskDao task;
        private final MessageDao message;

        public SharedTeamDatabase(SharedDbConfig config) {
            this.config = Objects.requireNonNull(config, "config");
            this.engine = new DatabaseEngine(toDatabaseConfig(config));
            this.team = new TeamDao(engine);
            this.member = new MemberDao(engine);
            this.task = new TaskDao(engine);
            this.message = new MessageDao(engine);
        }

        @Override
        public SharedDbConfig config() {
            return config;
        }

        public DatabaseEngine engine() {
            return engine;
        }

        public TeamDao team() {
            return team;
        }

        public MemberDao member() {
            return member;
        }

        public TaskDao task() {
            return task;
        }

        public MessageDao message() {
            return message;
        }

        private static DatabaseConfig toDatabaseConfig(SharedDbConfig config) {
            DatabaseConfig databaseConfig = new DatabaseConfig();
            if ("memory".equals(config.dbType())) {
                databaseConfig.setDbType(DatabaseType.SQLITE);
                databaseConfig.setConnectionString(":memory:");
                return databaseConfig;
            }
            databaseConfig.setDbType(DatabaseType.fromValue(config.dbType()));
            databaseConfig.setConnectionString(config.connectionString());
            return databaseConfig;
        }
    }

    /**
     * Factory hook used only to avoid physical database creation in focused tests.
     *
     * <p>Mirrors Python's lazy constructor calls for database singletons in
     * {@code openjiuwen/agent_teams/spawn/shared_resources.py}.</p>
     */
    @FunctionalInterface
    interface DatabaseFactory {
        SharedDatabase create(SharedDbConfig config);
    }
}
