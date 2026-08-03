/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.openjiuwen.core.foundation.store.db.DefaultDbStore;
import com.openjiuwen.core.memory.manage.mem_model.DbModelSupport;
import com.openjiuwen.core.memory.manage.mem_model.SqlDbStore;
import com.openjiuwen.core.memory.migration.migrator.SqlMigrator;
import com.openjiuwen.core.memory.migration.operation.AddColumnOperation;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.RenameColumnOperation;
import com.openjiuwen.core.memory.migration.operation.UpdateColumnTypeOperation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Supplemental parity coverage for SQL migrator tests.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/memory/migration/migrator/test_sql_migrator.py}
 * in {@code tests/unit_tests/core/memory/migration/migrator/test_sql_migrator.py}.</p>
 */
class SqlMigratorParityTest {

    private static final List<String> PASSED_PYTHON_TESTS = List.of(
            "TestSQLMigrator::test_try_migrate_empty_operations",
            "TestSQLMigrator::test_add_column_operation_user_message",
            "TestSQLMigrator::test_add_column_operation_scope_user_mapping",
            "TestSQLMigrator::test_add_column_unsupported_table",
            "TestSQLMigrator::test_rename_column_operation",
            "TestSQLMigrator::test_rename_column_unsupported_table",
            "TestSQLMigrator::test_update_column_type_operation_sqlite",
            "TestSQLMigrator::test_update_column_type_unsupported_table",
            "TestSQLMigrator::test_version_control",
            "TestSQLMigrator::test_multiple_operations_in_single_migration",
            "TestSQLMigrator::test_get_sqlalchemy_type_string",
            "TestSQLMigrator::test_get_sqlalchemy_type_string_with_length",
            "TestSQLMigrator::test_get_sqlalchemy_type_integer",
            "TestSQLMigrator::test_get_sqlalchemy_type_datetime",
            "TestSQLMigrator::test_get_sqlalchemy_type_boolean",
            "TestSQLMigrator::test_get_sqlalchemy_type_text",
            "TestSQLMigrator::test_get_sqlalchemy_type_float",
            "TestSQLMigrator::test_get_sqlalchemy_type_unknown",
            "TestSQLMigrator::test_batch_migrate",
            "TestSQLMigrator::test_update_column_type_nonexistent_column",
            "TestSQLMigrator::test_add_column_with_default_value",
            "TestSQLMigrator::test_migration_idempotency",
            "TestSQLMigrator::test_migrate_with_data_preservation",
            "TestSQLMigrator::test_empty_operations_list",
            "TestSQLMigrator::test_batch_migrate_with_empty_operations",
            "TestSQLMigrator::test_add_column_text_type",
            "TestSQLMigrator::test_update_column_type_sqlite_data_preservation",
            "TestSQLMigrator::test_skip_lower_version_operations"
    );

    @TestFactory
    Collection<DynamicTest> pythonSqlMigratorPassedCases() {
        return PASSED_PYTHON_TESTS.stream()
                .map(name -> dynamicTest(name, () -> runPassedPythonCase(name)))
                .toList();
    }

    @Test
    @Disabled("Skipped in Python source: Skipping MySQL tests")
    void mysqlTryMigrateEmptyOperationsSkippedInPython() {
    }

    @Test
    @Disabled("Skipped in Python source: Skipping MySQL tests")
    void mysqlAddColumnUserMessageSkippedInPython() {
    }

    @Test
    @Disabled("Skipped in Python source: Skipping MySQL tests")
    void mysqlAddColumnScopeUserMappingSkippedInPython() {
    }

    @Test
    @Disabled("Skipped in Python source: Skipping MySQL tests")
    void mysqlRenameColumnSkippedInPython() {
    }

    @Test
    @Disabled("Skipped in Python source: Skipping MySQL tests")
    void mysqlUpdateColumnTypeSkippedInPython() {
    }

    @Test
    @Disabled("Skipped in Python source: Skipping MySQL tests")
    void mysqlVersionControlSkippedInPython() {
    }

    @Test
    @Disabled("Skipped in Python source: Skipping MySQL tests")
    void mysqlMultipleOperationsSkippedInPython() {
    }

    @Test
    @Disabled("Skipped in Python source: Skipping MySQL tests")
    void mysqlBatchMigrateSkippedInPython() {
    }

    @Test
    @Disabled("Skipped in Python source: Skipping MySQL tests")
    void mysqlAddColumnWithDefaultSkippedInPython() {
    }

    @Test
    @Disabled("Skipped in Python source: Skipping MySQL tests")
    void mysqlMigrationIdempotencySkippedInPython() {
    }

    @Test
    @Disabled("Skipped in Python source: Skipping MySQL tests")
    void mysqlUpdateTypeDataPreservationSkippedInPython() {
    }

    @Test
    @Disabled("Skipped in Python source: Skipping MySQL tests")
    void mysqlSkipLowerVersionOperationsSkippedInPython() {
    }

    private void runPassedPythonCase(String name) throws Exception {
        if (name.contains("get_sqlalchemy_type")) {
            assertSqlAlchemyTypeSemantics(name);
            return;
        }
        if (name.contains("unsupported_table")) {
            assertUnsupportedTableReturnsFalse(name);
            return;
        }
        if (name.contains("nonexistent_column")) {
            assertUpdateNonexistentColumnReturnsFalse();
            return;
        }
        if (name.contains("batch_migrate_with_empty_operations")) {
            assertBatchMigrateWithEmptyOperations();
            return;
        }
        if (name.contains("batch_migrate")) {
            assertBatchMigrate();
            return;
        }
        if (name.contains("empty_operations")) {
            assertEmptyOperations();
            return;
        }
        if (name.contains("rename_column_operation")) {
            assertRenameColumnOperation();
            return;
        }
        if (name.contains("update_column_type")) {
            assertUpdateColumnType(name);
            return;
        }
        if (name.contains("version_control")) {
            assertVersionControl();
            return;
        }
        if (name.contains("multiple_operations")) {
            assertMultipleOperations();
            return;
        }
        if (name.contains("default_value")) {
            assertAddColumnWithDefaultValue();
            return;
        }
        if (name.contains("idempotency")) {
            assertMigrationIdempotency();
            return;
        }
        if (name.contains("data_preservation")) {
            assertDataPreservation();
            return;
        }
        if (name.contains("add_column_text_type")) {
            assertAddColumnTextType();
            return;
        }
        if (name.contains("skip_lower_version")) {
            assertSkipLowerVersionOperations();
            return;
        }
        assertAddColumnOperation(name);
    }

    private static void assertEmptyOperations() {
        Fixture fixture = newFixture();

        assertThat(fixture.migrator().tryMigrate("test_table", List.of()).join()).isTrue();
    }

    private static void assertAddColumnOperation(String name) throws SQLException {
        Fixture fixture = newFixture();
        String table = name.contains("scope_user_mapping") ? "scope_user_mapping" : "user_message";
        String column = name.contains("scope_user_mapping") ? "new_integer_column" : "new_column";
        BaseOperation operation = new AddColumnOperation(
                metadata(1, "add column"),
                table,
                column,
                name.contains("scope_user_mapping") ? "Integer" : "String",
                !name.contains("scope_user_mapping"),
                name.contains("scope_user_mapping") ? 0 : null
        );

        assertThat(fixture.migrator().tryMigrate(table, List.of(operation)).join()).isTrue();
        assertThat(hasColumn(fixture.dataSource(), table, column)).isTrue();
        assertThat(schemaVersion(fixture.dataSource(), table)).isEqualTo("1");
    }

    private static void assertUnsupportedTableReturnsFalse(String name) {
        Fixture fixture = newFixture();
        BaseOperation operation = name.contains("rename")
                ? new RenameColumnOperation(metadata(1, "rename unsupported"), "unsupported_table", "old_name", "new_name")
                : name.contains("update")
                ? new UpdateColumnTypeOperation(metadata(1, "update unsupported"), "unsupported_table", "test_column", "Text")
                : new AddColumnOperation(metadata(1, "add unsupported"), "unsupported_table", "new_column", "String");

        assertThat(fixture.migrator().tryMigrate("unsupported_table", List.of(operation)).join()).isFalse();
    }

    private static void assertRenameColumnOperation() throws SQLException {
        Fixture fixture = newFixture();

        assertThat(fixture.migrator().tryMigrate("user_message", List.of(
                new AddColumnOperation(metadata(1, "add old column"), "user_message", "old_name", "String"),
                new RenameColumnOperation(metadata(2, "rename old column"), "user_message", "old_name", "new_name")
        )).join()).isTrue();

        assertThat(hasColumn(fixture.dataSource(), "user_message", "old_name")).isFalse();
        assertThat(hasColumn(fixture.dataSource(), "user_message", "new_name")).isTrue();
        assertThat(schemaVersion(fixture.dataSource(), "user_message")).isEqualTo("2");
    }

    private static void assertUpdateColumnType(String name) throws SQLException {
        Fixture fixture = newFixture();

        assertThat(fixture.migrator().tryMigrate("user_message", List.of(
                new AddColumnOperation(metadata(1, "add string column"), "user_message", "test_column", "String"),
                new UpdateColumnTypeOperation(metadata(2, "update text column"), "user_message", "test_column", "Text")
        )).join()).isTrue();

        assertThat(columnType(fixture.dataSource(), "user_message", "test_column").toLowerCase(Locale.ROOT))
                .containsAnyOf("clob", "character");
        if (name.contains("data_preservation")) {
            assertDataPreservation();
        }
    }

    private static void assertVersionControl() throws SQLException {
        Fixture fixture = newFixture();

        assertThat(fixture.migrator().tryMigrate("user_message", List.of(
                new AddColumnOperation(metadata(1, "add column v1"), "user_message", "column_v1", "String"),
                new AddColumnOperation(metadata(2, "add column v2"), "user_message", "column_v2", "String"),
                new AddColumnOperation(metadata(3, "add column v3"), "user_message", "column_v3", "String")
        )).join()).isTrue();
        assertThat(List.of("column_v1", "column_v2", "column_v3"))
                .allSatisfy(column -> assertThat(hasColumn(fixture.dataSource(), "user_message", column)).isTrue());

        assertThat(fixture.migrator().tryMigrate("user_message", List.of(
                new AddColumnOperation(metadata(4, "add column v4"), "user_message", "column_v4", "String")
        )).join()).isTrue();
        assertThat(schemaVersion(fixture.dataSource(), "user_message")).isEqualTo("4");
    }

    private static void assertMultipleOperations() throws SQLException {
        Fixture fixture = newFixture();

        assertThat(fixture.migrator().tryMigrate("user_message", List.of(
                new AddColumnOperation(metadata(1, "add col1"), "user_message", "col1", "String"),
                new AddColumnOperation(metadata(2, "add col2"), "user_message", "col2", "Integer"),
                new RenameColumnOperation(metadata(3, "rename col1"), "user_message", "col1", "col1_renamed")
        )).join()).isTrue();

        assertThat(hasColumn(fixture.dataSource(), "user_message", "col1_renamed")).isTrue();
        assertThat(hasColumn(fixture.dataSource(), "user_message", "col2")).isTrue();
        assertThat(hasColumn(fixture.dataSource(), "user_message", "col1")).isFalse();
    }

    private static void assertSqlAlchemyTypeSemantics(String name) {
        if (name.contains("string_with_length")) {
            assertThat(SqlMigrator.getSqlalchemyType("String(100)")).isEqualTo("VARCHAR(100)");
            assertThat(SqlMigrator.getSqlalchemyType("VARCHAR(255)")).isEqualTo("VARCHAR(255)");
            return;
        }
        if (name.contains("string")) {
            assertThat(SqlMigrator.getSqlalchemyType("String")).isEqualTo("VARCHAR(255)");
            assertThat(SqlMigrator.getSqlalchemyType("VARCHAR")).isEqualTo("VARCHAR(255)");
            return;
        }
        if (name.contains("integer")) {
            assertThat(SqlMigrator.getSqlalchemyType("Integer")).isEqualTo("INTEGER");
            assertThat(SqlMigrator.getSqlalchemyType("INT")).isEqualTo("INTEGER");
            return;
        }
        if (name.contains("datetime")) {
            assertThat(SqlMigrator.getSqlalchemyType("DateTime")).isEqualTo("TIMESTAMP");
            assertThat(SqlMigrator.getSqlalchemyType("DATETIME")).isEqualTo("TIMESTAMP");
            return;
        }
        if (name.contains("boolean")) {
            assertThat(SqlMigrator.getSqlalchemyType("Boolean")).isEqualTo("BOOLEAN");
            assertThat(SqlMigrator.getSqlalchemyType("BOOL")).isEqualTo("BOOLEAN");
            return;
        }
        if (name.contains("text")) {
            assertThat(SqlMigrator.getSqlalchemyType("Text")).isEqualTo("CLOB");
            assertThat(SqlMigrator.getSqlalchemyType("TEXT")).isEqualTo("CLOB");
            return;
        }
        if (name.contains("float")) {
            assertThat(SqlMigrator.getSqlalchemyType("Float")).isEqualTo("DOUBLE");
            assertThat(SqlMigrator.getSqlalchemyType("FLOAT")).isEqualTo("DOUBLE");
            return;
        }
        assertThat(SqlMigrator.getSqlalchemyType("UnknownType")).isEqualTo("CLOB");
    }

    private static void assertBatchMigrate() {
        Fixture fixture = newFixture();

        Map<String, Boolean> results = fixture.migrator().batchMigrate(List.of(
                Map.of("table_name", "user_message", "operations", List.of(
                        new AddColumnOperation(metadata(1, "add batch col1"), "user_message", "batch_col1", "String")
                )),
                Map.of("table_name", "scope_user_mapping", "operations", List.of(
                        new AddColumnOperation(metadata(1, "add batch col2"), "scope_user_mapping", "batch_col2", "Integer")
                ))
        )).join();

        assertThat(results).containsEntry("user_message", true).containsEntry("scope_user_mapping", true);
    }

    private static void assertBatchMigrateWithEmptyOperations() {
        Fixture fixture = newFixture();

        Map<String, Boolean> results = fixture.migrator().batchMigrate(List.of(
                Map.of("table_name", "user_message", "operations", List.of()),
                Map.of("table_name", "scope_user_mapping", "operations", List.of())
        )).join();

        assertThat(results).containsEntry("user_message", true).containsEntry("scope_user_mapping", true);
    }

    private static void assertUpdateNonexistentColumnReturnsFalse() {
        Fixture fixture = newFixture();

        assertThat(fixture.migrator().tryMigrate("user_message", List.of(
                new UpdateColumnTypeOperation(metadata(1, "missing column"), "user_message", "nonexistent_column", "Text")
        )).join()).isFalse();
    }

    private static void assertAddColumnWithDefaultValue() throws SQLException {
        Fixture fixture = newFixture();

        assertThat(fixture.migrator().tryMigrate("user_message", List.of(
                new AddColumnOperation(metadata(1, "add default column"), "user_message", "default_col", "Integer", false, 42)
        )).join()).isTrue();
        insertUserMessage(fixture.dataSource(), "message-default", null);

        assertThat(singleValue(fixture.dataSource(), "SELECT default_col FROM user_message WHERE message_id = 'message-default'"))
                .isEqualTo(42);
    }

    private static void assertMigrationIdempotency() throws SQLException {
        Fixture fixture = newFixture();
        List<BaseOperation> operations = List.of(
                new AddColumnOperation(metadata(1, "add idempotent column"), "user_message", "idempotent_col", "String")
        );

        assertThat(fixture.migrator().tryMigrate("user_message", operations).join()).isTrue();
        assertThat(fixture.migrator().tryMigrate("user_message", operations).join()).isTrue();
        assertThat(hasColumn(fixture.dataSource(), "user_message", "idempotent_col")).isTrue();
    }

    private static void assertDataPreservation() throws SQLException {
        Fixture fixture = newFixture();

        assertThat(fixture.migrator().tryMigrate("user_message", List.of(
                new AddColumnOperation(metadata(1, "add test column"), "user_message", "test_column", "String")
        )).join()).isTrue();
        insertUserMessage(fixture.dataSource(), "message-data", "test_data");
        assertThat(fixture.migrator().tryMigrate("user_message", List.of(
                new UpdateColumnTypeOperation(metadata(2, "update test column"), "user_message", "test_column", "Text")
        )).join()).isTrue();

        assertThat(singleValue(fixture.dataSource(), "SELECT test_column FROM user_message WHERE message_id = 'message-data'"))
                .isEqualTo("test_data");
    }

    private static void assertAddColumnTextType() throws SQLException {
        Fixture fixture = newFixture();

        assertThat(fixture.migrator().tryMigrate("user_message", List.of(
                new AddColumnOperation(metadata(1, "add text column"), "user_message", "text_col", "Text")
        )).join()).isTrue();

        assertThat(columnType(fixture.dataSource(), "user_message", "text_col").toLowerCase(Locale.ROOT))
                .containsAnyOf("clob", "character");
    }

    private static void assertSkipLowerVersionOperations() throws SQLException {
        Fixture fixture = newFixture();
        List<BaseOperation> operationsV1 = List.of(
                new AddColumnOperation(metadata(1, "add column v1"), "user_message", "column_v1", "String")
        );

        assertThat(fixture.migrator().tryMigrate("user_message", operationsV1).join()).isTrue();
        assertThat(fixture.migrator().tryMigrate("user_message", List.of(
                new AddColumnOperation(metadata(2, "add column v2"), "user_message", "column_v2", "String")
        )).join()).isTrue();
        assertThat(fixture.migrator().tryMigrate("user_message", operationsV1).join()).isTrue();
        assertThat(hasColumn(fixture.dataSource(), "user_message", "column_v1")).isTrue();
        assertThat(hasColumn(fixture.dataSource(), "user_message", "column_v2")).isTrue();
        assertThat(schemaVersion(fixture.dataSource(), "user_message")).isEqualTo("2");
    }

    private static OperationMetadata metadata(int schemaVersion, String description) {
        return new OperationMetadata(schemaVersion, description);
    }

    private static Fixture newFixture() {
        String dbName = "sql_migrator_parity_" + UUID.randomUUID().toString().replace("-", "");
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        DefaultDbStore<JdbcDataSource> dbStore = new DefaultDbStore<>(dataSource);
        DbModelSupport.createTables(dbStore).join();
        SqlDbStore sqlDbStore = new SqlDbStore(dbStore);
        return new Fixture(dataSource, new SqlMigrator(sqlDbStore));
    }

    private static boolean hasColumn(JdbcDataSource dataSource, String tableName, String columnName) throws SQLException {
        return columnType(dataSource, tableName, columnName) != null;
    }

    private static String columnType(JdbcDataSource dataSource, String tableName, String columnName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             ResultSet resultSet = connection.getMetaData()
                     .getColumns(connection.getCatalog(), null, tableName, null)) {
            while (resultSet.next()) {
                String currentName = resultSet.getString("COLUMN_NAME");
                if (columnName.equalsIgnoreCase(currentName)) {
                    return resultSet.getString("TYPE_NAME");
                }
            }
        }
        return null;
    }

    private static String schemaVersion(JdbcDataSource dataSource, String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT schema_version FROM memory_meta WHERE table_name = ?"
             )) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }

    private static void insertUserMessage(JdbcDataSource dataSource, String messageId, String testColumnValue)
            throws SQLException {
        String sql = testColumnValue == null
                ? """
                INSERT INTO user_message (message_id, user_id, session_id, scope_id, role, content, timestamp)
                VALUES (?, 'user1', 'session1', 'scope1', 'user', 'test message', '2026-06-20T00:00:00Z')
                """
                : """
                INSERT INTO user_message (message_id, user_id, session_id, scope_id, role, content, timestamp, test_column)
                VALUES (?, 'user1', 'session1', 'scope1', 'user', 'test message', '2026-06-20T00:00:00Z', ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, messageId);
            if (testColumnValue != null) {
                statement.setString(2, testColumnValue);
            }
            statement.executeUpdate();
        }
    }

    private static Object singleValue(JdbcDataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                return null;
            }
            Object value = resultSet.getObject(1);
            if (value instanceof java.sql.Clob clob) {
                return clob.getSubString(1, Math.toIntExact(clob.length()));
            }
            return value;
        }
    }

    private record Fixture(JdbcDataSource dataSource, SqlMigrator migrator) {
    }
}
