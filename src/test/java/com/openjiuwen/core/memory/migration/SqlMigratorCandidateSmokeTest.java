/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration;

import com.openjiuwen.core.foundation.store.db.DefaultDbStore;
import com.openjiuwen.core.memory.manage.mem_model.DbModelSupport;
import com.openjiuwen.core.memory.manage.mem_model.SqlDbStore;
import com.openjiuwen.core.memory.migration.migrator.SqlMigrator;
import com.openjiuwen.core.memory.migration.operation.AddColumnOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;

import org.h2.jdbcx.JdbcDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

/**
 * Focused validation for SQL migration DDL over a real JDBC data source.
 *
 * <p>Mirrors Python's {@code SQLMigrator} in
 * {@code openjiuwen/core/memory/migration/migrator/sql_migrator.py}.</p>
 */
public final class SqlMigratorCandidateSmokeTest {

    private static final int TARGET_SCHEMA_VERSION = 77;

    private SqlMigratorCandidateSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        JdbcDataSource dataSource = newDataSource();
        DefaultDbStore<JdbcDataSource> dbStore = new DefaultDbStore<>(dataSource);
        DbModelSupport.createTables(dbStore).join();

        SqlDbStore sqlDbStore = new SqlDbStore(dbStore);
        SqlMigrator migrator = new SqlMigrator(sqlDbStore);
        AddColumnOperation operation = new AddColumnOperation(
                new OperationMetadata(TARGET_SCHEMA_VERSION, "sql h2 add column smoke"),
                "user_message",
                "extra_field",
                "String"
        );

        require(migrator.tryMigrate("user_message", List.of(operation)).join(), "SQL migration should succeed");
        require(hasColumn(dataSource, "user_message", "extra_field"), "extra_field column should be added");
        require(String.valueOf(TARGET_SCHEMA_VERSION).equals(schemaVersion(dataSource, "user_message")),
                "memory_meta schema version should update");

        System.out.println("PASS SqlMigratorCandidateSmokeTest");
    }

    private static JdbcDataSource newDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:sql_migrator_candidate;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static boolean hasColumn(JdbcDataSource dataSource, String tableName, String columnName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             ResultSet resultSet = connection.getMetaData()
                     .getColumns(connection.getCatalog(), null, tableName, null)) {
            while (resultSet.next()) {
                String currentName = resultSet.getString("COLUMN_NAME");
                if (columnName.equalsIgnoreCase(currentName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String schemaVersion(JdbcDataSource dataSource, String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT schema_version FROM memory_meta WHERE LOWER(table_name) = ?"
             )) {
            statement.setString(1, tableName.toLowerCase(Locale.ROOT));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
