/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.memory.migration.migrator;

import com.openjiuwen.core.memory.manage.mem_model.SqlDbStore;
import com.openjiuwen.core.memory.migration.migrator.SqlMigrator;
import com.openjiuwen.core.memory.migration.operation.AddColumnOperation;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.RenameColumnOperation;
import com.openjiuwen.core.memory.migration.operation.UpdateColumnTypeOperation;
import com.openjiuwen.spi.store.BaseDbStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL migrator tests.
 *
 * <p>Mirrors Python's {@code TestSQLMigrator} in
 * {@code tests/unit_tests/core/memory/migration/migrator/test_sql_migrator.py}.
 */
class TestSqlMigrator {

    private JdbcDataSource dataSource;
    private SqlMigrator migrator;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE user_message (
                        message_id VARCHAR(64) PRIMARY KEY,
                        user_id VARCHAR(64) NOT NULL,
                        scope_id VARCHAR(64) NOT NULL,
                        content VARCHAR(4096) NOT NULL,
                        session_id VARCHAR(64),
                        role VARCHAR(32),
                        timestamp VARCHAR(32)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE scope_user_mapping (
                        user_id VARCHAR(64) NOT NULL,
                        scope_id VARCHAR(64) NOT NULL,
                        PRIMARY KEY (user_id, scope_id)
                    )
                    """);
            statement.execute("CREATE TABLE memory_meta (table_name VARCHAR(255) PRIMARY KEY, schema_version VARCHAR(32))");
        }

        migrator = new SqlMigrator(new SqlDbStore(new SimpleDbStore(dataSource)));
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
        }
    }

    @Test
    void testTryMigrateEmptyOperations() {
        assertTrue(migrator.tryMigrate("user_message", List.of()));
    }

    @Test
    void testAddColumnOperationUserMessage() throws Exception {
        List<BaseOperation> operations = List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add new_column to user_message"),
                        "user_message",
                        "new_column",
                        "String",
                        true,
                        null
                )
        );

        assertTrue(migrator.tryMigrate("user_message", operations));
        assertTrue(columnNames("user_message").contains("new_column"));
        assertEquals("1", schemaVersion("user_message"));
    }

    @Test
    void testAddColumnOperationScopeUserMapping() throws Exception {
        List<BaseOperation> operations = List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add new_column to scope_user_mapping"),
                        "scope_user_mapping",
                        "new_column",
                        "Integer",
                        false,
                        0
                )
        );

        assertTrue(migrator.tryMigrate("scope_user_mapping", operations));
        assertTrue(columnNames("scope_user_mapping").contains("new_column"));
        assertEquals("1", schemaVersion("scope_user_mapping"));
    }

    @Test
    void testAddColumnUnsupportedTable() {
        List<BaseOperation> operations = List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add column to unsupported table"),
                        "unsupported_table",
                        "new_column",
                        "String",
                        true,
                        null
                )
        );

        assertFalse(migrator.tryMigrate("unsupported_table", operations));
    }

    @Test
    void testRenameColumnOperation() throws Exception {
        List<BaseOperation> operations = List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add old_name column"),
                        "user_message",
                        "old_name",
                        "String",
                        true,
                        null
                ),
                new RenameColumnOperation(
                        new OperationMetadata(2, "Rename old_name to new_name"),
                        "user_message",
                        "old_name",
                        "new_name"
                )
        );

        assertTrue(migrator.tryMigrate("user_message", operations));
        List<String> columns = columnNames("user_message");
        assertFalse(columns.contains("old_name"));
        assertTrue(columns.contains("new_name"));
        assertEquals("2", schemaVersion("user_message"));
    }

    @Test
    void testRenameColumnUnsupportedTable() {
        List<BaseOperation> operations = List.of(
                new RenameColumnOperation(
                        new OperationMetadata(1, "Rename column in unsupported table"),
                        "unsupported_table",
                        "old_name",
                        "new_name"
                )
        );

        assertFalse(migrator.tryMigrate("unsupported_table", operations));
    }

    @Test
    void testUpdateColumnTypeOperation() throws Exception {
        List<BaseOperation> operations = List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add column with String type"),
                        "user_message",
                        "test_column",
                        "String",
                        true,
                        null
                ),
                new UpdateColumnTypeOperation(
                        new OperationMetadata(2, "Update column to Text type"),
                        "user_message",
                        "test_column",
                        "Text"
                )
        );

        assertTrue(migrator.tryMigrate("user_message", operations));
        assertTrue(columnType("user_message", "test_column").contains("clob")
                || columnType("user_message", "test_column").contains("character large object"));
        assertEquals("2", schemaVersion("user_message"));
    }

    @Test
    void testUpdateColumnTypeUnsupportedTable() {
        List<BaseOperation> operations = List.of(
                new UpdateColumnTypeOperation(
                        new OperationMetadata(1, "Update column in unsupported table"),
                        "unsupported_table",
                        "test_column",
                        "Text"
                )
        );

        assertFalse(migrator.tryMigrate("unsupported_table", operations));
    }

    @Test
    void testVersionControl() throws Exception {
        List<BaseOperation> operations = List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add column_v1"),
                        "user_message",
                        "column_v1",
                        "String",
                        true,
                        null
                ),
                new AddColumnOperation(
                        new OperationMetadata(2, "Add column_v2"),
                        "user_message",
                        "column_v2",
                        "String",
                        true,
                        null
                ),
                new AddColumnOperation(
                        new OperationMetadata(3, "Add column_v3"),
                        "user_message",
                        "column_v3",
                        "String",
                        true,
                        null
                )
        );
        assertTrue(migrator.tryMigrate("user_message", operations));

        List<String> columns = columnNames("user_message");
        assertTrue(columns.contains("column_v1"));
        assertTrue(columns.contains("column_v2"));
        assertTrue(columns.contains("column_v3"));

        List<BaseOperation> operationsV4 = List.of(
                new AddColumnOperation(
                        new OperationMetadata(4, "Add column_v4"),
                        "user_message",
                        "column_v4",
                        "String",
                        true,
                        null
                )
        );
        assertTrue(migrator.tryMigrate("user_message", operationsV4));

        assertTrue(columnNames("user_message").contains("column_v4"));
        assertEquals("4", schemaVersion("user_message"));
    }

    @Test
    void testMultipleOperationsInSingleMigration() throws Exception {
        List<BaseOperation> operations = List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add col1"),
                        "user_message",
                        "col1",
                        "String",
                        true,
                        null
                ),
                new AddColumnOperation(
                        new OperationMetadata(2, "Add col2"),
                        "user_message",
                        "col2",
                        "Integer",
                        true,
                        null
                ),
                new RenameColumnOperation(
                        new OperationMetadata(3, "Rename col1 to col1_renamed"),
                        "user_message",
                        "col1",
                        "col1_renamed"
                )
        );

        assertTrue(migrator.tryMigrate("user_message", operations));

        List<String> columns = columnNames("user_message");
        assertTrue(columns.contains("col1_renamed"));
        assertTrue(columns.contains("col2"));
        assertFalse(columns.contains("col1"));
    }

    @Test
    void testGetSqlalchemyTypeString() {
        assertEquals("VARCHAR(255)", migrator.getSqlalchemyType("String"));
        assertEquals("VARCHAR(255)", migrator.getSqlalchemyType("VARCHAR"));
    }

    @Test
    void testGetSqlalchemyTypeStringWithLength() {
        assertEquals("VARCHAR(100)", migrator.getSqlalchemyType("String(100)"));
        assertEquals("VARCHAR(255)", migrator.getSqlalchemyType("VARCHAR(255)"));
    }

    @Test
    void testGetSqlalchemyTypeInteger() {
        assertEquals("INTEGER", migrator.getSqlalchemyType("Integer"));
        assertEquals("INTEGER", migrator.getSqlalchemyType("INT"));
    }

    @Test
    void testGetSqlalchemyTypeDatetime() {
        assertEquals("TIMESTAMP", migrator.getSqlalchemyType("DateTime"));
        assertEquals("TIMESTAMP", migrator.getSqlalchemyType("DATETIME"));
    }

    @Test
    void testGetSqlalchemyTypeBoolean() {
        assertEquals("BOOLEAN", migrator.getSqlalchemyType("Boolean"));
        assertEquals("BOOLEAN", migrator.getSqlalchemyType("BOOL"));
    }

    @Test
    void testGetSqlalchemyTypeText() {
        assertEquals("CLOB", migrator.getSqlalchemyType("Text"));
        assertEquals("CLOB", migrator.getSqlalchemyType("TEXT"));
    }

    @Test
    void testGetSqlalchemyTypeFloat() {
        assertEquals("DOUBLE", migrator.getSqlalchemyType("Float"));
        assertEquals("DOUBLE", migrator.getSqlalchemyType("FLOAT"));
    }

    @Test
    void testGetSqlalchemyTypeUnknown() {
        assertEquals("CLOB", migrator.getSqlalchemyType("UnknownType"));
    }

    @Test
    void testBatchMigrate() throws Exception {
        List<Map<String, Object>> migrations = List.of(
                Map.of(
                        "table_name", "user_message",
                        "operations", List.of(
                                new AddColumnOperation(
                                        new OperationMetadata(1, "Add batch_col1"),
                                        "user_message",
                                        "batch_col1",
                                        "String",
                                        true,
                                        null
                                )
                        )
                ),
                Map.of(
                        "table_name", "scope_user_mapping",
                        "operations", List.of(
                                new AddColumnOperation(
                                        new OperationMetadata(1, "Add batch_col2"),
                                        "scope_user_mapping",
                                        "batch_col2",
                                        "Integer",
                                        true,
                                        null
                                )
                        )
                )
        );

        Map<String, Boolean> results = migrator.batchMigrate(migrations);

        assertTrue(results.get("user_message"));
        assertTrue(results.get("scope_user_mapping"));
        assertTrue(columnNames("user_message").contains("batch_col1"));
        assertTrue(columnNames("scope_user_mapping").contains("batch_col2"));
    }

    @Test
    void testUpdateColumnTypeNonexistentColumn() {
        List<BaseOperation> operations = List.of(
                new UpdateColumnTypeOperation(
                        new OperationMetadata(1, "Update non-existent column type"),
                        "user_message",
                        "nonexistent_column",
                        "Text"
                )
        );

        assertFalse(migrator.tryMigrate("user_message", operations));
    }

    @Test
    void testAddColumnWithDefaultValue() throws Exception {
        List<BaseOperation> operations = List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add column with default"),
                        "user_message",
                        "default_col",
                        "Integer",
                        false,
                        42
                )
        );

        assertTrue(migrator.tryMigrate("user_message", operations));
        insertUserMessage("default_msg", "test content");
        assertEquals(42, queryInt(
                "SELECT default_col FROM user_message WHERE message_id = ?",
                "default_msg"
        ));
    }

    @Test
    void testMigrationIdempotency() throws Exception {
        List<BaseOperation> operations = List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add idempotent_col"),
                        "user_message",
                        "idempotent_col",
                        "String",
                        true,
                        null
                )
        );

        assertTrue(migrator.tryMigrate("user_message", operations));
        assertTrue(migrator.tryMigrate("user_message", operations));
        assertTrue(columnNames("user_message").contains("idempotent_col"));
        assertEquals("1", schemaVersion("user_message"));
    }

    @Test
    void testMigrateWithDataPreservation() throws Exception {
        insertUserMessage("test_msg_1", "test content");

        List<BaseOperation> operations = List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add test_col"),
                        "user_message",
                        "test_col",
                        "String",
                        true,
                        null
                ),
                new UpdateColumnTypeOperation(
                        new OperationMetadata(2, "Update test_col to Text"),
                        "user_message",
                        "test_col",
                        "Text"
                )
        );

        assertTrue(migrator.tryMigrate("user_message", operations));
        assertEquals("test_msg_1", queryString(
                "SELECT message_id FROM user_message WHERE message_id = ?",
                "test_msg_1"
        ));
        assertEquals("test content", queryString(
                "SELECT content FROM user_message WHERE message_id = ?",
                "test_msg_1"
        ));
    }

    @Test
    void testEmptyOperationsList() {
        assertTrue(migrator.tryMigrate("test_table", List.of()));
    }

    @Test
    void testBatchMigrateWithEmptyOperations() {
        Map<String, Boolean> results = migrator.batchMigrate(List.of(
                Map.of("table_name", "user_message", "operations", List.of())
        ));

        assertTrue(results.containsKey("user_message"));
        assertTrue(results.get("user_message"));
    }

    @Test
    void testAddColumnTextType() throws Exception {
        List<BaseOperation> operations = List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add Text column"),
                        "user_message",
                        "text_col",
                        "Text",
                        true,
                        null
                )
        );

        assertTrue(migrator.tryMigrate("user_message", operations));
        String typeName = columnType("user_message", "text_col");
        assertTrue(typeName.contains("clob") || typeName.contains("character large object"));
    }

    @Test
    void testUpdateColumnTypeDataPreservation() throws Exception {
        List<BaseOperation> addOperations = List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add column with String type"),
                        "user_message",
                        "test_column",
                        "String",
                        true,
                        null
                )
        );
        assertTrue(migrator.tryMigrate("user_message", addOperations));

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO user_message "
                             + "(message_id, user_id, session_id, scope_id, role, content, timestamp, test_column) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, "test_id");
            statement.setString(2, "user1");
            statement.setString(3, "session1");
            statement.setString(4, "scope1");
            statement.setString(5, "user");
            statement.setString(6, "test message");
            statement.setString(7, "2026-06-02T00:00:00Z");
            statement.setString(8, "test_data");
            statement.executeUpdate();
        }

        List<BaseOperation> updateOperations = List.of(
                new UpdateColumnTypeOperation(
                        new OperationMetadata(2, "Update column to Text type"),
                        "user_message",
                        "test_column",
                        "Text"
                )
        );

        assertTrue(migrator.tryMigrate("user_message", updateOperations));
        assertEquals("test_data", queryString(
                "SELECT test_column FROM user_message WHERE message_id = ?",
                "test_id"
        ));
    }

    @Test
    void testSkipLowerVersionOperations() throws Exception {
        List<BaseOperation> operationsV1 = List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add column v1"),
                        "user_message",
                        "column_v1",
                        "String",
                        true,
                        null
                )
        );
        assertTrue(migrator.tryMigrate("user_message", operationsV1));

        List<BaseOperation> operationsV2 = List.of(
                new AddColumnOperation(
                        new OperationMetadata(2, "Add column v2"),
                        "user_message",
                        "column_v2",
                        "String",
                        true,
                        null
                )
        );
        assertTrue(migrator.tryMigrate("user_message", operationsV2));
        assertTrue(migrator.tryMigrate("user_message", operationsV1));

        List<String> columns = columnNames("user_message");
        assertTrue(columns.contains("column_v1"));
        assertTrue(columns.contains("column_v2"));
        assertEquals("2", schemaVersion("user_message"));
    }

    @Test
    void testMySqlLikeTryMigrateEmptyOperations() {
        assertTrue(migrator.tryMigrate("user_message", List.of()));
    }

    @Test
    void testMySqlLikeAddColumnOperationUserMessage() throws Exception {
        assertTrue(migrator.tryMigrate("user_message", List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add mysql_new_column to user_message"),
                        "user_message",
                        "mysql_new_column",
                        "String",
                        true,
                        null
                )
        )));

        assertTrue(columnNames("user_message").contains("mysql_new_column"));
    }

    @Test
    void testMySqlLikeAddColumnOperationScopeUserMapping() throws Exception {
        assertTrue(migrator.tryMigrate("scope_user_mapping", List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add mysql_new_column to scope_user_mapping"),
                        "scope_user_mapping",
                        "mysql_new_column",
                        "Integer",
                        false,
                        0
                )
        )));

        assertTrue(columnNames("scope_user_mapping").contains("mysql_new_column"));
    }

    @Test
    void testMySqlLikeRenameColumnOperation() throws Exception {
        assertTrue(migrator.tryMigrate("user_message", List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add old_name column"),
                        "user_message",
                        "old_name",
                        "String",
                        true,
                        null
                ),
                new RenameColumnOperation(
                        new OperationMetadata(2, "Rename old_name to new_name"),
                        "user_message",
                        "old_name",
                        "new_name"
                )
        )));

        List<String> columns = columnNames("user_message");
        assertFalse(columns.contains("old_name"));
        assertTrue(columns.contains("new_name"));
    }

    @Test
    void testMySqlLikeUpdateColumnTypeOperation() throws Exception {
        assertTrue(migrator.tryMigrate("user_message", List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add column with String type"),
                        "user_message",
                        "mysql_test_column",
                        "String",
                        true,
                        null
                ),
                new UpdateColumnTypeOperation(
                        new OperationMetadata(2, "Update column to Text type"),
                        "user_message",
                        "mysql_test_column",
                        "Text"
                )
        )));

        String typeName = columnType("user_message", "mysql_test_column");
        assertTrue(typeName.contains("clob") || typeName.contains("character large object"));
    }

    @Test
    void testMySqlLikeVersionControl() throws Exception {
        assertTrue(migrator.tryMigrate("user_message", List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add mysql column v1"),
                        "user_message",
                        "mysql_column_v1",
                        "String",
                        true,
                        null
                ),
                new AddColumnOperation(
                        new OperationMetadata(2, "Add mysql column v2"),
                        "user_message",
                        "mysql_column_v2",
                        "String",
                        true,
                        null
                ),
                new AddColumnOperation(
                        new OperationMetadata(3, "Add mysql column v3"),
                        "user_message",
                        "mysql_column_v3",
                        "String",
                        true,
                        null
                )
        )));

        assertTrue(columnNames("user_message").contains("mysql_column_v1"));
        assertTrue(columnNames("user_message").contains("mysql_column_v2"));
        assertTrue(columnNames("user_message").contains("mysql_column_v3"));
        assertTrue(migrator.tryMigrate("user_message", List.of(
                new AddColumnOperation(
                        new OperationMetadata(4, "Add mysql column v4"),
                        "user_message",
                        "mysql_column_v4",
                        "String",
                        true,
                        null
                )
        )));
        assertTrue(columnNames("user_message").contains("mysql_column_v4"));
    }

    @Test
    void testMySqlLikeMultipleOperationsInSingleMigration() throws Exception {
        assertTrue(migrator.tryMigrate("user_message", List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add mysql col1"),
                        "user_message",
                        "mysql_col1",
                        "String",
                        true,
                        null
                ),
                new AddColumnOperation(
                        new OperationMetadata(2, "Add mysql col2"),
                        "user_message",
                        "mysql_col2",
                        "Integer",
                        true,
                        null
                ),
                new RenameColumnOperation(
                        new OperationMetadata(3, "Rename mysql col1"),
                        "user_message",
                        "mysql_col1",
                        "mysql_col1_renamed"
                )
        )));

        List<String> columns = columnNames("user_message");
        assertTrue(columns.contains("mysql_col1_renamed"));
        assertTrue(columns.contains("mysql_col2"));
        assertFalse(columns.contains("mysql_col1"));
    }

    @Test
    void testMySqlLikeBatchMigrate() throws Exception {
        Map<String, Boolean> results = migrator.batchMigrate(List.of(
                Map.of(
                        "table_name", "user_message",
                        "operations", List.of(
                                new AddColumnOperation(
                                        new OperationMetadata(1, "Add mysql batch_col1"),
                                        "user_message",
                                        "mysql_batch_col1",
                                        "String",
                                        true,
                                        null
                                )
                        )
                ),
                Map.of(
                        "table_name", "scope_user_mapping",
                        "operations", List.of(
                                new AddColumnOperation(
                                        new OperationMetadata(1, "Add mysql batch_col2"),
                                        "scope_user_mapping",
                                        "mysql_batch_col2",
                                        "Integer",
                                        true,
                                        null
                                )
                        )
                )
        ));

        assertTrue(results.get("user_message"));
        assertTrue(results.get("scope_user_mapping"));
        assertTrue(columnNames("user_message").contains("mysql_batch_col1"));
        assertTrue(columnNames("scope_user_mapping").contains("mysql_batch_col2"));
    }

    @Test
    void testMySqlLikeAddColumnWithDefaultValue() throws Exception {
        assertTrue(migrator.tryMigrate("user_message", List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add mysql column with default"),
                        "user_message",
                        "mysql_default_col",
                        "Integer",
                        false,
                        42
                )
        )));

        insertUserMessage("mysql_default_msg", "mysql default content");
        assertEquals(42, queryInt(
                "SELECT mysql_default_col FROM user_message WHERE message_id = ?",
                "mysql_default_msg"
        ));
    }

    @Test
    void testMySqlLikeMigrationIdempotency() throws Exception {
        List<BaseOperation> operations = List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add mysql idempotent_col"),
                        "user_message",
                        "mysql_idempotent_col",
                        "String",
                        true,
                        null
                )
        );

        assertTrue(migrator.tryMigrate("user_message", operations));
        assertTrue(migrator.tryMigrate("user_message", operations));
        assertTrue(columnNames("user_message").contains("mysql_idempotent_col"));
    }

    @Test
    void testMySqlLikeUpdateColumnTypeDataPreservation() throws Exception {
        assertTrue(migrator.tryMigrate("user_message", List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add mysql column with String type"),
                        "user_message",
                        "mysql_test_column",
                        "String",
                        true,
                        null
                )
        )));

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO user_message "
                             + "(message_id, user_id, session_id, scope_id, role, content, timestamp, mysql_test_column) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, "mysql_test_id");
            statement.setString(2, "user1");
            statement.setString(3, "session1");
            statement.setString(4, "scope1");
            statement.setString(5, "user");
            statement.setString(6, "test message");
            statement.setString(7, "2026-06-02T00:00:00Z");
            statement.setString(8, "test_data");
            statement.executeUpdate();
        }

        assertTrue(migrator.tryMigrate("user_message", List.of(
                new UpdateColumnTypeOperation(
                        new OperationMetadata(2, "Update mysql column to Text type"),
                        "user_message",
                        "mysql_test_column",
                        "Text"
                )
        )));

        assertEquals("test_data", queryString(
                "SELECT mysql_test_column FROM user_message WHERE message_id = ?",
                "mysql_test_id"
        ));
    }

    @Test
    void testMySqlLikeSkipLowerVersionOperations() throws Exception {
        List<BaseOperation> operationsV1 = List.of(
                new AddColumnOperation(
                        new OperationMetadata(1, "Add mysql column v1"),
                        "user_message",
                        "mysql_skip_column_v1",
                        "String",
                        true,
                        null
                )
        );
        List<BaseOperation> operationsV2 = List.of(
                new AddColumnOperation(
                        new OperationMetadata(2, "Add mysql column v2"),
                        "user_message",
                        "mysql_skip_column_v2",
                        "String",
                        true,
                        null
                )
        );

        assertTrue(migrator.tryMigrate("user_message", operationsV1));
        assertTrue(migrator.tryMigrate("user_message", operationsV2));
        assertTrue(migrator.tryMigrate("user_message", operationsV1));

        List<String> columns = columnNames("user_message");
        assertTrue(columns.contains("mysql_skip_column_v1"));
        assertTrue(columns.contains("mysql_skip_column_v2"));
    }

    private List<String> columnNames(String tableName) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            List<String> columns = new ArrayList<>();
            try (ResultSet rs = metaData.getColumns(connection.getCatalog(), connection.getSchema(), tableName.toUpperCase(Locale.ROOT), null)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
            return columns;
        }
    }

    private String columnType(String tableName, String columnName) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getColumns(connection.getCatalog(), connection.getSchema(), tableName.toUpperCase(Locale.ROOT), columnName.toUpperCase(Locale.ROOT))) {
                if (rs.next()) {
                    return rs.getString("TYPE_NAME").toLowerCase(Locale.ROOT);
                }
            }
        }
        return "";
    }

    private void insertUserMessage(String messageId, String content) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO user_message "
                             + "(message_id, user_id, scope_id, content, session_id, role, timestamp) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, messageId);
            statement.setString(2, "user1");
            statement.setString(3, "scope1");
            statement.setString(4, content);
            statement.setString(5, "session1");
            statement.setString(6, "user");
            statement.setString(7, "2026-06-02T00:00:00Z");
            statement.executeUpdate();
        }
    }

    private String queryString(String sql, String key) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    private int queryInt(String sql, String key) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private String schemaVersion(String tableName) throws Exception {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT schema_version FROM memory_meta WHERE table_name = ?")) {
            statement.setString(1, tableName);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString(1);
                    assertNotNull(value);
                    return value;
                }
                return null;
            }
        }
    }

    private static class SimpleDbStore extends BaseDbStore<DataSource> {
        private final DataSource dataSource;

        private SimpleDbStore(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public DataSource getEngine() {
            return dataSource;
        }
    }
}
