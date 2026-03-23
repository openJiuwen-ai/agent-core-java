package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.memory.migration.MigrationPlan;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.support.TestDbStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbModelTest {

    private Map<String, List<BaseOperation>> originalSqlOperations;

    @BeforeEach
    void backupRegistry() {
        originalSqlOperations = copyOperations(MigrationPlan.getSqlRegistry().getAllOperations());
        MigrationPlan.getSqlRegistry().clear();
    }

    @AfterEach
    void restoreRegistry() {
        MigrationPlan.getSqlRegistry().clear();
        MigrationPlan.getSqlRegistry().setOperations(originalSqlOperations);
    }

    @Test
    void createTablesCreatesAllRequiredTables() throws Exception {
        TestDbStore dbStore = new TestDbStore(createDataSource());

        DbModel.createTables(dbStore);

        try (Connection connection = dbStore.getEngine().getConnection();
             Statement statement = connection.createStatement()) {
            ResultSet resultSet = statement.executeQuery(
                    "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'"
            );
            List<String> tableNames = new ArrayList<>();
            while (resultSet.next()) {
                tableNames.add(resultSet.getString(1).toLowerCase());
            }

            assertTrue(tableNames.contains("memory_meta"));
            assertTrue(tableNames.contains("user_message"));
            assertTrue(tableNames.contains("scope_user_mapping"));
        }
    }

    @Test
    void createTablesWritesRegistrySchemaVersions() throws Exception {
        MigrationPlan.getSqlRegistry().register("user_messages", new TestOperation(5, "user message v5"));
        MigrationPlan.getSqlRegistry().register("scope_user_mapping", new TestOperation(3, "scope mapping v3"));

        TestDbStore dbStore = new TestDbStore(createDataSource());
        DbModel.createTables(dbStore);

        Map<String, String> meta = readMemoryMeta(dbStore.getEngine());
        assertEquals("5", meta.get("user_message"));
        assertEquals("3", meta.get("scope_user_mapping"));
    }

    @Test
    void createTablesDoesNotOverwriteExistingMeta() throws Exception {
        TestDbStore dbStore = new TestDbStore(createDataSource());
        DbModel.createTables(dbStore);

        try (Connection connection = dbStore.getEngine().getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM memory_meta");
            statement.executeUpdate("INSERT INTO memory_meta (table_name, schema_version) VALUES ('user_message', '1')");
            statement.executeUpdate("INSERT INTO memory_meta (table_name, schema_version) VALUES ('scope_user_mapping', '2')");
        }

        MigrationPlan.getSqlRegistry().register("user_messages", new TestOperation(10, "user message v10"));
        DbModel.createTables(dbStore);

        Map<String, String> meta = readMemoryMeta(dbStore.getEngine());
        assertEquals("1", meta.get("user_message"));
        assertEquals("2", meta.get("scope_user_mapping"));
    }

    private static Map<String, String> readMemoryMeta(DataSource dataSource) throws Exception {
        Map<String, String> meta = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT table_name, schema_version FROM memory_meta")) {
            while (resultSet.next()) {
                meta.put(resultSet.getString(1), resultSet.getString(2));
            }
        }
        return meta;
    }

    private static DataSource createDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");
        return dataSource;
    }

    private static final class TestOperation extends BaseOperation {
        private TestOperation(int schemaVersion, String description) {
            super(new OperationMetadata(schemaVersion, description));
        }
    }

    private static Map<String, List<BaseOperation>> copyOperations(Map<String, List<BaseOperation>> source) {
        Map<String, List<BaseOperation>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, new ArrayList<>(value)));
        return copy;
    }
}
