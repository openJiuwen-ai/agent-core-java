/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.migrator;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.memory.manage.mem_model.DbModel;
import com.openjiuwen.core.memory.manage.mem_model.SqlDbStore;
import com.openjiuwen.core.memory.migration.operation.AddColumnOperation;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.RenameColumnOperation;
import com.openjiuwen.core.memory.migration.operation.UpdateColumnTypeOperation;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;

/**
 * SQL schema migrator using JDBC. Simplified version of Python's Alembic-based SQLMigrator.
 * Supports add column, rename column, and update column type operations.
 */
public class SqlMigrator {

  private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;
  private static final Set<String> SUPPORTED_TABLES = supportedTables();

  private final SqlDbStore sqlDb;
  private final MemoryMetaManager memoryMetaManager;

  /** Auto-generated for codecheck compliance. */
  public SqlMigrator(SqlDbStore sqlDb) {
    this.sqlDb = sqlDb;
    this.memoryMetaManager = new MemoryMetaManager(sqlDb);
  }

  /** Auto-generated for codecheck compliance. */
  public static void validateTable(String tableName) {
    if (!SUPPORTED_TABLES.contains(tableName)) {
      throw new IllegalArgumentException("Unsupported table name: " + tableName);
    }
  }

  /** Auto-generated for codecheck compliance. */
  public Map<String, Boolean> batchMigrate(List<Map<String, Object>> migrations) {
    Map<String, Boolean> results = new LinkedHashMap<>();
    if (migrations == null) {
      return results;
    }
    for (Map<String, Object> migration : migrations) {
      Object rawTableName = migration == null ? null : migration.get("table_name");
      String tableName = rawTableName instanceof String value ? value : null;
      Object rawOperations = migration == null ? null : migration.get("operations");
      List<BaseOperation> operations = castOperations(rawOperations);
      results.put(tableName, tryMigrate(tableName, operations));
    }
    return results;
  }

  /** Auto-generated for codecheck compliance. */
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
      List<BaseOperation> pendingOps =
          operations.stream().filter(op -> cv == null || op.getSchemaVersion() > cv).toList();

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
          MEMORY_LOGGER.error(
              "[{}] Unsupported engine type for SQL migration: {}",
              LogEventType.MEMORY_INIT,
              engine.getClass().getName());
          return false;
        }

        conn.setAutoCommit(false);
        String dialect = detectDialect(conn);

        for (BaseOperation op : pendingOps) {
          MEMORY_LOGGER.info(
              "[{}] Executing SQL migration: {} v={}",
              LogEventType.MEMORY_INIT,
              op.getDescription(),
              op.getSchemaVersion());
          executeSqlOperation(conn, op, dialect);
        }

        // Update version in memory_meta
        int targetVersion = pendingOps.get(pendingOps.size() - 1).getSchemaVersion();
        updateMetaVersion(conn, tableName, String.valueOf(targetVersion));

        conn.commit();
        return true;
      } catch (Exception e) {
        if (conn != null) {
          try {
            conn.rollback();
          } catch (SQLException ignored) {
            // Preserve the original migration failure; rollback is best-effort cleanup.
          }
        }
        throw e;
      } finally {
        if (ownConnection && conn != null) {
          try {
            conn.close();
          } catch (SQLException ignored) {
            // Best-effort close after migration; the operation result was already determined.
          }
        }
      }
    } catch (Exception e) {
      MEMORY_LOGGER.error(
          "[{}] SQL migration failed for table {}: {}",
          LogEventType.MEMORY_INIT,
          tableName,
          e.getMessage());
      return false;
    }
  }

  private void executeSqlOperation(Connection conn, BaseOperation op, String dialect)
      throws SQLException {
    if (op instanceof AddColumnOperation addColumnOperation) {
      executeAddColumn(conn, addColumnOperation);
      return;
    }
    if (op instanceof RenameColumnOperation renameColumnOperation) {
      executeRenameColumn(conn, renameColumnOperation, dialect);
      return;
    }
    if (op instanceof UpdateColumnTypeOperation updateColumnTypeOperation) {
      executeUpdateColumnType(conn, updateColumnTypeOperation, dialect);
      return;
    }
    throw new UnsupportedOperationException(
        "Unsupported SQL operation: " + op.getClass().getName());
  }

  private void executeAddColumn(Connection conn, AddColumnOperation op) throws SQLException {
    validateTable(op.getTable());
    StringBuilder sql =
        new StringBuilder("ALTER TABLE ")
            .append(op.getTable())
            .append(" ADD COLUMN ")
            .append(op.getColumnName())
            .append(" ")
            .append(toSqlType(op.getColumnType()));
    if (!op.isNullable()) {
      sql.append(" NOT NULL");
    }
    if (op.getDefaultValue() != null) {
      sql.append(" DEFAULT ").append(formatDefault(op.getDefaultValue()));
    }
    try (Statement stmt = conn.createStatement()) {
      stmt.execute(sql.toString());
    }
  }

  private void executeRenameColumn(Connection conn, RenameColumnOperation op, String dialect)
      throws SQLException {
    validateTable(op.getTable());
    if ("mysql".equals(dialect)) {
      ColumnDefinition column = getRequiredColumn(conn, op.getTable(), op.getOldColumnName());
      String sql =
          "ALTER TABLE "
              + op.getTable()
              + " CHANGE "
              + op.getOldColumnName()
              + " "
              + buildColumnDefinition(column, column.typeName(), op.getNewColumnName());
      try (Statement stmt = conn.createStatement()) {
        stmt.execute(sql);
      }
      return;
    }
    String sql =
        "ALTER TABLE "
            + op.getTable()
            + " RENAME COLUMN "
            + op.getOldColumnName()
            + " TO "
            + op.getNewColumnName();
    try (Statement stmt = conn.createStatement()) {
      stmt.execute(sql);
    }
  }

  private void executeUpdateColumnType(
      Connection conn, UpdateColumnTypeOperation op, String dialect) throws SQLException {
    validateTable(op.getTable());
    if ("sqlite".equals(dialect)) {
      alterColumnTypeSqlite(conn, op.getTable(), op.getColumnName(), op.getNewColumnType());
      return;
    }
    if ("mysql".equals(dialect)) {
      ColumnDefinition column = getRequiredColumn(conn, op.getTable(), op.getColumnName());
      String sql =
          "ALTER TABLE "
              + op.getTable()
              + " MODIFY COLUMN "
              + buildColumnDefinition(column, toSqlType(op.getNewColumnType()), column.name());
      try (Statement stmt = conn.createStatement()) {
        stmt.execute(sql);
      }
      return;
    }
    String sql =
        "ALTER TABLE "
            + op.getTable()
            + " ALTER COLUMN "
            + op.getColumnName()
            + " TYPE "
            + toSqlType(op.getNewColumnType());
    try (Statement stmt = conn.createStatement()) {
      stmt.execute(sql);
    }
  }

  private void alterColumnTypeSqlite(
      Connection conn, String tableName, String columnName, String newColumnType)
      throws SQLException {
    List<ColumnDefinition> columns = getTableColumns(conn, tableName);
    if (columns.isEmpty()) {
      throw new SQLException("Table not found for SQLite migration: " + tableName);
    }

    boolean columnFound = false;
    List<String> columnNames = new ArrayList<>();
    List<String> columnDefinitions = new ArrayList<>();
    for (ColumnDefinition column : columns) {
      String effectiveType = column.typeName();
      if (column.name().equalsIgnoreCase(columnName)) {
        effectiveType = toSqlType(newColumnType);
        columnFound = true;
      }
      columnNames.add(column.name());
      columnDefinitions.add(buildColumnDefinition(column, effectiveType, column.name()));
    }
    if (!columnFound) {
      throw new SQLException(
          "Column not found for SQLite migration: " + tableName + "." + columnName);
    }

    String tempTable = tableName + "_new_" + columnName;
    String createSql =
        "CREATE TABLE " + tempTable + " (" + String.join(", ", columnDefinitions) + ")";
    String copySql =
        "INSERT INTO "
            + tempTable
            + " ("
            + String.join(", ", columnNames)
            + ") SELECT "
            + String.join(", ", columnNames)
            + " FROM "
            + tableName;

    try (Statement stmt = conn.createStatement()) {
      stmt.execute("DROP TABLE IF EXISTS " + tempTable);
      stmt.execute(createSql);
      stmt.execute(copySql);
      stmt.execute("DROP TABLE " + tableName);
      stmt.execute("ALTER TABLE " + tempTable + " RENAME TO " + tableName);
    }
    MEMORY_LOGGER.info(
        "[{}] SQLite column type migration completed for {}.{} -> {}",
        LogEventType.MEMORY_INIT,
        tableName,
        columnName,
        newColumnType);
  }

  private List<ColumnDefinition> getTableColumns(Connection conn, String tableName)
      throws SQLException {
    DatabaseMetaData metaData = conn.getMetaData();
    Set<String> primaryKeys = new HashSet<>();
    try (ResultSet pkRs = metaData.getPrimaryKeys(conn.getCatalog(), conn.getSchema(), tableName)) {
      while (pkRs.next()) {
        primaryKeys.add(pkRs.getString("COLUMN_NAME"));
      }
    }

    List<ColumnDefinition> columns = new ArrayList<>();
    try (ResultSet rs = metaData.getColumns(conn.getCatalog(), conn.getSchema(), tableName, null)) {
      while (rs.next()) {
        columns.add(
            new ColumnDefinition(
                rs.getString("COLUMN_NAME"),
                rs.getString("TYPE_NAME"),
                rs.getInt("COLUMN_SIZE"),
                rs.getInt("DECIMAL_DIGITS"),
                rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                rs.getString("COLUMN_DEF"),
                primaryKeys.contains(rs.getString("COLUMN_NAME"))));
      }
    }
    return columns;
  }

  private ColumnDefinition getRequiredColumn(Connection conn, String tableName, String columnName)
      throws SQLException {
    return getTableColumns(conn, tableName).stream()
        .filter(column -> column.name().equalsIgnoreCase(columnName))
        .findFirst()
        .orElseThrow(
            () ->
                new SQLException(
                    "Column not found for migration: " + tableName + "." + columnName));
  }

  private String buildColumnDefinition(
      ColumnDefinition column, String typeName, String columnName) {
    StringBuilder definition =
        new StringBuilder(columnName).append(" ").append(normalizeTypeName(typeName));
    if (!column.nullable()) {
      definition.append(" NOT NULL");
    }
    if (column.defaultValue() != null && !column.defaultValue().isBlank()) {
      definition.append(" DEFAULT ").append(column.defaultValue());
    }
    if (column.primaryKey()) {
      definition.append(" PRIMARY KEY");
    }
    return definition.toString();
  }

  private String normalizeTypeName(String typeName) {
    return typeName == null || typeName.isBlank() ? "TEXT" : typeName;
  }

  private String toSqlType(String typeString) {
    if (typeString == null || typeString.isBlank()) {
      return "TEXT";
    }
    String trimmed = typeString.trim();
    int paren = trimmed.indexOf('(');
    String base = (paren >= 0 ? trimmed.substring(0, paren) : trimmed).toUpperCase(Locale.ROOT);
    String suffix = paren >= 0 ? trimmed.substring(paren) : "";
    return switch (base) {
      case "STRING", "VARCHAR" -> suffix.isBlank() ? "VARCHAR(255)" : "VARCHAR" + suffix;
      case "INTEGER", "INT" -> "INTEGER";
      case "DATETIME" -> "TIMESTAMP";
      case "BOOLEAN", "BOOL" -> "BOOLEAN";
      case "TEXT" -> "TEXT";
      case "FLOAT" -> "FLOAT";
      default -> "TEXT";
    };
  }

  @SuppressWarnings("unchecked")
  private List<BaseOperation> castOperations(Object rawOperations) {
    if (rawOperations == null) {
      return Collections.emptyList();
    }
    if (rawOperations instanceof List<?> rawList) {
      List<BaseOperation> operations = new ArrayList<>();
      for (Object raw : rawList) {
        if (!(raw instanceof BaseOperation operation)) {
          throw new IllegalArgumentException("Unsupported SQL migration operation: " + raw);
        }
        operations.add(operation);
      }
      return operations;
    }
    throw new IllegalArgumentException("operations must be a List<BaseOperation>");
  }

  private static Set<String> supportedTables() {
    Set<String> tables = new HashSet<>();
    for (String[] tableConfig : DbModel.MEMORY_TABLES_CONFIG) {
      tables.add(tableConfig[0]);
    }
    return Collections.unmodifiableSet(tables);
  }

  private record ColumnDefinition(
      String name,
      String typeName,
      int size,
      int decimalDigits,
      boolean nullable,
      String defaultValue,
      boolean primaryKey) {}

  private void updateMetaVersion(Connection conn, String tableName, String version)
      throws SQLException {
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
      String driverName = conn.getMetaData().getDriverName().toLowerCase(Locale.ROOT);
      String url = conn.getMetaData().getURL();
      if (driverName.contains("sqlite")) {
        return "sqlite";
      }
      if (driverName.contains("mysql")) {
        return "mysql";
      }
      if (url != null && url.toLowerCase(Locale.ROOT).contains("mode=mysql")) {
        return "mysql";
      }
      if (driverName.contains("postgresql") || driverName.contains("postgres")) {
        return "postgresql";
      }
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
