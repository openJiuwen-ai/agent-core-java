/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration;

import com.openjiuwen.core.memory.common.KvPrefixRegistry;
import com.openjiuwen.core.memory.manage.mem_model.DbModel;
import com.openjiuwen.core.memory.manage.mem_model.SqlDbStore;
import com.openjiuwen.core.memory.migration.migrator.KvMigrator;
import com.openjiuwen.core.memory.migration.operation.AddColumnOperation;
import com.openjiuwen.core.memory.migration.operation.AddScalarFieldOperation;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.UpdateKVOperation;
import com.openjiuwen.spi.store.BaseDbStore;
import com.openjiuwen.core.foundation.store.BaseKVStore;
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
        DataSource dataSource = createDataSource();
        BaseDbStore<DataSource> spiDbStore = createDbStore(dataSource);
        DbModel.createTables(spiDbStore);

        RunMigrations.runSqlMigrations(new SqlDbStore(createFoundationDbStore(dataSource))).join();
    }

    @Test
    void runSqlMigrationsAppliesRegisteredOperations() throws Exception {
        DataSource dataSource = createDataSource();
        BaseDbStore<DataSource> spiDbStore = createDbStore(dataSource);
        DbModel.createTables(spiDbStore);
        MigrationPlan.getSqlRegistry().register(DbModel.USER_MESSAGE_TABLE, new AddColumnOperation(
                new OperationMetadata(2, "add runner column"),
                DbModel.USER_MESSAGE_TABLE,
                "runner_source",
                "STRING",
                true,
                null));

        RunMigrations.runSqlMigrations(new SqlDbStore(createFoundationDbStore(dataSource))).join();
        assertTrue(columnExists(dataSource, DbModel.USER_MESSAGE_TABLE, "runner_source"));
        assertEquals("2", readSchemaVersion(dataSource, DbModel.USER_MESSAGE_TABLE));
    }

    @Test
    void runSqlMigrationsReturnsFalseOnFailedEntity() {
        DataSource dataSource = createDataSource();
        BaseDbStore<DataSource> spiDbStore = createDbStore(dataSource);
        DbModel.createTables(spiDbStore);
        MigrationPlan.getSqlRegistry().register("bad_table", new AddColumnOperation(
                new OperationMetadata(1, "bad table"),
                "bad_table",
                "source",
                "TEXT",
                true,
                null));

        boolean failed = false;
        try {
            RunMigrations.runSqlMigrations(new SqlDbStore(createFoundationDbStore(dataSource))).join();
        } catch (Exception e) {
            failed = true;
        }
        assertTrue(failed);
    }

    @Test
    void runKvMigrationsAppliesRegisteredOperations() {
        TestInMemoryKVStore spiKvStore = new TestInMemoryKVStore();
        spiKvStore.set("user_message:1", "old");
        BaseKVStore kvStore = new BaseKVStore() {
            @Override
            public java.util.concurrent.CompletableFuture<Void> set(String key, Object value) {
                spiKvStore.set(key, value);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }

            @Override
            public java.util.concurrent.CompletableFuture<Boolean> exclusiveSet(String key, Object value, Integer expiry) {
                return java.util.concurrent.CompletableFuture.completedFuture(spiKvStore.exclusiveSet(key, value, expiry));
            }

            @Override
            public java.util.concurrent.CompletableFuture<Object> get(String key) {
                return java.util.concurrent.CompletableFuture.completedFuture(spiKvStore.get(key));
            }

            @Override
            public java.util.concurrent.CompletableFuture<Boolean> exists(String key) {
                return java.util.concurrent.CompletableFuture.completedFuture(spiKvStore.isExists(key));
            }

            @Override
            public java.util.concurrent.CompletableFuture<Void> delete(String key) {
                spiKvStore.delete(key);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }

            @Override
            public java.util.concurrent.CompletableFuture<java.util.Map<String, Object>> getByPrefix(String prefix) {
                return java.util.concurrent.CompletableFuture.completedFuture(spiKvStore.getByPrefix(prefix));
            }

            @Override
            public java.util.concurrent.CompletableFuture<Void> deleteByPrefix(String prefix, Integer batchSize) {
                spiKvStore.deleteByPrefix(prefix, batchSize);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }

            @Override
            public java.util.concurrent.CompletableFuture<java.util.List<Object>> mget(java.util.List<String> keys) {
                return java.util.concurrent.CompletableFuture.completedFuture(spiKvStore.mget(keys));
            }

            @Override
            public java.util.concurrent.CompletableFuture<Integer> batchDelete(java.util.List<String> keys, Integer batchSize) {
                return java.util.concurrent.CompletableFuture.completedFuture(spiKvStore.batchDelete(keys, batchSize));
            }

            @Override
            public com.openjiuwen.core.foundation.store.BasedKVStorePipeline pipeline() {
                return null;
            }
        };
        MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY, new UpdateKVOperation(
                new OperationMetadata(3, "update kv"),
                store -> store.set("user_message:1", "new")));

        RunMigrations.runKvMigrations(kvStore).join();
        assertEquals("new", spiKvStore.get("user_message:1"));
        assertEquals("3", spiKvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void runVectorMigrationsAppliesRegisteredOperations() {
        InMemoryVectorStore vectorStore = new InMemoryVectorStore("vector_user_profile");
        String collectionName = "runner_scope_user_profile";
        vectorStore.createCollection(collectionName, 3, Map.of()).join();
        vectorStore.updateCollectionMetadata(collectionName, Map.of("schema_version", 0)).join();
        vectorStore.withCollection(collectionName).add(List.of(Map.of(
                "id", "1",
                "text", "hello",
                "vector", List.of(1.0f, 2.0f, 3.0f)
        )), null, Map.of()).join();
        MigrationPlan.getVectorRegistry().register("vector_user_profile", new AddScalarFieldOperation(
                new OperationMetadata(2, "add runner field"),
                "user_profile",
                "runner_field",
                "string",
                "value"));

        RunMigrations.runVectorMigrations(vectorStore).join();
        var metadata = vectorStore.getCollectionMetadata(collectionName).join();
        assertEquals(2, metadata.get("schema_version"));
        var results = vectorStore.withCollection(collectionName)
                .queryByFilters(Map.of("runner_field", "value"), 10);
        assertEquals("value", results.get(0).getMetadata().get("runner_field"));
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

    private static BaseDbStore<DataSource> createDbStore(DataSource ds) {
        return new BaseDbStore<>() {
            @Override
            public DataSource getEngine() {
                return ds;
            }
        };
    }

    private static com.openjiuwen.core.foundation.store.BaseDbStore<DataSource> createFoundationDbStore(DataSource ds) {
        return new com.openjiuwen.core.foundation.store.BaseDbStore<>() {
            @Override
            public DataSource getAsyncEngine() {
                return ds;
            }
        };
    }

    private static Map<String, List<BaseOperation>> copyOperations(Map<String, List<BaseOperation>> source) {
        Map<String, List<BaseOperation>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, new ArrayList<>(value)));
        return copy;
    }
}
