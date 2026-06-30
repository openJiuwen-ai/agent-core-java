/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration;

import com.openjiuwen.core.memory.common.KvPrefixRegistry;
import com.openjiuwen.core.memory.manage.mem_model.DbModel;
import com.openjiuwen.core.memory.manage.mem_model.SemanticStore;
import com.openjiuwen.core.memory.manage.mem_model.SqlDbStore;
import com.openjiuwen.core.memory.migration.migrator.KvMigrator;
import com.openjiuwen.core.memory.migration.operation.AddColumnOperation;
import com.openjiuwen.core.memory.migration.operation.AddScalarFieldOperation;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.UpdateKVOperation;
import com.openjiuwen.core.memory.support.TestDbStore;
import com.openjiuwen.core.memory.support.TestInMemoryKVStore;
import com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunMigrationsTest {

    private Map<String, List<BaseOperation>> sqlBackup;
    private Map<String, List<BaseOperation>> vectorBackup;
    private Map<String, List<BaseOperation>> kvBackup;

    @BeforeEach
    void backupRegistry() {
        sqlBackup = copyOperations(MigrationPlan.getSqlRegistry().getAllOperations());
        vectorBackup = copyOperations(MigrationPlan.getVectorRegistry().getAllOperations());
        kvBackup = copyOperations(MigrationPlan.getKvRegistry().getAllOperations());
        MigrationPlan.getSqlRegistry().clear();
        MigrationPlan.getVectorRegistry().clear();
        MigrationPlan.getKvRegistry().clear();
        KvPrefixRegistry.getInstance().registerCurrent("user_message");
    }

    @AfterEach
    void restoreRegistry() {
        MigrationPlan.getSqlRegistry().clear();
        MigrationPlan.getSqlRegistry().setOperations(sqlBackup);
        MigrationPlan.getVectorRegistry().clear();
        MigrationPlan.getVectorRegistry().setOperations(vectorBackup);
        MigrationPlan.getKvRegistry().clear();
        MigrationPlan.getKvRegistry().setOperations(kvBackup);
        KvPrefixRegistry.getInstance().unregister("user_message");
    }

    @Test
    void runSqlMigrationsHandlesEmptyRegistry() {
        TestDbStore dbStore = new TestDbStore(createDataSource());
        DbModel.createTables(dbStore);

        assertTrue(RunMigrations.runSqlMigrations(new SqlDbStore(dbStore)));
    }

    @Test
    void runSqlMigrationsAppliesRegisteredOperations() throws Exception {
        TestDbStore dbStore = new TestDbStore(createDataSource());
        DbModel.createTables(dbStore);
        MigrationPlan.getSqlRegistry().register(DbModel.USER_MESSAGE_TABLE, new AddColumnOperation(
                new OperationMetadata(2, "add runner column"),
                DbModel.USER_MESSAGE_TABLE,
                "runner_source",
                "STRING",
                true,
                null));

        assertTrue(RunMigrations.runSqlMigrations(new SqlDbStore(dbStore)));
        assertTrue(columnExists(dbStore.getEngine(), DbModel.USER_MESSAGE_TABLE, "runner_source"));
        assertEquals("2", readSchemaVersion(dbStore.getEngine(), DbModel.USER_MESSAGE_TABLE));
    }

    @Test
    void runSqlMigrationsReturnsFalseOnFailedEntity() {
        TestDbStore dbStore = new TestDbStore(createDataSource());
        DbModel.createTables(dbStore);
        MigrationPlan.getSqlRegistry().register("bad_table", new AddColumnOperation(
                new OperationMetadata(1, "bad table"),
                "bad_table",
                "source",
                "TEXT",
                true,
                null));

        assertFalse(RunMigrations.runSqlMigrations(new SqlDbStore(dbStore)));
    }

    @Test
    void runKvMigrationsAppliesRegisteredOperations() {
        TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
        kvStore.set("user_message:1", "old");
        MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY, new UpdateKVOperation(
                new OperationMetadata(3, "update kv"),
                store -> store.set("user_message:1", "new")));

        assertTrue(RunMigrations.runKvMigrations(kvStore));
        assertEquals("new", kvStore.get("user_message:1"));
        assertEquals("3", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void runVectorMigrationsAppliesRegisteredOperations() {
        InMemoryVectorStore vectorStore = new InMemoryVectorStore("vector_user_profile");
        SemanticStore semanticStore = new SemanticStore(vectorStore);
        String collectionName = "runner_scope_user_profile";
        semanticStore.createCollection(collectionName, 3, Map.of());
        semanticStore.updateCollectionMetadata(collectionName, Map.of("schema_version", 0));
        vectorStore.withCollection(collectionName).add(List.of(Map.of(
                "id", "1",
                "text", "hello",
                "vector", List.of(1.0f, 2.0f, 3.0f)
        )), null, Map.of());
        MigrationPlan.getVectorRegistry().register("vector_user_profile", new AddScalarFieldOperation(
                new OperationMetadata(2, "add runner field"),
                "user_profile",
                "runner_field",
                "string",
                "value"));

        assertTrue(RunMigrations.runVectorMigrations(semanticStore));
        assertEquals(2, semanticStore.getCollectionMetadata(collectionName).get("schema_version"));
        assertEquals("value", vectorStore.withCollection(collectionName)
                .queryByFilters(Map.of("runner_field", "value"), 10)
                .get(0)
                .getMetadata()
                .get("runner_field"));
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

    private static DataSource createDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");
        return dataSource;
    }

    private static Map<String, List<BaseOperation>> copyOperations(Map<String, List<BaseOperation>> source) {
        Map<String, List<BaseOperation>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, new ArrayList<>(value)));
        return copy;
    }
}
