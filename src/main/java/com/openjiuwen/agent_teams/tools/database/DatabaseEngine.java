/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import org.bouncycastle.crypto.digests.Blake2sDigest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Database engine initialization, session management, and table lifecycle.
 * <p>
 * Mirrors Python's module in
 * {@code openjiuwen/agent_teams/tools/database/engine.py}.
 */
public class DatabaseEngine {

    static final String[] TEAM_DYNAMIC_TABLE_PREFIXES = {
            "team_task_dependency_",
            "team_task_",
            "team_message_",
            "message_read_status_"
    };

    static final String[] TEAM_STATIC_TABLES_TO_CLEAR = {
            "team_info",
            "team_member"
    };

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;

    private final DatabaseConfig config;
    private Connection connection;
    private boolean initialized;

    public DatabaseEngine(DatabaseConfig config) {
        this(config, null);
    }

    DatabaseEngine(DatabaseConfig config, Connection connection) {
        this.config = config;
        this.connection = connection;
        this.initialized = connection != null;
    }

    public CompletableFuture<DatabaseEngine> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (connection == null) {
                    switch (config.getDbType()) {
                        case SQLITE -> initSqlite();
                        case POSTGRESQL -> initPostgreSql();
                        case MYSQL -> initMySql();
                        default -> throw new UnsupportedOperationException(
                                "Unsupported database type: " + config.getDbType()
                        );
                    }
                }
                initialized = true;
                createStaticTables();
                ensureTeamMemberRoleColumn();
                return this;
            } catch (Exception exception) {
                throw new RuntimeException("Database initialization failed", exception);
            }
        });
    }

    public Connection getConnection() {
        return connection;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            TEAM_LOGGER.warning("Failed to close database: %s", exception.getMessage());
        } finally {
            connection = null;
            initialized = false;
        }
    }

    public CompletableFuture<Void> createCurrentSessionTables() {
        String sessionId = AgentTeamsContext.getSessionId();
        return CompletableFuture.runAsync(() -> {
            if (connection == null) {
                return;
            }
            if (sessionId == null || sessionId.isEmpty()) {
                TEAM_LOGGER.warning("No session_id in context, cannot create session tables");
                return;
            }

            String suffix = sanitizeSessionIdForTable(sessionId);
            try (Statement statement = connection.createStatement()) {
                statement.execute(dynamicTaskSql(suffix));
                statement.execute(dynamicTaskDependencySql(suffix));
                statement.execute(dynamicMessageSql(suffix));
                statement.execute(dynamicMessageReadStatusSql(suffix));
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to create current session tables", exception);
            }

            TEAM_LOGGER.info("Session tables ready for session %s", sessionId);
        });
    }

    public CompletableFuture<Void> dropCurrentSessionTables() {
        String sessionId = AgentTeamsContext.getSessionId();
        return CompletableFuture.runAsync(() -> {
            if (connection == null) {
                return;
            }
            if (sessionId == null || sessionId.isEmpty()) {
                TEAM_LOGGER.warning("No session_id in context, cannot drop session tables");
                return;
            }

            String suffix = sanitizeSessionIdForTable(sessionId);
            try {
                dropTable("team_task_" + suffix);
                dropTable("team_task_dependency_" + suffix);
                dropTable("team_message_" + suffix);
                dropTable("message_read_status_" + suffix);
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to drop current session tables", exception);
            }

            TEAM_LOGGER.info("Dropped dynamic tables for session %s", sessionId);
        });
    }

    public CompletableFuture<CleanupResult> cleanupAllRuntimeState() {
        return CompletableFuture.supplyAsync(() -> {
            if (connection == null) {
                return new CleanupResult(List.of(), List.of());
            }

            List<String> deletedTables = new ArrayList<>();
            List<String> clearedTables = new ArrayList<>();
            try {
                Set<String> tableNames = getTableNames();
                for (String tableName : tableNames) {
                    if (!startsWithDynamicPrefix(tableName)) {
                        continue;
                    }
                    dropTable(tableName);
                    deletedTables.add(tableName);
                }

                for (String tableName : TEAM_STATIC_TABLES_TO_CLEAR) {
                    if (!tableNames.contains(tableName)) {
                        continue;
                    }
                    clearTable(tableName);
                    clearedTables.add(tableName);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to cleanup runtime state", exception);
            }

            TEAM_LOGGER.info(
                    "Cleaned team runtime state: deleted dynamic tables=%s, cleared static tables=%s",
                    deletedTables,
                    clearedTables
            );
            return new CleanupResult(deletedTables, clearedTables);
        });
    }

    public CompletableFuture<List<String>> dropSessionTablesById(String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            if (connection == null || sessionId == null || sessionId.isEmpty()) {
                return List.of();
            }

            String suffix = sanitizeSessionIdForTable(sessionId);
            List<String> droppedTables = new ArrayList<>();
            try {
                Set<String> tableNames = getTableNames();
                for (String prefix : TEAM_DYNAMIC_TABLE_PREFIXES) {
                    String expectedTable = prefix + suffix;
                    if (tableNames.contains(expectedTable)) {
                        dropTable(expectedTable);
                        droppedTables.add(expectedTable);
                    }
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to drop session tables", exception);
            }

            if (!droppedTables.isEmpty()) {
                TEAM_LOGGER.info("Dropped session tables for session %s: %s", sessionId, droppedTables);
            }
            return droppedTables;
        });
    }

    public void dropTable(String tableName) throws SQLException {
        String quotedName = tableName.replace("\"", "\"\"");
        try (Statement statement = connection.createStatement()) {
            try {
                statement.execute("DROP TABLE IF EXISTS \"" + quotedName + "\"");
            } catch (SQLException ignored) {
                statement.execute("DROP TABLE IF EXISTS " + tableName);
            }
        }
    }

    public void clearTable(String tableName) throws SQLException {
        String quotedName = tableName.replace("\"", "\"\"");
        try (Statement statement = connection.createStatement()) {
            try {
                statement.execute("DELETE FROM \"" + quotedName + "\"");
            } catch (SQLException ignored) {
                statement.execute("DELETE FROM " + tableName);
            }
        }
    }

    public Set<String> getTableNames() throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        Set<String> tableNames = new LinkedHashSet<>();
        try (ResultSet resultSet = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                tableNames.add(resultSet.getString("TABLE_NAME").toLowerCase());
            }
        }
        return tableNames;
    }

    static long getCurrentTime() {
        return Math.round(System.currentTimeMillis());
    }

    static String sanitizeSessionIdForTable(String sessionId) {
        Blake2sDigest digest = new Blake2sDigest(64);
        byte[] source = sessionId.getBytes(StandardCharsets.UTF_8);
        digest.update(source, 0, source.length);
        byte[] output = new byte[8];
        digest.doFinal(output, 0);
        return HexFormat.of().formatHex(output);
    }

    private void initSqlite() throws Exception {
        String connectionString = config.getConnectionString();
        boolean inMemory = ":memory:".equals(connectionString);
        if (!inMemory) {
            Path dbPath = Path.of(connectionString).toAbsolutePath();
            Path parent = dbPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            connectionString = dbPath.toString();
        }
        String jdbcUrl = inMemory ? "jdbc:sqlite::memory:" : "jdbc:sqlite:" + connectionString;
        connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            if (config.isDbEnableWal() && !inMemory) {
                statement.execute("PRAGMA journal_mode=WAL");
            }
        }
    }

    private void initPostgreSql() throws SQLException {
        String connectionString = config.getConnectionString() == null ? "" : config.getConnectionString().trim();
        if (connectionString.isEmpty()) {
            throw new IllegalArgumentException("PostgreSQL requires a non-empty connection_string");
        }
        connection = DriverManager.getConnection(convertToJdbcUrl(connectionString, "postgresql"));
    }

    private void initMySql() throws SQLException {
        String connectionString = config.getConnectionString() == null ? "" : config.getConnectionString().trim();
        if (connectionString.isEmpty()) {
            throw new IllegalArgumentException("MySQL requires a non-empty connection_string");
        }
        connection = DriverManager.getConnection(convertToJdbcUrl(connectionString, "mysql"));
    }

    private String convertToJdbcUrl(String connectionString, String databaseType) {
        if (connectionString.startsWith("jdbc:")) {
            return connectionString;
        }
        if ("postgresql".equals(databaseType)) {
            if (connectionString.startsWith("postgres://")) {
                return "jdbc:postgresql://" + connectionString.substring("postgres://".length());
            }
            if (connectionString.startsWith("postgresql://")) {
                return "jdbc:postgresql://" + connectionString.substring("postgresql://".length());
            }
        }
        if ("mysql".equals(databaseType)) {
            if (connectionString.startsWith("mysql://")) {
                return "jdbc:mysql://" + connectionString.substring("mysql://".length());
            }
            if (connectionString.startsWith("mysql+aiomysql://")) {
                return "jdbc:mysql://" + connectionString.substring("mysql+aiomysql://".length());
            }
        }
        return "jdbc:" + databaseType + "://" + connectionString;
    }

    private void createStaticTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS team_info (
                        team_name VARCHAR(255) PRIMARY KEY,
                        display_name VARCHAR(255) NOT NULL,
                        leader_member_name VARCHAR(255) NOT NULL,
                        "desc" CLOB,
                        prompt CLOB,
                        created BIGINT NOT NULL,
                        updated_at BIGINT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS team_member (
                        member_name VARCHAR(255) NOT NULL,
                        team_name VARCHAR(255) NOT NULL,
                        display_name VARCHAR(255) NOT NULL,
                        "desc" CLOB,
                        agent_card CLOB NOT NULL,
                        status VARCHAR(255) NOT NULL,
                        execution_status VARCHAR(255),
                        mode VARCHAR(255) NOT NULL,
                        role VARCHAR(255) NOT NULL DEFAULT 'teammate',
                        prompt CLOB,
                        model_ref_json CLOB,
                        updated_at BIGINT,
                        PRIMARY KEY (member_name, team_name)
                    )
                    """);
        }
    }

    private void ensureTeamMemberRoleColumn() throws SQLException {
        Set<String> tableNames = getTableNames();
        if (!tableNames.contains("team_member")) {
            return;
        }

        Set<String> columns = new LinkedHashSet<>();
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(null, null, "%", null)) {
            while (resultSet.next()) {
                String tableName = resultSet.getString("TABLE_NAME");
                if ("team_member".equalsIgnoreCase(tableName)) {
                    columns.add(resultSet.getString("COLUMN_NAME").toLowerCase());
                }
            }
        }
        if (columns.contains("role")) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            try {
                statement.execute("ALTER TABLE team_member ADD COLUMN role VARCHAR(255) NOT NULL DEFAULT 'teammate'");
            } catch (SQLException ignored) {
                statement.execute("ALTER TABLE \"team_member\" ADD COLUMN role VARCHAR(255) NOT NULL DEFAULT 'teammate'");
            }
        }
        TEAM_LOGGER.info(
                "Migrated legacy team_member table: added role column with default %s",
                "teammate"
        );
    }

    private boolean startsWithDynamicPrefix(String tableName) {
        for (String prefix : TEAM_DYNAMIC_TABLE_PREFIXES) {
            if (tableName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String dynamicTaskSql(String suffix) {
        return """
                CREATE TABLE IF NOT EXISTS "team_task_%s" (
                    task_id VARCHAR(255) PRIMARY KEY,
                    team_name VARCHAR(255) NOT NULL,
                    title CLOB NOT NULL,
                    content CLOB NOT NULL,
                    status VARCHAR(255) NOT NULL,
                    assignee VARCHAR(255),
                    updated_at BIGINT
                )
                """.formatted(suffix);
    }

    private String dynamicTaskDependencySql(String suffix) {
        return """
                CREATE TABLE IF NOT EXISTS "team_task_dependency_%s" (
                    task_id VARCHAR(255) NOT NULL,
                    depends_on_task_id VARCHAR(255) NOT NULL,
                    team_name VARCHAR(255) NOT NULL,
                    resolved BOOLEAN DEFAULT FALSE,
                    PRIMARY KEY (task_id, depends_on_task_id)
                )
                """.formatted(suffix);
    }

    private String dynamicMessageSql(String suffix) {
        return """
                CREATE TABLE IF NOT EXISTS "team_message_%s" (
                    message_id VARCHAR(255) PRIMARY KEY,
                    team_name VARCHAR(255) NOT NULL,
                    from_member_name VARCHAR(255) NOT NULL,
                    to_member_name VARCHAR(255),
                    content CLOB NOT NULL,
                    timestamp BIGINT NOT NULL,
                    broadcast BOOLEAN NOT NULL,
                    is_read BOOLEAN
                )
                """.formatted(suffix);
    }

    private String dynamicMessageReadStatusSql(String suffix) {
        return """
                CREATE TABLE IF NOT EXISTS "message_read_status_%s" (
                    member_name VARCHAR(255) NOT NULL,
                    team_name VARCHAR(255) NOT NULL,
                    read_at BIGINT,
                    PRIMARY KEY (member_name, team_name)
                )
                """.formatted(suffix);
    }

    public record CleanupResult(List<String> deletedTables, List<String> clearedTables) {
    }
}
