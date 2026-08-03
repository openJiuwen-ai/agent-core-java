/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.migrator;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.memory.manage.mem_model.DbModelSupport;
import com.openjiuwen.core.memory.manage.mem_model.SqlDbStore;
import com.openjiuwen.core.memory.migration.operation.AddColumnOperation;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.RenameColumnOperation;
import com.openjiuwen.core.memory.migration.operation.UpdateColumnTypeOperation;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * <p>Mirrors Python's {@code SQLMigrator} in
 * {@code openjiuwen/core/memory/migration/migrator/sql_migrator.py}.</p>
 */
public class SqlMigrator {

    private final SqlDbStore sqlDb;
    private final MemoryMetaManager memoryMetaManager;
    private final DataSource dataSource;

    public SqlMigrator(SqlDbStore sqlDbStore) {
        this.sqlDb = sqlDbStore;
        this.memoryMetaManager = new MemoryMetaManager(sqlDbStore);
        this.dataSource = resolveDataSource(sqlDbStore);
    }

    public static String getSqlalchemyType(String typeString) {
        if (typeString == null || typeString.isBlank()) {
            return "CLOB";
        }

        String normalized = typeString.trim();
        int parameterStart = normalized.indexOf('(');
        if (parameterStart > 0 && normalized.endsWith(")")) {
            String baseType = normalized.substring(0, parameterStart).trim().toUpperCase(Locale.ROOT);
            String rawParameter = normalized.substring(parameterStart + 1, normalized.length() - 1).trim();
            if (("STRING".equals(baseType) || "VARCHAR".equals(baseType)) && rawParameter.matches("\\d+")) {
                return "VARCHAR(" + rawParameter + ")";
            }
            if ("TEXT".equals(baseType)) {
                return "CLOB";
            }
        }

        return switch (normalized.toUpperCase(Locale.ROOT)) {
            case "STRING", "VARCHAR" -> "VARCHAR(255)";
            case "INTEGER", "INT" -> "INTEGER";
            case "DATETIME", "TIMESTAMP" -> "TIMESTAMP";
            case "BOOLEAN", "BOOL" -> "BOOLEAN";
            case "TEXT", "CLOB" -> "CLOB";
            case "FLOAT", "DOUBLE", "REAL" -> "DOUBLE";
            default -> "CLOB";
        };
    }

    public CompletableFuture<Boolean> tryMigrate(String entityKey, List<BaseOperation> operations) {
        if (operations == null || operations.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }

        return CompletableFuture.supplyAsync(() -> {
            String tableName = entityKey;
            try {
                Integer currentVersion = getCurrentVersion(tableName);
                List<BaseOperation> pendingOperations = operations.stream()
                        .filter(operation -> currentVersion == null || operation.getSchemaVersion() > currentVersion)
                        .toList();
                if (pendingOperations.isEmpty()) {
                    return true;
                }

                try (Connection connection = dataSource.getConnection()) {
                    boolean originalAutoCommit = connection.getAutoCommit();
                    connection.setAutoCommit(false);
                    try {
                        String dialectName = detectDialect(connection);
                        for (BaseOperation operation : pendingOperations) {
                            executeOperation(connection, operation, dialectName);
                        }

                        int targetVersion = pendingOperations.get(pendingOperations.size() - 1).getSchemaVersion();
                        updateMetaVersion(connection, tableName, String.valueOf(targetVersion));
                        connection.commit();
                        connection.setAutoCommit(originalAutoCommit);
                        return true;
                    } catch (Exception migrationError) {
                        connection.rollback();
                        connection.setAutoCommit(originalAutoCommit);
                        throw migrationError;
                    }
                }
            } catch (Exception error) {
                Loggers.MEMORY.error("Error during migration of table {}: {}", tableName, error.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Map<String, Boolean>> batchMigrate(List<Map<String, Object>> migrations) {
        Map<String, Boolean> results = new LinkedHashMap<>();
        CompletableFuture<Map<String, Boolean>> chain = CompletableFuture.completedFuture(results);
        if (migrations == null) {
            return chain;
        }

        for (Map<String, Object> migration : migrations) {
            chain = chain.thenCompose(currentResults -> {
                String tableName = migration == null ? null : stringValue(migration.get("table_name"));
                List<BaseOperation> operations = extractOperations(migration);
                return tryMigrate(tableName, operations).thenApply(success -> {
                    currentResults.put(tableName, success);
                    return currentResults;
                });
            });
        }
        return chain;
    }

    private Integer getCurrentVersion(String tableName) {
        List<Map<String, Object>> currentMeta = memoryMetaManager.getByTableName(tableName).join();
        if (currentMeta == null || currentMeta.isEmpty()) {
            return null;
        }
        Object schemaVersion = currentMeta.get(0).get("schema_version");
        if (schemaVersion == null) {
            return null;
        }
        return Integer.parseInt(String.valueOf(schemaVersion));
    }

    private void executeOperation(Connection connection, BaseOperation operation, String dialectName) throws SQLException {
        if (operation instanceof AddColumnOperation addColumnOperation) {
            migrateAddColumn(connection, addColumnOperation);
            return;
        }
        if (operation instanceof RenameColumnOperation renameColumnOperation) {
            migrateRenameColumn(connection, renameColumnOperation, dialectName);
            return;
        }
        if (operation instanceof UpdateColumnTypeOperation updateColumnTypeOperation) {
            migrateUpdateColumnType(connection, updateColumnTypeOperation, dialectName);
            return;
        }
        throw new SQLException("Unsupported operation type: " + operation.getClass().getSimpleName());
    }

    private void migrateAddColumn(Connection connection, AddColumnOperation operation) throws SQLException {
        String tableName = requireSupportedTable(operation.getTable());
        String columnName = requireIdentifier(operation.getColumnName());
        StringBuilder sql = new StringBuilder("ALTER TABLE ")
                .append(tableName)
                .append(" ADD COLUMN ")
                .append(columnName)
                .append(' ')
                .append(getSqlalchemyType(operation.getColumnType()));
        if (!operation.isNullable()) {
            sql.append(" NOT NULL");
        }
        if (operation.getDefaultValue() != null) {
            sql.append(" DEFAULT ").append(formatDefault(operation.getDefaultValue()));
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql.toString());
        }
    }

    private void migrateRenameColumn(
            Connection connection,
            RenameColumnOperation operation,
            String dialectName
    ) throws SQLException {
        String tableName = requireSupportedTable(operation.getTable());
        String oldColumnName = requireIdentifier(operation.getOldColumnName());
        String newColumnName = requireIdentifier(operation.getNewColumnName());
        String sql;
        if ("mysql".equals(dialectName)) {
            String existingType = findColumnType(connection, tableName, oldColumnName);
            if (existingType != null && !existingType.isBlank()) {
                sql = "ALTER TABLE " + tableName + " CHANGE " + oldColumnName + " " + newColumnName + " " + existingType;
            } else {
                sql = "ALTER TABLE " + tableName + " RENAME COLUMN " + oldColumnName + " TO " + newColumnName;
            }
        } else {
            sql = "ALTER TABLE " + tableName + " RENAME COLUMN " + oldColumnName + " TO " + newColumnName;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void migrateUpdateColumnType(
            Connection connection,
            UpdateColumnTypeOperation operation,
            String dialectName
    ) throws SQLException {
        String tableName = requireSupportedTable(operation.getTable());
        String columnName = requireIdentifier(operation.getColumnName());
        String newColumnType = getSqlalchemyType(operation.getNewColumnType());
        if ("sqlite".equals(dialectName)) {
            alterColumnTypeSqlite(connection, tableName, columnName, newColumnType);
            return;
        }

        String sql = switch (dialectName) {
            case "h2" -> "ALTER TABLE " + tableName + " ALTER COLUMN " + columnName + " " + newColumnType;
            default -> "ALTER TABLE " + tableName + " ALTER COLUMN " + columnName + " TYPE " + newColumnType;
        };
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void alterColumnTypeSqlite(
            Connection connection,
            String tableName,
            String columnName,
            String newColumnType
    ) throws SQLException {
        List<ColumnDefinition> columns = getTableColumns(connection, tableName);
        if (columns.isEmpty()) {
            throw new SQLException("Table not found: " + tableName);
        }

        boolean columnFound = false;
        List<String> columnNames = new ArrayList<>();
        List<String> columnDefinitions = new ArrayList<>();
        for (ColumnDefinition column : columns) {
            String effectiveType = column.name().equalsIgnoreCase(columnName) ? newColumnType : column.typeName();
            if (column.name().equalsIgnoreCase(columnName)) {
                columnFound = true;
            }
            columnNames.add(column.name());
            columnDefinitions.add(buildColumnDefinition(column, effectiveType));
        }
        if (!columnFound) {
            throw new SQLException("Column " + columnName + " not found in table " + tableName);
        }

        String newTableName = tableName + "_new_" + columnName;
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + newTableName);
            statement.execute("CREATE TABLE " + newTableName + " (" + String.join(", ", columnDefinitions) + ")");
            statement.execute("INSERT INTO " + newTableName + " (" + String.join(", ", columnNames)
                    + ") SELECT " + String.join(", ", columnNames) + " FROM " + tableName);
            statement.execute("DROP TABLE " + tableName);
            statement.execute("ALTER TABLE " + newTableName + " RENAME TO " + tableName);
        }
        Loggers.MEMORY.info(
                "Successfully altered column type for {}.{} to {}",
                tableName,
                columnName,
                newColumnType
        );
    }

    private void updateMetaVersion(Connection connection, String tableName, String targetVersion) throws SQLException {
        try (PreparedStatement updateStatement = connection.prepareStatement(
                "UPDATE memory_meta SET schema_version = ? WHERE table_name = ?"
        )) {
            updateStatement.setString(1, targetVersion);
            updateStatement.setString(2, tableName);
            int updatedRows = updateStatement.executeUpdate();
            if (updatedRows != 0) {
                return;
            }
        }

        try (PreparedStatement insertStatement = connection.prepareStatement(
                "INSERT INTO memory_meta (table_name, schema_version) VALUES (?, ?)"
        )) {
            insertStatement.setString(1, tableName);
            insertStatement.setString(2, targetVersion);
            insertStatement.executeUpdate();
        }
    }

    private List<ColumnDefinition> getTableColumns(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        List<ColumnDefinition> columns = new ArrayList<>();
        try (ResultSet resultSet = metadata.getColumns(connection.getCatalog(), connection.getSchema(), tableName, null)) {
            while (resultSet.next()) {
                columns.add(new ColumnDefinition(
                        resultSet.getString("COLUMN_NAME"),
                        resultSet.getString("TYPE_NAME"),
                        resultSet.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                        resultSet.getString("COLUMN_DEF")
                ));
            }
        }
        return columns;
    }

    private String findColumnType(Connection connection, String tableName, String columnName) throws SQLException {
        for (ColumnDefinition column : getTableColumns(connection, tableName)) {
            if (column.name().equalsIgnoreCase(columnName)) {
                return column.typeName();
            }
        }
        return null;
    }

    private static String buildColumnDefinition(ColumnDefinition column, String typeName) {
        StringBuilder definition = new StringBuilder(column.name()).append(' ').append(typeName);
        if (!column.nullable()) {
            definition.append(" NOT NULL");
        }
        if (column.defaultValue() != null && !column.defaultValue().isBlank()) {
            definition.append(" DEFAULT ").append(column.defaultValue());
        }
        return definition.toString();
    }

    private static String detectDialect(Connection connection) {
        try {
            String driverName = connection.getMetaData().getDriverName().toLowerCase(Locale.ROOT);
            if (driverName.contains("sqlite")) {
                return "sqlite";
            }
            if (driverName.contains("h2")) {
                return "h2";
            }
            if (driverName.contains("mysql")) {
                return "mysql";
            }
            if (driverName.contains("postgresql") || driverName.contains("postgres")) {
                return "postgresql";
            }
            return "unknown";
        } catch (SQLException exception) {
            return "unknown";
        }
    }

    private static String requireSupportedTable(String tableName) throws SQLException {
        String normalized = requireIdentifier(tableName);
        boolean supported = DbModelSupport.MEMORY_TABLES_CONFIG.stream()
                .anyMatch(tableConfig -> tableConfig.tableName().equals(normalized));
        if (!supported) {
            throw new SQLException("Unsupported table name: " + tableName);
        }
        return normalized;
    }

    private static String requireIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("invalid SQL identifier: " + identifier);
        }
        return identifier;
    }

    private static String formatDefault(Object value) {
        if (value instanceof String text) {
            return "'" + text.replace("'", "''") + "'";
        }
        return String.valueOf(value);
    }

    private static List<BaseOperation> extractOperations(Map<String, Object> migration) {
        if (migration == null || !(migration.get("operations") instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(BaseOperation.class::isInstance)
                .map(BaseOperation.class::cast)
                .toList();
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static DataSource resolveDataSource(SqlDbStore sqlDbStore) {
        return sqlDbStore.getDataSource();
    }

    private record ColumnDefinition(
            String name,
            String typeName,
            boolean nullable,
            String defaultValue
    ) {
    }
}
