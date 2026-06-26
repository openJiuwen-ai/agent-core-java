/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.store.BaseDbStore;
import com.openjiuwen.core.memory.migration.MigrationPlan;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Table-creation support for memory SQL models.
 *
 * <p>Mirrors Python's module-level helpers in
 * {@code openjiuwen/core/memory/manage/mem_model/db_model.py}.</p>
 */
public final class DbModelSupport {

    public static final List<MemoryTableConfig> MEMORY_TABLES_CONFIG = List.of(
            new MemoryTableConfig(UserMessage.TABLE_NAME, "user_messages"),
            new MemoryTableConfig(ScopeUserMapping.TABLE_NAME, "scope_user_mapping")
    );

    private DbModelSupport() {
    }

    public static CompletableFuture<Void> createTables(BaseDbStore<?> dbStore) {
        return CompletableFuture.runAsync(() -> {
            DataSource dataSource = requireDataSource(dbStore);
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(true);

                List<String> newlyCreatedTables = new ArrayList<>();
                boolean recreateUserMessage = recreateLegacyUserMessageTable(connection);

                for (MemoryTableConfig tableConfig : MEMORY_TABLES_CONFIG) {
                    if (recreateUserMessage && UserMessage.TABLE_NAME.equals(tableConfig.tableName())) {
                        newlyCreatedTables.add(tableConfig.tableName());
                        continue;
                    }
                    if (!hasTable(connection, tableConfig.tableName())) {
                        newlyCreatedTables.add(tableConfig.tableName());
                    }
                }

                createMemoryMetaTable(connection);
                createUserMessageTable(connection);
                createScopeUserMappingTable(connection);
                updateSchemaVersions(connection, newlyCreatedTables);
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        });
    }

    private static boolean recreateLegacyUserMessageTable(Connection connection) throws SQLException {
        if (!hasTable(connection, UserMessage.TABLE_NAME)) {
            return false;
        }

        Set<String> columns = getColumnNames(connection, UserMessage.TABLE_NAME);
        if (!columns.contains("group_id")) {
            return false;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + UserMessage.TABLE_NAME);
        }
        Loggers.MEMORY.debug("delete old version sql table");
        return true;
    }

    private static void createMemoryMetaTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS memory_meta (
                        table_name VARCHAR(64) NOT NULL PRIMARY KEY,
                        schema_version VARCHAR(64) NOT NULL
                    )
                    """);
        }
    }

    private static void createUserMessageTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS user_message (
                        message_id VARCHAR(64) NOT NULL PRIMARY KEY,
                        user_id VARCHAR(64) NOT NULL,
                        scope_id VARCHAR(64) NOT NULL,
                        content VARCHAR(4096) NOT NULL,
                        session_id VARCHAR(64),
                        role VARCHAR(32),
                        timestamp VARCHAR(32)
                    )
                    """);
        }
    }

    private static void createScopeUserMappingTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS scope_user_mapping (
                        user_id VARCHAR(64) NOT NULL,
                        scope_id VARCHAR(64) NOT NULL,
                        PRIMARY KEY (user_id, scope_id)
                    )
                    """);
        }
    }

    private static void updateSchemaVersions(Connection connection, List<String> newlyCreatedTables) throws SQLException {
        for (MemoryTableConfig tableConfig : MEMORY_TABLES_CONFIG) {
            if (!newlyCreatedTables.contains(tableConfig.tableName())) {
                continue;
            }
            int currentVersion = MigrationPlan.getSqlRegistry().getCurrentVersion(tableConfig.entityKey());
            if (currentVersion <= 0) {
                continue;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO memory_meta (table_name, schema_version) VALUES (?, ?)"
            )) {
                statement.setString(1, tableConfig.tableName());
                statement.setString(2, String.valueOf(currentVersion));
                statement.executeUpdate();
            }
        }
    }

    private static DataSource requireDataSource(BaseDbStore<?> dbStore) {
        Object engine = dbStore.getAsyncEngine();
        if (engine instanceof DataSource dataSource) {
            return dataSource;
        }
        throw new IllegalArgumentException("db_store.getAsyncEngine() must return a DataSource");
    }

    private static boolean hasTable(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet resultSet = metadata.getTables(connection.getCatalog(), null, null, new String[]{"TABLE"})) {
            while (resultSet.next()) {
                String existingName = resultSet.getString("TABLE_NAME");
                if (existingName != null && existingName.equalsIgnoreCase(tableName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> getColumnNames(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        Set<String> columns = new java.util.LinkedHashSet<>();
        try (ResultSet resultSet = metadata.getColumns(connection.getCatalog(), null, null, null)) {
            while (resultSet.next()) {
                String existingTable = resultSet.getString("TABLE_NAME");
                if (existingTable != null && existingTable.equalsIgnoreCase(tableName)) {
                    String columnName = resultSet.getString("COLUMN_NAME");
                    if (columnName != null) {
                        columns.add(columnName.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        return columns;
    }

    /**
     * Table metadata used by downstream SQL migrators.
     *
     * <p>Mirrors Python's entries in {@code MEMORY_TABLES_CONFIG} from
     * {@code openjiuwen/core/memory/manage/mem_model/db_model.py}.</p>
     */
    public record MemoryTableConfig(String tableName, String entityKey) {
    }
}
