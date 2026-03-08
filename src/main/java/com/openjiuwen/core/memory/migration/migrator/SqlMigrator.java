/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.memory.migration.migrator;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.memory.manage.mem_model.SqlDbStore;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;

import javax.sql.DataSource;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL schema migrator using JDBC. Simplified version of Python's Alembic-based SQLMigrator.
 * Supports add column, rename column, and update column type operations.
 */
public class SqlMigrator {

    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;

    private final SqlDbStore sqlDb;
    private final MemoryMetaManager memoryMetaManager;

    public SqlMigrator(SqlDbStore sqlDb) {
        this.sqlDb = sqlDb;
        this.memoryMetaManager = new MemoryMetaManager(sqlDb);
    }

    public boolean tryMigrate(String entityKey, List<BaseOperation> operations) {
        if (operations == null || operations.isEmpty()) {
            return true;
        }
        String tableName = entityKey;
        Integer currentVersion = null;

        try {
            List<Map<String, Object>> currentMeta = memoryMetaManager.getByTableName(tableName);
            if (currentMeta != null && !currentMeta.isEmpty()) {
                currentVersion = Integer.parseInt(String.valueOf(currentMeta.get(0).get("schema_version")));
            }

            final Integer cv = currentVersion;
            List<BaseOperation> pendingOps = operations.stream()
                    .filter(op -> cv == null || op.getSchemaVersion() > cv)
                    .toList();

            if (pendingOps.isEmpty()) {
                return true;
            }

            Object engine = sqlDb.getEngine();
            Connection conn = null;
            boolean ownConnection = false;
            try {
                if (engine instanceof DataSource ds) {
                    conn = ds.getConnection();
                    ownConnection = true;
                } else if (engine instanceof Connection c) {
                    conn = c;
                } else {
                    MEMORY_LOGGER.error("[{}] Unsupported engine type for SQL migration: {}",
                            LogEventType.MEMORY_INIT, engine.getClass().getName());
                    return false;
                }

                conn.setAutoCommit(false);
                String dialect = detectDialect(conn);

                for (BaseOperation op : pendingOps) {
                    MEMORY_LOGGER.info("[{}] Executing SQL migration: {} v={}",
                            LogEventType.MEMORY_INIT, op.getDescription(), op.getSchemaVersion());
                    executeSqlOperation(conn, op, dialect);
                }

                // Update version in memory_meta
                int targetVersion = pendingOps.get(pendingOps.size() - 1).getSchemaVersion();
                updateMetaVersion(conn, tableName, String.valueOf(targetVersion));

                conn.commit();
                return true;
            } catch (Exception e) {
                if (conn != null) {
                    try { conn.rollback(); } catch (SQLException ignored) {}
                }
                throw e;
            } finally {
                if (ownConnection && conn != null) {
                    try { conn.close(); } catch (SQLException ignored) {}
                }
            }
        } catch (Exception e) {
            MEMORY_LOGGER.error("[{}] SQL migration failed for table {}: {}",
                    LogEventType.MEMORY_INIT, tableName, e.getMessage());
            return false;
        }
    }

    private void executeSqlOperation(Connection conn, BaseOperation op, String dialect) throws SQLException {
        // Use reflection-like checks on operation class name since Operations.java uses package-private classes
        String className = op.getClass().getSimpleName();
        switch (className) {
            case "AddColumnOperation" -> executeAddColumn(conn, op);
            case "RenameColumnOperation" -> executeRenameColumn(conn, op, dialect);
            case "UpdateColumnTypeOperation" -> executeUpdateColumnType(conn, op, dialect);
            default -> throw new UnsupportedOperationException("Unsupported SQL operation: " + className);
        }
    }

    private void executeAddColumn(Connection conn, BaseOperation op) throws SQLException {
        // Use accessors via reflection since class is package-private
        try {
            String table = (String) op.getClass().getMethod("getTable").invoke(op);
            String columnName = (String) op.getClass().getMethod("getColumnName").invoke(op);
            String columnType = (String) op.getClass().getMethod("getColumnType").invoke(op);
            boolean nullable = (boolean) op.getClass().getMethod("isNullable").invoke(op);
            Object defaultValue = op.getClass().getMethod("getDefaultValue").invoke(op);

            StringBuilder sql = new StringBuilder("ALTER TABLE ")
                    .append(table).append(" ADD COLUMN ").append(columnName)
                    .append(" ").append(columnType);
            if (!nullable) {
                sql.append(" NOT NULL");
            }
            if (defaultValue != null) {
                sql.append(" DEFAULT ").append(formatDefault(defaultValue));
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql.toString());
            }
        } catch (ReflectiveOperationException e) {
            throw new SQLException("Failed to execute AddColumnOperation", e);
        }
    }

    private void executeRenameColumn(Connection conn, BaseOperation op, String dialect) throws SQLException {
        try {
            String table = (String) op.getClass().getMethod("getTable").invoke(op);
            String oldCol = (String) op.getClass().getMethod("getOldColumnName").invoke(op);
            String newCol = (String) op.getClass().getMethod("getNewColumnName").invoke(op);

            String sql;
            if ("sqlite".equals(dialect)) {
                sql = "ALTER TABLE " + table + " RENAME COLUMN " + oldCol + " TO " + newCol;
            } else {
                sql = "ALTER TABLE " + table + " RENAME COLUMN " + oldCol + " TO " + newCol;
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        } catch (ReflectiveOperationException e) {
            throw new SQLException("Failed to execute RenameColumnOperation", e);
        }
    }

    private void executeUpdateColumnType(Connection conn, BaseOperation op, String dialect) throws SQLException {
        try {
            String table = (String) op.getClass().getMethod("getTable").invoke(op);
            String columnName = (String) op.getClass().getMethod("getColumnName").invoke(op);
            String newType = (String) op.getClass().getMethod("getNewColumnType").invoke(op);

            if ("sqlite".equals(dialect)) {
                MEMORY_LOGGER.warn("[{}] SQLite does not support ALTER COLUMN TYPE. " +
                        "Consider recreating the table for {}.{}", LogEventType.MEMORY_INIT, table, columnName);
                return;
            }
            String sql = "ALTER TABLE " + table + " ALTER COLUMN " + columnName + " TYPE " + newType;
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        } catch (ReflectiveOperationException e) {
            throw new SQLException("Failed to execute UpdateColumnTypeOperation", e);
        }
    }

    private void updateMetaVersion(Connection conn, String tableName, String version) throws SQLException {
        // Try update first
        String updateSql = "UPDATE memory_meta SET schema_version = ? WHERE table_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, version);
            ps.setString(2, tableName);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                String insertSql = "INSERT INTO memory_meta (table_name, schema_version) VALUES (?, ?)";
                try (PreparedStatement ips = conn.prepareStatement(insertSql)) {
                    ips.setString(1, tableName);
                    ips.setString(2, version);
                    ips.executeUpdate();
                }
            }
        }
    }

    private String detectDialect(Connection conn) {
        try {
            String driverName = conn.getMetaData().getDriverName().toLowerCase();
            if (driverName.contains("sqlite")) return "sqlite";
            if (driverName.contains("mysql")) return "mysql";
            if (driverName.contains("postgresql") || driverName.contains("postgres")) return "postgresql";
            return "unknown";
        } catch (SQLException e) {
            return "unknown";
        }
    }

    private String formatDefault(Object value) {
        if (value instanceof String) {
            return "'" + value.toString().replace("'", "''") + "'";
        }
        return String.valueOf(value);
    }
}
