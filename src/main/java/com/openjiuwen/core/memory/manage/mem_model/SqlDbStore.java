/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.store.BaseDbStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Mirrors Python's {@code SqlDbStore} in
 * {@code openjiuwen/core/memory/manage/mem_model/sql_db_store.py}.
 */
public class SqlDbStore {

    private static final String ID_COLUMN = "id";
    private static final String ASC = "ASC";
    private static final String DESC = "DESC";

    private final BaseDbStore<?> dbStore;
    private final Map<String, TableInfo> tableCache = new LinkedHashMap<>();

    public SqlDbStore(BaseDbStore<?> dbStore) {
        this.dbStore = dbStore;
    }

    public CompletableFuture<Boolean> write(String table, Map<String, Object> data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TableInfo tableInfo = getTableBlocking(table);
                String sql = buildInsertSql(tableInfo.name(), data.keySet());
                try (Connection connection = dataSource().getConnection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    bindValues(statement, new ArrayList<>(data.values()));
                    statement.executeUpdate();
                    return true;
                }
            } catch (Exception ex) {
                Loggers.MEMORY.error("Write failed, table_name={}", table, ex);
                return false;
            }
        });
    }

    public CompletableFuture<Map<String, Object>> get(String table, String recordId) {
        return get(table, recordId, List.of());
    }

    public CompletableFuture<Map<String, Object>> get(String table, String recordId, List<String> columns) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TableInfo tableInfo = getTableBlocking(table);
                List<String> selectedColumns = normalizeColumns(columns, tableInfo);
                String sql = "SELECT " + selectClause(selectedColumns) + " FROM " + tableInfo.name()
                        + " WHERE " + ID_COLUMN + " = ?";
                try (Connection connection = dataSource().getConnection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setObject(1, recordId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            return null;
                        }
                        return rowToMap(resultSet);
                    }
                }
            } catch (Exception ex) {
                Loggers.MEMORY.error("Failed to get data, table_name={}, record_id={}", table, recordId, ex);
                return null;
            }
        });
    }

    public CompletableFuture<List<Map<String, Object>>> getWithSort(String table, Map<String, Object> filters) {
        return getWithSort(table, filters, "timestamp", ASC, 100);
    }

    public CompletableFuture<List<Map<String, Object>>> getWithSort(
            String table,
            Map<String, Object> filters,
            String sortBy,
            String order,
            int limit
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TableInfo tableInfo = getTableBlocking(table);
                String sortColumn = requireKnownColumn(sortBy, tableInfo);
                StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableInfo.name());
                List<Object> parameters = new ArrayList<>();
                appendFilterWhere(sql, parameters, filters, tableInfo);
                sql.append(" ORDER BY ").append(sortColumn).append(' ')
                        .append(DESC.equalsIgnoreCase(order) ? DESC : ASC);
                sql.append(" LIMIT ?");
                parameters.add(limit);
                return queryRows(sql.toString(), parameters);
            } catch (Exception ex) {
                Loggers.MEMORY.error("Failed to fetch filtered and sorted data, table_name={}", table, ex);
                return List.of();
            }
        });
    }

    public CompletableFuture<Boolean> exist(String table, Map<String, Object> conditions) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TableInfo tableInfo = getTableBlocking(table);
                WhereClause where = buildEqualityWhere(conditions, tableInfo, Joiner.AND);
                String sql = "SELECT 1 FROM " + tableInfo.name() + where.sql() + " LIMIT 1";
                try (Connection connection = dataSource().getConnection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    bindValues(statement, where.parameters());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        return resultSet.next();
                    }
                }
            } catch (Exception ex) {
                throw new CompletionException(ex);
            }
        });
    }

    public CompletableFuture<List<Map<String, Object>>> batchGet(
            String table,
            List<Map<String, Object>> conditionsList
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TableInfo tableInfo = getTableBlocking(table);
                List<Object> parameters = new ArrayList<>();
                StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableInfo.name());
                if (conditionsList != null && !conditionsList.isEmpty()) {
                    List<String> conditionClauses = new ArrayList<>();
                    for (Map<String, Object> conditions : conditionsList) {
                        WhereClause clause = buildEqualityWhere(conditions, tableInfo, Joiner.OR);
                        if (!clause.sql().isBlank()) {
                            conditionClauses.add(stripWhere(clause.sql()));
                            parameters.addAll(clause.parameters());
                        }
                    }
                    if (!conditionClauses.isEmpty()) {
                        sql.append(" WHERE ").append(String.join(" OR ", conditionClauses));
                    }
                }
                return queryRows(sql.toString(), parameters);
            } catch (Exception ex) {
                throw new CompletionException(ex);
            }
        });
    }

    public CompletableFuture<List<Map<String, Object>>> conditionGet(
            String table,
            Map<String, ?> conditions,
            List<String> columns
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TableInfo tableInfo = getTableBlocking(table);
                List<String> selectedColumns = normalizeColumns(columns, tableInfo);
                StringBuilder sql = new StringBuilder("SELECT ")
                        .append(selectClause(selectedColumns))
                        .append(" FROM ")
                        .append(tableInfo.name());
                List<Object> parameters = new ArrayList<>();
                List<String> clauses = new ArrayList<>();
                if (conditions != null) {
                    for (Map.Entry<String, ?> entry : conditions.entrySet()) {
                        String column = requireKnownColumn(entry.getKey(), tableInfo);
                        Object values = entry.getValue();
                        if (!(values instanceof List<?> listValues)) {
                            throw ErrorHelper.buildError(StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                                    "memory_type", "message",
                                    "error_msg", "db store condition[" + entry.getKey()
                                            + "] must be a list, (got "
                                            + values.getClass().getSimpleName() + ")");
                        }
                        clauses.add(inClause(column, listValues.size()));
                        parameters.addAll(listValues);
                    }
                }
                if (!clauses.isEmpty()) {
                    sql.append(" WHERE ").append(String.join(" AND ", clauses));
                }
                return queryRows(sql.toString(), parameters);
            } catch (Exception ex) {
                Loggers.MEMORY.error("Failed to get data via condition_get, table_name={}", table, ex);
                return null;
            }
        });
    }

    public CompletableFuture<Boolean> update(
            String table,
            Map<String, ?> conditions,
            Map<String, Object> data
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TableInfo tableInfo = getTableBlocking(table);
                WhereClause where = buildFlexibleWhere(conditions, tableInfo);
                String sql = "UPDATE " + tableInfo.name() + " SET " + assignments(data.keySet())
                        + where.sql();
                List<Object> parameters = new ArrayList<>(data.values());
                parameters.addAll(where.parameters());
                try (Connection connection = dataSource().getConnection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    bindValues(statement, parameters);
                    statement.executeUpdate();
                    return true;
                }
            } catch (Exception ex) {
                Loggers.MEMORY.error("Update failed, table_name={}", table, ex);
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> delete(String table, Map<String, ?> conditions) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TableInfo tableInfo = getTableBlocking(table);
                WhereClause where = buildFlexibleWhere(conditions, tableInfo);
                String sql = "DELETE FROM " + tableInfo.name() + where.sql();
                try (Connection connection = dataSource().getConnection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    bindValues(statement, where.parameters());
                    statement.executeUpdate();
                    return true;
                }
            } catch (Exception ex) {
                Loggers.MEMORY.error("Delete failed, table_name={}", table, ex);
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> deleteTable(String tableName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String table = requireIdentifier(tableName);
                try (Connection connection = dataSource().getConnection();
                     Statement statement = connection.createStatement()) {
                    statement.execute("DROP TABLE IF EXISTS " + table);
                }
                invalidateTableCache(tableName);
                return true;
            } catch (Exception ex) {
                Loggers.MEMORY.error("Delete table failed, table_name={}", tableName, ex);
                return false;
            }
        });
    }

    public void invalidateTableCache(String tableName) {
        if (tableName != null) {
            synchronized (tableCache) {
                tableCache.remove(tableName);
            }
        }
    }

    public CompletableFuture<TableInfo> getTable(String tableName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getTableBlocking(tableName);
            } catch (SQLException ex) {
                throw new CompletionException(ex);
            }
        });
    }

    private TableInfo getTableBlocking(String tableName) throws SQLException {
        synchronized (tableCache) {
            TableInfo cached = tableCache.get(tableName);
            if (cached != null) {
                return cached;
            }
        }
        String table = requireIdentifier(tableName);
        Set<String> columns = new LinkedHashSet<>();
        try (Connection connection = dataSource().getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet resultSet = metadata.getColumns(connection.getCatalog(), null, table, null)) {
                while (resultSet.next()) {
                    String columnName = resultSet.getString("COLUMN_NAME");
                    if (columnName != null) {
                        columns.add(columnName.toLowerCase(Locale.ROOT));
                    }
                }
            }
            if (columns.isEmpty()) {
                try (ResultSet resultSet = metadata.getColumns(connection.getCatalog(), null, table.toUpperCase(Locale.ROOT), null)) {
                    while (resultSet.next()) {
                        String columnName = resultSet.getString("COLUMN_NAME");
                        if (columnName != null) {
                            columns.add(columnName.toLowerCase(Locale.ROOT));
                        }
                    }
                }
            }
        }
        TableInfo tableInfo = new TableInfo(table, columns);
        synchronized (tableCache) {
            tableCache.put(tableName, tableInfo);
        }
        return tableInfo;
    }

    /**
     * Exposes the JDBC data source backing Python's SQLAlchemy async engine.
     *
     * <p>Mirrors Python's {@code SQLDBStore.sql_engine} usage in
     * {@code openjiuwen/core/memory/manage/mem_model/sql_db_store.py}.</p>
     *
     * @return JDBC data source used by this store
     */
    public DataSource getDataSource() {
        Object engine = dbStore.getAsyncEngine();
        if (engine instanceof DataSource dataSource) {
            return dataSource;
        }
        throw new IllegalArgumentException("db_store.get_async_engine() must return a DataSource");
    }

    private DataSource dataSource() {
        return getDataSource();
    }

    private List<Map<String, Object>> queryRows(String sql, List<Object> parameters) throws SQLException {
        try (Connection connection = dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindValues(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(rowToMap(resultSet));
                }
                return rows;
            }
        }
    }

    private static void appendFilterWhere(
            StringBuilder sql,
            List<Object> parameters,
            Map<String, Object> filters,
            TableInfo tableInfo
    ) {
        if (filters == null || filters.isEmpty()) {
            return;
        }
        List<String> clauses = new ArrayList<>();
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            if (!tableInfo.containsColumn(entry.getKey())) {
                continue;
            }
            clauses.add(requireIdentifier(entry.getKey()) + " = ?");
            parameters.add(entry.getValue());
        }
        if (!clauses.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", clauses));
        }
    }

    private static WhereClause buildEqualityWhere(Map<String, ?> conditions, TableInfo tableInfo, Joiner joiner) {
        if (conditions == null || conditions.isEmpty()) {
            return new WhereClause("", List.of());
        }
        List<String> clauses = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        for (Map.Entry<String, ?> entry : conditions.entrySet()) {
            String column = requireKnownColumn(entry.getKey(), tableInfo);
            clauses.add(column + " = ?");
            parameters.add(entry.getValue());
        }
        return new WhereClause(" WHERE " + String.join(" " + joiner.name() + " ", clauses), parameters);
    }

    private static WhereClause buildFlexibleWhere(Map<String, ?> conditions, TableInfo tableInfo) {
        if (conditions == null || conditions.isEmpty()) {
            return new WhereClause("", List.of());
        }
        List<String> clauses = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        for (Map.Entry<String, ?> entry : conditions.entrySet()) {
            String column = requireKnownColumn(entry.getKey(), tableInfo);
            Object value = entry.getValue();
            if (value instanceof Collection<?> collection) {
                clauses.add(inClause(column, collection.size()));
                parameters.addAll(collection);
            } else {
                clauses.add(column + " = ?");
                parameters.add(value);
            }
        }
        return new WhereClause(" WHERE " + String.join(" AND ", clauses), parameters);
    }

    private static String assignments(Collection<String> columns) {
        List<String> assignments = new ArrayList<>();
        for (String column : columns) {
            assignments.add(requireIdentifier(column) + " = ?");
        }
        return String.join(", ", assignments);
    }

    private static String buildInsertSql(String table, Collection<String> columns) {
        List<String> names = columns.stream().map(SqlDbStore::requireIdentifier).toList();
        return "INSERT INTO " + table + " (" + String.join(", ", names) + ") VALUES ("
                + String.join(", ", Collections.nCopies(names.size(), "?")) + ")";
    }

    private static List<String> normalizeColumns(List<String> columns, TableInfo tableInfo) {
        if (columns == null || columns.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String column : columns) {
            result.add(requireKnownColumn(column, tableInfo));
        }
        return result;
    }

    private static String selectClause(List<String> columns) {
        return columns == null || columns.isEmpty() ? "*" : String.join(", ", columns);
    }

    private static String inClause(String column, int size) {
        if (size <= 0) {
            return "1 = 0";
        }
        return column + " IN (" + String.join(", ", Collections.nCopies(size, "?")) + ")";
    }

    private static String stripWhere(String sql) {
        return sql.startsWith(" WHERE ") ? sql.substring(" WHERE ".length()) : sql;
    }

    private static String requireKnownColumn(String columnName, TableInfo tableInfo) {
        String column = requireIdentifier(columnName);
        if (!tableInfo.columns().isEmpty() && !tableInfo.containsColumn(column)) {
            throw new IllegalArgumentException("column does not exist: " + column);
        }
        return column;
    }

    private static String requireIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("invalid SQL identifier: " + identifier);
        }
        return identifier;
    }

    private static void bindValues(PreparedStatement statement, List<?> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            statement.setObject(i + 1, values.get(i));
        }
    }

    private static Map<String, Object> rowToMap(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metadata = resultSet.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= metadata.getColumnCount(); i++) {
            row.put(metadata.getColumnLabel(i).toLowerCase(Locale.ROOT), resultSet.getObject(i));
        }
        return row;
    }

    private enum Joiner {
        AND,
        OR
    }

    private record WhereClause(String sql, List<Object> parameters) {
    }

    /**
     * Mirrors Python's reflected SQLAlchemy {@code Table} cache entry in
     * {@code openjiuwen/core/memory/manage/mem_model/sql_db_store.py}.
     */
    public static final class TableInfo {
        private final String name;
        private final Set<String> columns;

        public TableInfo(String name, Set<String> columns) {
            this.name = name;
            this.columns = columns == null ? Set.of() : Set.copyOf(columns);
        }

        public String name() {
            return name;
        }

        public Set<String> columns() {
            return columns;
        }

        public boolean containsColumn(String column) {
            return columns.isEmpty() || columns.contains(column.toLowerCase(Locale.ROOT));
        }
    }
}
