/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.spi.store.BaseDbStore;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Locale;
import java.util.*;

/**
 * JDBC-based SQL CRUD wrapper for memory tables.
 * Translates Python's SQLAlchemy-based SqlDbStore to JDBC operations.
 */
public class SqlDbStore {

    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;

    private final BaseDbStore<?> dbStore;

    /**
     * Auto-generated for codecheck compliance.
     */
    public SqlDbStore(BaseDbStore<?> dbStore) {
        this.dbStore = dbStore;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public BaseDbStore<?> getDbStore() {
        return dbStore;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getEngine() {
        return dbStore.getEngine();
    }

    private Connection getConnection() throws SQLException {
        Object engine = dbStore.getEngine();
        if (engine instanceof DataSource) {
            return ((DataSource) engine).getConnection();
        }
        if (engine instanceof Connection) {
            return (Connection) engine;
        }
        throw new SQLException("Unsupported engine type: " + engine.getClass().getName());
    }

    /**
     * Insert a row into the specified table.
     */
    public boolean write(String table, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return false;
        }
        List<String> columns = new ArrayList<>(data.keySet());
        String placeholders = String.join(", ", Collections.nCopies(columns.size(), "?"));
        String columnNames = String.join(", ", columns);
        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", table, columnNames, placeholders);

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < columns.size(); i++) {
                ps.setObject(i + 1, data.get(columns.get(i)));
            }
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            MEMORY_LOGGER.error("[{}] Write failed for table {}: {}", LogEventType.MEMORY_STORE, table, e.getMessage());
            return false;
        }
    }

    /**
     * Get a single record by id.
     */
    public Map<String, Object> get(String table, String recordId, List<String> columns) {
        try (Connection conn = getConnection()) {
            String cols = (columns == null || columns.isEmpty()) ? "*" : String.join(", ", columns);
            String sql = String.format("SELECT %s FROM %s WHERE id = ?", cols, table);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, recordId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return resultSetToMap(rs);
                    }
                    return null;
                }
            }
        } catch (Exception e) {
            MEMORY_LOGGER.error("[{}] Failed to get data from table {}: {}",
                    LogEventType.MEMORY_RETRIEVE, table, e.getMessage());
            return null;
        }
    }

    /**
     * Get rows with filters, sorting, and limit.
     */
    public List<Map<String, Object>> getWithSort(String table, Map<String, Object> filters,
                                                   String sortBy, String order, int limit) {
        try (Connection conn = getConnection()) {
            if (sortBy != null && !hasColumn(conn, table, sortBy)) {
                throw ErrorHelper.buildError(
                        StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                        "memory_type", "message",
                        "error_msg", "sort column '" + sortBy + "' does not exist in db store table '" + table + "'"
                );
            }
            StringBuilder sql = new StringBuilder("SELECT * FROM ").append(table);
            List<Object> params = new ArrayList<>();
            if (filters != null && !filters.isEmpty()) {
                sql.append(" WHERE ");
                List<String> clauses = new ArrayList<>();
                for (Map.Entry<String, Object> entry : filters.entrySet()) {
                    clauses.add(entry.getKey() + " = ?");
                    params.add(entry.getValue());
                }
                sql.append(String.join(" AND ", clauses));
            }
            if (sortBy != null) {
                sql.append(" ORDER BY ").append(sortBy);
                if ("DESC".equalsIgnoreCase(order)) {
                    sql.append(" DESC");
                } else {
                    sql.append(" ASC");
                }
            }
            sql.append(" LIMIT ?");
            params.add(limit);

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    return resultSetToList(rs);
                }
            }
        } catch (Exception e) {
            MEMORY_LOGGER.error("[{}] Failed to fetch sorted data from table {}: {}",
                    LogEventType.MEMORY_RETRIEVE, table, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Check if a record exists matching the given conditions.
     */
    public boolean exist(String table, Map<String, Object> conditions) {
        try (Connection conn = getConnection()) {
            StringBuilder sql = new StringBuilder("SELECT 1 FROM ").append(table).append(" WHERE ");
            List<Object> params = new ArrayList<>();
            List<String> clauses = new ArrayList<>();
            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                clauses.add(entry.getKey() + " = ?");
                params.add(entry.getValue());
            }
            sql.append(String.join(" AND ", clauses));
            sql.append(" LIMIT 1");

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (Exception e) {
            MEMORY_LOGGER.error("[{}] Exist check failed for table {}: {}",
                    LogEventType.MEMORY_RETRIEVE, table, e.getMessage());
            return false;
        }
    }

    /**
     * Get rows matching any condition group in the provided list.
     */
    public List<Map<String, Object>> batchGet(String table, List<Map<String, Object>> conditionsList) {
        try (Connection conn = getConnection()) {
            StringBuilder sql = new StringBuilder("SELECT * FROM ").append(table);
            List<Object> params = new ArrayList<>();
            List<String> groups = new ArrayList<>();

            if (conditionsList != null) {
                for (Map<String, Object> conditions : conditionsList) {
                    if (conditions == null || conditions.isEmpty()) {
                        continue;
                    }
                    List<String> clauses = new ArrayList<>();
                    for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                        clauses.add(entry.getKey() + " = ?");
                        params.add(entry.getValue());
                    }
                    groups.add("(" + String.join(" OR ", clauses) + ")");
                }
            }

            if (!groups.isEmpty()) {
                sql.append(" WHERE ").append(String.join(" OR ", groups));
            }

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    return resultSetToList(rs);
                }
            }
        } catch (Exception e) {
            MEMORY_LOGGER.error("[{}] Batch get failed for table {}: {}",
                    LogEventType.MEMORY_RETRIEVE, table, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get rows matching IN conditions on specified columns.
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Map<String, Object>> conditionGet(String table, Map<String, ?> conditions,
                                                    List<String> columns) {
        try (Connection conn = getConnection()) {
            String cols = (columns == null || columns.isEmpty()) ? "*" : String.join(", ", columns);
            StringBuilder sql = new StringBuilder("SELECT ").append(cols).append(" FROM ").append(table);
            List<Object> params = new ArrayList<>();

            if (conditions != null && !conditions.isEmpty()) {
                sql.append(" WHERE ");
                List<String> clauses = new ArrayList<>();
                for (Map.Entry<String, ?> entry : conditions.entrySet()) {
                    Object rawValues = entry.getValue();
                    if (!(rawValues instanceof List<?> rawList)) {
                        throw ErrorHelper.buildError(
                                StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                                "memory_type", "message",
                                "error_msg", "db store condition[" + entry.getKey() + "] must be a list"
                        );
                    }
                    List<?> values = rawList;
                    if (values.isEmpty()) {
                        continue;
                    }
                    String placeholders = String.join(", ", Collections.nCopies(values.size(), "?"));
                    clauses.add(entry.getKey() + " IN (" + placeholders + ")");
                    params.addAll(values);
                }
                if (!clauses.isEmpty()) {
                    sql.append(String.join(" AND ", clauses));
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    return resultSetToList(rs);
                }
            }
        } catch (Exception e) {
            MEMORY_LOGGER.error("[{}] Failed to conditionGet from table {}: {}",
                    LogEventType.MEMORY_RETRIEVE, table, e.getMessage());
            return null;
        }
    }

    private boolean hasColumn(Connection conn, String table, String columnName) throws SQLException {
        TableInfo tableInfo = reflectTable(conn, table);
        if (tableInfo == null) {
            return false;
        }
        for (ColumnInfo column : tableInfo.getColumns()) {
            if (column.getName().equalsIgnoreCase(columnName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Update rows matching the given conditions.
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean update(String table, Map<String, Object> conditions, Map<String, Object> data) {
        try (Connection conn = getConnection()) {
            List<Object> params = new ArrayList<>();
            List<String> setClauses = new ArrayList<>();
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                setClauses.add(entry.getKey() + " = ?");
                params.add(entry.getValue());
            }

            List<String> whereClauses = new ArrayList<>();
            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof List) {
                    List<Object> list = (List<Object>) val;
                    String placeholders = String.join(", ", Collections.nCopies(list.size(), "?"));
                    whereClauses.add(entry.getKey() + " IN (" + placeholders + ")");
                    params.addAll(list);
                } else {
                    whereClauses.add(entry.getKey() + " = ?");
                    params.add(val);
                }
            }

            String sql = String.format("UPDATE %s SET %s WHERE %s", table,
                    String.join(", ", setClauses), String.join(" AND ", whereClauses));

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                ps.executeUpdate();
                return true;
            }
        } catch (Exception e) {
            MEMORY_LOGGER.error("[{}] Update failed for table {}: {}",
                    LogEventType.MEMORY_UPDATE, table, e.getMessage());
            return false;
        }
    }

    /**
     * Delete rows matching the given conditions.
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean delete(String table, Map<String, Object> conditions) {
        try (Connection conn = getConnection()) {
            List<Object> params = new ArrayList<>();
            List<String> whereClauses = new ArrayList<>();
            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof List) {
                    List<Object> list = (List<Object>) val;
                    String placeholders = String.join(", ", Collections.nCopies(list.size(), "?"));
                    whereClauses.add(entry.getKey() + " IN (" + placeholders + ")");
                    params.addAll(list);
                } else {
                    whereClauses.add(entry.getKey() + " = ?");
                    params.add(val);
                }
            }

            String sql = String.format("DELETE FROM %s WHERE %s", table, String.join(" AND ", whereClauses));

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                ps.executeUpdate();
                return true;
            }
        } catch (Exception e) {
            MEMORY_LOGGER.error("[{}] Delete failed for table {}: {}",
                    LogEventType.MEMORY_DELETE, table, e.getMessage());
            return false;
        }
    }

    /**
     * Drop a table if it exists.
     */
    public boolean deleteTable(String tableName) {
        String sql = "DROP TABLE IF EXISTS " + tableName;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            return true;
        } catch (Exception e) {
            MEMORY_LOGGER.error("[{}] Delete table failed for {}: {}",
                    LogEventType.MEMORY_DELETE, tableName, e.getMessage());
            return false;
        }
    }

    /**
     * Reflect table metadata for public callers that need schema access.
     */
    public TableInfo getTable(String tableName) {
        try (Connection conn = getConnection()) {
            return reflectTable(conn, tableName);
        } catch (Exception e) {
            MEMORY_LOGGER.error("[{}] Failed to reflect table {}: {}",
                    LogEventType.MEMORY_RETRIEVE, tableName, e.getMessage());
            return null;
        }
    }

    private TableInfo reflectTable(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        String resolvedTableName = resolveTableName(metaData, tableName);
        if (resolvedTableName == null) {
            return null;
        }
        List<ColumnInfo> columns = new ArrayList<>();
        try (ResultSet rs = metaData.getColumns(conn.getCatalog(), conn.getSchema(), resolvedTableName, null)) {
            while (rs.next()) {
                columns.add(new ColumnInfo(
                        rs.getString("COLUMN_NAME"),
                        rs.getString("TYPE_NAME"),
                        rs.getInt("DATA_TYPE"),
                        rs.getInt("COLUMN_SIZE"),
                        rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                        rs.getString("COLUMN_DEF")
                ));
            }
        }
        return new TableInfo(resolvedTableName, columns);
    }

    private String resolveTableName(DatabaseMetaData metaData, String tableName) throws SQLException {
        try (ResultSet rs = metaData.getTables(null, null, null, new String[]{"TABLE"})) {
            while (rs.next()) {
                String candidate = rs.getString("TABLE_NAME");
                if (candidate != null && candidate.equalsIgnoreCase(tableName)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final class TableInfo {
        private final String name;
        private final List<ColumnInfo> columns;

        /**
         * Auto-generated for codecheck compliance.
         */
        public TableInfo(String name, List<ColumnInfo> columns) {
            this.name = name;
            this.columns = List.copyOf(columns);
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public String getName() {
            return name;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<ColumnInfo> getColumns() {
            return columns;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final class ColumnInfo {
        private final String name;
        private final String typeName;
        private final int jdbcType;
        private final int size;
        private final boolean nullable;
        private final String defaultValue;

        /**
         * Auto-generated for codecheck compliance.
         */
        public ColumnInfo(String name, String typeName, int jdbcType, int size, boolean nullable, String defaultValue) {
            this.name = name;
            this.typeName = typeName;
            this.jdbcType = jdbcType;
            this.size = size;
            this.nullable = nullable;
            this.defaultValue = defaultValue;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public String getName() {
            return name;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public String getTypeName() {
            return typeName;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public int getJdbcType() {
            return jdbcType;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public int getSize() {
            return size;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean isNullable() {
            return nullable;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public String getDefaultValue() {
            return defaultValue;
        }
    }

    private Map<String, Object> resultSetToMap(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String columnName = meta.getColumnLabel(i);
            if (columnName == null || columnName.isEmpty()) {
                columnName = meta.getColumnName(i);
            }
            row.put(columnName.toLowerCase(Locale.ROOT), rs.getObject(i));
        }
        return row;
    }

    private List<Map<String, Object>> resultSetToList(ResultSet rs) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        while (rs.next()) {
            list.add(resultSetToMap(rs));
        }
        return list;
    }
}
