/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration;

import com.openjiuwen.core.foundation.store.BaseDbStore;
import com.openjiuwen.core.memory.manage.mem_model.DbModel;
import com.openjiuwen.core.memory.manage.mem_model.DbModelSupport;
import com.openjiuwen.core.memory.manage.mem_model.SqlDbStore;
import com.openjiuwen.core.memory.migration.migrator.SqlMigrator;
import com.openjiuwen.core.memory.migration.operation.AddColumnOperation;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.RenameColumnOperation;
import com.openjiuwen.core.memory.migration.operation.UpdateColumnTypeOperation;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlMigratorTest {

    @Test
    void tryMigrateAddsColumnAndUpdatesMetaVersion() throws Exception {
        BaseDbStore<DataSource> dbStore = createDbStore(createDataSource());
        DbModelSupport.createTables(dbStore).join();

        SqlMigrator migrator = new SqlMigrator(new SqlDbStore(dbStore));
        BaseOperation operation = new AddColumnOperation(
                new OperationMetadata(2, "add source column"),
                DbModel.USER_MESSAGE_TABLE,
                "source",
                "STRING",
                true,
                "manual");

        assertTrue(migrator.tryMigrate(DbModel.USER_MESSAGE_TABLE, List.of(operation)).join());
        assertTrue(columnExists(dbStore.getAsyncEngine(), DbModel.USER_MESSAGE_TABLE, "source"));
        assertEquals("2", readSchemaVersion(dbStore.getAsyncEngine(), DbModel.USER_MESSAGE_TABLE));
    }

    @Test
    void tryMigrateSkipsLowerVersionOperations() throws Exception {
        BaseDbStore<DataSource> dbStore = createDbStore(createDataSource());
        DbModelSupport.createTables(dbStore).join();
        insertMeta(dbStore.getAsyncEngine(), DbModel.USER_MESSAGE_TABLE, "5");

        SqlMigrator migrator = new SqlMigrator(new SqlDbStore(dbStore));
        BaseOperation operation = new AddColumnOperation(
                new OperationMetadata(2, "old source column"),
                DbModel.USER_MESSAGE_TABLE,
                "old_source",
                "TEXT",
                true,
                null);

        assertTrue(migrator.tryMigrate(DbModel.USER_MESSAGE_TABLE, List.of(operation)).join());
        assertFalse(columnExists(dbStore.getAsyncEngine(), DbModel.USER_MESSAGE_TABLE, "old_source"));
        assertEquals("5", readSchemaVersion(dbStore.getAsyncEngine(), DbModel.USER_MESSAGE_TABLE));
    }

    @Test
    void tryMigrateRenamesColumn() throws Exception {
        BaseDbStore<DataSource> dbStore = createDbStore(createDataSource());
        DbModelSupport.createTables(dbStore).join();
        try (Connection connection = dbStore.getAsyncEngine().getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE user_message ADD COLUMN legacy_source VARCHAR(255)");
        }

        SqlMigrator migrator = new SqlMigrator(new SqlDbStore(dbStore));
        BaseOperation operation = new RenameColumnOperation(
                new OperationMetadata(3, "rename source column"),
                DbModel.USER_MESSAGE_TABLE,
                "legacy_source",
                "source");

        assertTrue(migrator.tryMigrate(DbModel.USER_MESSAGE_TABLE, List.of(operation)).join());
        assertFalse(columnExists(dbStore.getAsyncEngine(), DbModel.USER_MESSAGE_TABLE, "legacy_source"));
        assertTrue(columnExists(dbStore.getAsyncEngine(), DbModel.USER_MESSAGE_TABLE, "source"));
        assertEquals("3", readSchemaVersion(dbStore.getAsyncEngine(), DbModel.USER_MESSAGE_TABLE));
    }

    @Test
    void tryMigrateRejectsUnsupportedTable() {
        BaseDbStore<DataSource> dbStore = createDbStore(createDataSource());
        DbModelSupport.createTables(dbStore).join();

        SqlMigrator migrator = new SqlMigrator(new SqlDbStore(dbStore));
        BaseOperation operation = new AddColumnOperation(
                new OperationMetadata(1, "bad table"),
                "not_memory_table",
                "source",
                "TEXT",
                true,
                null);

        assertFalse(migrator.tryMigrate("not_memory_table", List.of(operation)).join());
        assertThrows(Exception.class, () -> {
            migrator.tryMigrate("not_memory_table", List.of(new AddColumnOperation(
                    new OperationMetadata(1, "bad table"),
                    "not_memory_table",
                    "source",
                    "TEXT",
                    true,
                    null))).join();
        });
    }

    @Test
    void batchMigrateReturnsPerTableResults() throws Exception {
        BaseDbStore<DataSource> dbStore = createDbStore(createDataSource());
        DbModelSupport.createTables(dbStore).join();

        SqlMigrator migrator = new SqlMigrator(new SqlDbStore(dbStore));
        BaseOperation userMessageOperation = new AddColumnOperation(
                new OperationMetadata(2, "add source column"),
                DbModel.USER_MESSAGE_TABLE,
                "source",
                "VARCHAR(128)",
                true,
                null);
        BaseOperation scopeMappingOperation = new AddColumnOperation(
                new OperationMetadata(4, "add label column"),
                DbModel.SCOPE_USER_MAPPING_TABLE,
                "label",
                "TEXT",
                true,
                null);

        Map<String, Boolean> results = migrator.batchMigrate(List.of(
                Map.of("table_name", DbModel.USER_MESSAGE_TABLE, "operations", List.of(userMessageOperation)),
                Map.of("table_name", DbModel.SCOPE_USER_MAPPING_TABLE, "operations", List.of(scopeMappingOperation))
        )).join();

        assertEquals(Boolean.TRUE, results.get(DbModel.USER_MESSAGE_TABLE));
        assertEquals(Boolean.TRUE, results.get(DbModel.SCOPE_USER_MAPPING_TABLE));
        assertTrue(columnExists(dbStore.getAsyncEngine(), DbModel.USER_MESSAGE_TABLE, "source"));
        assertTrue(columnExists(dbStore.getAsyncEngine(), DbModel.SCOPE_USER_MAPPING_TABLE, "label"));
        assertEquals("2", readSchemaVersion(dbStore.getAsyncEngine(), DbModel.USER_MESSAGE_TABLE));
        assertEquals("4", readSchemaVersion(dbStore.getAsyncEngine(), DbModel.SCOPE_USER_MAPPING_TABLE));
    }

    @Test
    void batchMigrateHandlesEmptyOperations() throws Exception {
        BaseDbStore<DataSource> dbStore = createDbStore(createDataSource());
        DbModelSupport.createTables(dbStore).join();

        SqlMigrator migrator = new SqlMigrator(new SqlDbStore(dbStore));
        Map<String, Boolean> results = migrator.batchMigrate(List.of(
                Map.of("table_name", DbModel.USER_MESSAGE_TABLE, "operations", List.of())
        )).join();

        assertEquals(Boolean.TRUE, results.get(DbModel.USER_MESSAGE_TABLE));
    }

    @Test
    void tryMigrateIsIdempotentForAlreadyAppliedVersion() throws Exception {
        BaseDbStore<DataSource> dbStore = createDbStore(createDataSource());
        DbModelSupport.createTables(dbStore).join();

        SqlMigrator migrator = new SqlMigrator(new SqlDbStore(dbStore));
        BaseOperation operation = new AddColumnOperation(
                new OperationMetadata(1, "add idempotent column"),
                DbModel.USER_MESSAGE_TABLE,
                "idempotent_col",
                "STRING",
                true,
                null);

        assertTrue(migrator.tryMigrate(DbModel.USER_MESSAGE_TABLE, List.of(operation)).join());
        assertTrue(migrator.tryMigrate(DbModel.USER_MESSAGE_TABLE, List.of(operation)).join());
        assertTrue(columnExists(dbStore.getAsyncEngine(), DbModel.USER_MESSAGE_TABLE, "idempotent_col"));
        assertEquals("1", readSchemaVersion(dbStore.getAsyncEngine(), DbModel.USER_MESSAGE_TABLE));
    }

    @Test
    void tryMigrateAddsColumnWithDefaultValue() throws Exception {
        BaseDbStore<DataSource> dbStore = createDbStore(createDataSource());
        DbModelSupport.createTables(dbStore).join();

        SqlMigrator migrator = new SqlMigrator(new SqlDbStore(dbStore));
        BaseOperation operation = new AddColumnOperation(
                new OperationMetadata(1, "add default column"),
                DbModel.USER_MESSAGE_TABLE,
                "default_col",
                "INTEGER",
                false,
                42);

        assertTrue(migrator.tryMigrate(DbModel.USER_MESSAGE_TABLE, List.of(operation)).join());
        assertTrue(columnExists(dbStore.getAsyncEngine(), DbModel.USER_MESSAGE_TABLE, "default_col"));
        assertEquals("1", readSchemaVersion(dbStore.getAsyncEngine(), DbModel.USER_MESSAGE_TABLE));
    }

    @Test
    void tryMigrateReturnsFalseWhenUpdatingNonexistentColumn() throws Exception {
        BaseDbStore<DataSource> dbStore = createDbStore(createDataSource());
        DbModelSupport.createTables(dbStore).join();

        SqlMigrator migrator = new SqlMigrator(new SqlDbStore(dbStore));
        BaseOperation operation = new UpdateColumnTypeOperation(
                new OperationMetadata(1, "update missing column"),
                DbModel.USER_MESSAGE_TABLE,
                "nonexistent_column",
                "TEXT");

        assertFalse(migrator.tryMigrate(DbModel.USER_MESSAGE_TABLE, List.of(operation)).join());
    }

    @Test
    void tryMigratePreservesDataWhenAddingAndUpdatingColumnType() throws Exception {
        BaseDbStore<DataSource> dbStore = createDbStore(createDataSource());
        DbModelSupport.createTables(dbStore).join();
        try (Connection connection = dbStore.getAsyncEngine().getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO user_message "
                    + "(message_id, user_id, scope_id, content, session_id, role, timestamp) "
                    + "VALUES ('test_msg_1', 'user1', 'scope1', 'test content', 'session1', 'user', '2026-05-11')");
        }

        SqlMigrator migrator = new SqlMigrator(new SqlDbStore(dbStore));
        BaseOperation addColumn = new AddColumnOperation(
                new OperationMetadata(1, "add test column"),
                DbModel.USER_MESSAGE_TABLE,
                "test_col",
                "STRING",
                true,
                null);
        BaseOperation updateColumnType = new UpdateColumnTypeOperation(
                new OperationMetadata(2, "update test column type"),
                DbModel.USER_MESSAGE_TABLE,
                "test_col",
                "TEXT");

        assertTrue(migrator.tryMigrate(DbModel.USER_MESSAGE_TABLE, List.of(addColumn, updateColumnType)).join());
        assertEquals("test content", readStringCell(dbStore.getAsyncEngine(), "user_message", "content", "test_msg_1"));
        assertEquals("2", readSchemaVersion(dbStore.getAsyncEngine(), DbModel.USER_MESSAGE_TABLE));
    }

    @Test
    void tryMigratePreservesUpdatedColumnDataDuringTypeChange() throws Exception {
        BaseDbStore<DataSource> dbStore = createDbStore(createDataSource());
        DbModelSupport.createTables(dbStore).join();

        SqlMigrator migrator = new SqlMigrator(new SqlDbStore(dbStore));
        BaseOperation addColumn = new AddColumnOperation(
                new OperationMetadata(1, "add sqlite data column"),
                DbModel.USER_MESSAGE_TABLE,
                "test_column",
                "STRING",
                true,
                null);

        assertTrue(migrator.tryMigrate(DbModel.USER_MESSAGE_TABLE, List.of(addColumn)).join());

        try (Connection connection = dbStore.getAsyncEngine().getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO user_message "
                    + "(message_id, user_id, scope_id, content, session_id, role, timestamp, test_column) "
                    + "VALUES ('test_msg_1', 'user1', 'scope1', 'test content', 'session1', 'user', "
                    + "'2026-05-11', 'test_data')");
        }

        BaseOperation updateColumnType = new UpdateColumnTypeOperation(
                new OperationMetadata(2, "update sqlite data column type"),
                DbModel.USER_MESSAGE_TABLE,
                "test_column",
                "TEXT");

        assertTrue(migrator.tryMigrate(DbModel.USER_MESSAGE_TABLE, List.of(updateColumnType)).join());
        assertEquals("test_data", readStringCell(dbStore.getAsyncEngine(), "user_message", "test_column", "test_msg_1"));
        assertEquals("2", readSchemaVersion(dbStore.getAsyncEngine(), DbModel.USER_MESSAGE_TABLE));
    }

    @Test
    void tryMigrateUsesRealSqliteTableRebuildForColumnTypeChange() throws Exception {
        BaseDbStore<DataSource> dbStore = createDbStore(createSqliteDataSource());
        DbModelSupport.createTables(dbStore).join();
        try (Connection connection = dbStore.getAsyncEngine().getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE user_message ADD COLUMN sqlite_payload VARCHAR(255) DEFAULT 'legacy'");
            statement.executeUpdate("INSERT INTO user_message "
                    + "(message_id, user_id, scope_id, content, session_id, role, timestamp, sqlite_payload) "
                    + "VALUES ('sqlite_msg_1', 'user1', 'scope1', 'test content', 'session1', 'user', "
                    + "'2026-05-11', 'test_data')");
        }

        SqlMigrator migrator = new SqlMigrator(new SqlDbStore(dbStore));
        BaseOperation updateColumnType = new UpdateColumnTypeOperation(
                new OperationMetadata(2, "update sqlite payload column type"),
                DbModel.USER_MESSAGE_TABLE,
                "sqlite_payload",
                "TEXT");

        assertTrue(migrator.tryMigrate(DbModel.USER_MESSAGE_TABLE, List.of(updateColumnType)).join());
        assertEquals("test_data", readStringCell(dbStore.getAsyncEngine(), "user_message", "sqlite_payload", "sqlite_msg_1"));
        assertEquals("'legacy'", readColumnDefault(dbStore.getAsyncEngine(), "user_message", "sqlite_payload"));
        assertEquals("2", readSchemaVersion(dbStore.getAsyncEngine(), DbModel.USER_MESSAGE_TABLE));
    }

    @Test
    void tryMigrateUsesMysqlChangeSyntaxForRenameAndModifyColumn() throws Exception {
        BaseDbStore<DataSource> dbStore = createDbStore(createDataSource());
        DbModelSupport.createTables(dbStore).join();
        try (Connection connection = dbStore.getAsyncEngine().getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE user_message ADD COLUMN mysql_payload VARCHAR(255) DEFAULT 'legacy'");
            statement.executeUpdate("INSERT INTO user_message "
                    + "(message_id, user_id, scope_id, content, session_id, role, timestamp, mysql_payload) "
                    + "VALUES ('mysql_msg_1', 'user1', 'scope1', 'test content', 'session1', 'user', "
                    + "'2026-05-11', 'test_data')");
        }

        SqlMigrator migrator = new SqlMigrator(new SqlDbStore(dbStore));
        BaseOperation renameColumn = new RenameColumnOperation(
                new OperationMetadata(1, "rename mysql payload column"),
                DbModel.USER_MESSAGE_TABLE,
                "mysql_payload",
                "mysql_payload_renamed");
        BaseOperation updateColumnType = new UpdateColumnTypeOperation(
                new OperationMetadata(2, "update mysql payload column type"),
                DbModel.USER_MESSAGE_TABLE,
                "mysql_payload_renamed",
                "TEXT");

        assertTrue(migrator.tryMigrate(DbModel.USER_MESSAGE_TABLE, List.of(renameColumn, updateColumnType)).join());
        assertFalse(columnExists(dbStore.getAsyncEngine(), DbModel.USER_MESSAGE_TABLE, "mysql_payload"));
        assertTrue(columnExists(dbStore.getAsyncEngine(), DbModel.USER_MESSAGE_TABLE, "mysql_payload_renamed"));
        assertEquals(
                "test_data",
                readStringCell(dbStore.getAsyncEngine(), "user_message", "mysql_payload_renamed", "mysql_msg_1"));
        assertEquals("2", readSchemaVersion(dbStore.getAsyncEngine(), DbModel.USER_MESSAGE_TABLE));
    }

    private static void insertMeta(DataSource dataSource, String tableName, String schemaVersion) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("MERGE INTO memory_meta (table_name, schema_version) KEY(table_name) VALUES ('"
                    + tableName + "', '" + schemaVersion + "')");
        }
    }

    private static String readSchemaVersion(DataSource dataSource, String tableName) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT schema_version FROM memory_meta WHERE table_name = '" + tableName + "'")) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private static boolean columnExists(DataSource dataSource, String tableName, String columnName) throws Exception {
        try (Connection connection = dataSource.getConnection();
             ResultSet resultSet = connection.getMetaData().getColumns(
                     null,
                     null,
                     tableName.toUpperCase(),
                     columnName.toUpperCase())) {
            return resultSet.next();
        }
    }

    private static String readStringCell(DataSource dataSource, String tableName, String columnName, String messageId)
            throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT " + columnName + " FROM " + tableName + " WHERE message_id = '" + messageId + "'")) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private static String readColumnDefault(DataSource dataSource, String tableName, String columnName)
            throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet resultSet = metaData.getColumns(null, null, tableName, columnName)) {
                return resultSet.next() ? resultSet.getString("COLUMN_DEF") : null;
            }
        }
    }

    private static DataSource createDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");
        return dataSource;
    }

    private static DataSource createSqliteDataSource() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:target/sqlite-migration-" + UUID.randomUUID() + ".db");
        return dataSource;
    }

    private static BaseDbStore<DataSource> createDbStore(DataSource ds) {
        return new BaseDbStore<>() {
            @Override
            public DataSource getAsyncEngine() {
                return ds;
            }
        };
    }
}
