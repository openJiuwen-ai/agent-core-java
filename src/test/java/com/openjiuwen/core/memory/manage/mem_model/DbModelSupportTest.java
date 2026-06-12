/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.foundation.store.db.DefaultDbStore;
import com.openjiuwen.core.memory.migration.MigrationPlan;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused regression tests for memory DB model support.
 *
 * <p>Mirrors Python's table bootstrap behavior in
 * {@code openjiuwen/core/memory/manage/mem_model/db_model.py}.</p>
 */
class DbModelSupportTest {

    @AfterEach
    void clearSqlRegistry() {
        MigrationPlan.getSqlRegistry().clear();
    }

    @Test
    void createTablesCreatesExpectedTablesAndSchemaRows() throws Exception {
        MigrationPlan.getSqlRegistry().register("user_messages", new TestOperation(2, "user-messages-v2"));
        MigrationPlan.getSqlRegistry().register("scope_user_mapping", new TestOperation(1, "scope-user-v1"));

        JdbcDataSource dataSource = sqliteDataSource("create-all");
        DbModelSupport.createTables(new DefaultDbStore<>(dataSource)).join();

        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableNames(connection))
                    .contains("MEMORY_META", "USER_MESSAGE", "SCOPE_USER_MAPPING");
            assertThat(readSchemaRows(connection))
                    .containsExactlyInAnyOrder(
                            "user_message=2",
                            "scope_user_mapping=1"
                    );
        }
    }

    @Test
    void createTablesDropsLegacyUserMessageWithGroupId() throws Exception {
        JdbcDataSource dataSource = sqliteDataSource("legacy-group-id");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE user_message (
                        message_id VARCHAR(64) PRIMARY KEY,
                        group_id VARCHAR(64),
                        user_id VARCHAR(64),
                        scope_id VARCHAR(64),
                        content VARCHAR(4096)
                    )
                    """);
        }

        DbModelSupport.createTables(new DefaultDbStore<>(dataSource)).join();

        try (Connection connection = dataSource.getConnection()) {
            assertThat(columnNames(connection, "user_message"))
                    .contains("message_id", "user_id", "scope_id", "content", "session_id", "role", "timestamp")
                    .doesNotContain("group_id");
        }
    }

    @Test
    void createTablesSkipsSchemaRowsWhenVersionIsZero() throws Exception {
        JdbcDataSource dataSource = sqliteDataSource("zero-version");

        DbModelSupport.createTables(new DefaultDbStore<>(dataSource)).join();

        try (Connection connection = dataSource.getConnection()) {
            assertThat(readSchemaRows(connection)).isEmpty();
        }
    }

    @Test
    void createTablesDoesNotOverwriteExistingMeta() throws Exception {
        MigrationPlan.getSqlRegistry().register("user_messages", new TestOperation(10, "user-messages-v10"));
        JdbcDataSource dataSource = sqliteDataSource("preserve-meta");

        DbModelSupport.createTables(new DefaultDbStore<>(dataSource)).join();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM memory_meta");
            statement.executeUpdate("INSERT INTO memory_meta (table_name, schema_version) VALUES ('user_message', '1')");
            statement.executeUpdate("INSERT INTO memory_meta (table_name, schema_version) VALUES ('scope_user_mapping', '2')");
        }

        DbModelSupport.createTables(new DefaultDbStore<>(dataSource)).join();

        try (Connection connection = dataSource.getConnection()) {
            assertThat(readSchemaRows(connection))
                    .containsExactly("scope_user_mapping=2", "user_message=1");
        }
    }

    private static JdbcDataSource sqliteDataSource(String suffix) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + suffix + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static List<String> tableNames(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            return tableNames(connection);
        }
    }

    private static List<String> tableNames(Connection connection) throws Exception {
        List<String> result = new ArrayList<>();
        try (ResultSet resultSet = connection.getMetaData().getTables(connection.getCatalog(), null, null, new String[]{"TABLE"})) {
            while (resultSet.next()) {
                result.add(resultSet.getString("TABLE_NAME"));
            }
        }
        return result;
    }

    private static List<String> columnNames(Connection connection, String tableName) throws Exception {
        List<String> result = new ArrayList<>();
        try (ResultSet resultSet = connection.getMetaData().getColumns(connection.getCatalog(), null, null, null)) {
            while (resultSet.next()) {
                if (tableName.equalsIgnoreCase(resultSet.getString("TABLE_NAME"))) {
                    result.add(resultSet.getString("COLUMN_NAME").toLowerCase());
                }
            }
        }
        return result;
    }

    private static List<String> readSchemaRows(Connection connection) throws Exception {
        List<String> result = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT table_name, schema_version FROM memory_meta ORDER BY table_name")) {
            while (resultSet.next()) {
                result.add(resultSet.getString(1) + "=" + resultSet.getString(2));
            }
        }
        return result;
    }

    private static final class TestOperation extends BaseOperation {

        private TestOperation(int version, String description) {
            super(new OperationMetadata(version, description));
        }
    }
}
