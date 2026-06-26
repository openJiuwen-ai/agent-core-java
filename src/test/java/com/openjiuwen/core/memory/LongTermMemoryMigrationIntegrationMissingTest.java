/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory;

import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.foundation.store.VectorSearchResult;
import com.openjiuwen.core.foundation.store.db.DefaultDbStore;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.common.KvPrefixRegistry;
import com.openjiuwen.core.memory.manage.mem_model.DbModelSupport;
import com.openjiuwen.core.memory.migration.MigrationPlan;
import com.openjiuwen.core.memory.migration.migrator.KvMigrator;
import com.openjiuwen.core.memory.migration.operation.AddColumnOperation;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.RenameScalarFieldOperation;
import com.openjiuwen.core.memory.migration.operation.UpdateKVOperation;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Mirrors Python's {@code TestLongTermMemoryMigrationIntegration} in
 * {@code tests/unit_tests/core/memory/test_long_term_memory_migration_integration.py}.
 */
class LongTermMemoryMigrationIntegrationMissingTest {

    @BeforeEach
    void clearRegistriesBeforeTest() {
        clearRegistries();
    }

    @AfterEach
    void clearRegistriesAfterTest() {
        clearRegistries();
    }

    @Test
    void migrationDuringRegisterStore() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        TestDbFixture dbFixture = dbFixture();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "1").join();
        kvStore.set("old_key", "old_value").join();
        MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY,
                kvOperation(2, "Migrate v2", store -> store.get("old_key")
                        .thenCompose(oldValue -> oldValue == null ? CompletableFuture.completedFuture(null)
                                : store.set("new_key", oldValue).thenCompose(ignored -> store.delete("old_key")))));

        new LongTermMemory().registerStore(kvStore, vectorStore, dbFixture.dbStore(), embedding()).join();

        assertThat(kvStore.get("old_key").join()).isNull();
        assertThat(kvStore.get("new_key").join()).isEqualTo("old_value");
        assertThat(kvStore.get(KvMigrator.KV_SCHEMA_VERSION).join()).isEqualTo("2");
    }

    @Test
    void migrationFailurePreventsInitialization() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        TestDbFixture dbFixture = dbFixture();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "1").join();
        MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY,
                kvOperation(2, "Failing migration",
                        store -> CompletableFuture.failedFuture(
                                new IllegalStateException("Migration failed due to data corruption"))));

        Throwable thrown = catchThrowable(
                () -> new LongTermMemory().registerStore(kvStore, vectorStore, dbFixture.dbStore(), embedding()).join()
        );

        assertThat(messageChain(thrown))
                .contains("kv store migration failed")
                .contains("kv store migrations failed");
    }

    @Test
    void idempotentMigration() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        TestDbFixture dbFixture = dbFixture();
        String testPrefix = "TEST_PREFIX_IDEMPOTENT";
        KvPrefixRegistry.getInstance().registerCurrent(testPrefix);
        try {
            kvStore.set(testPrefix + "/existing_data", "value").join();
            kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "0").join();
            MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY,
                    kvOperation(1, "Initialize", store -> store.set(testPrefix + "/initialized", "true")
                            .thenCompose(ignored -> store.set(testPrefix + "/data", "value"))));

            new LongTermMemory().registerStore(kvStore, vectorStore, dbFixture.dbStore(), embedding()).join();
            assertThat(kvStore.get(testPrefix + "/initialized").join()).isEqualTo("true");
            assertThat(kvStore.get(testPrefix + "/data").join()).isEqualTo("value");

            new LongTermMemory().registerStore(kvStore, vectorStore, dbFixture.dbStore(), embedding()).join();
            assertThat(kvStore.get(testPrefix + "/initialized").join()).isEqualTo("true");
            assertThat(kvStore.get(testPrefix + "/data").join()).isEqualTo("value");
        } finally {
            KvPrefixRegistry.getInstance().unregister(testPrefix);
        }
    }

    @Test
    void emptyMigrationPlan() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        TestDbFixture dbFixture = dbFixture();

        new LongTermMemory().registerStore(kvStore, vectorStore, dbFixture.dbStore(), embedding()).join();

        assertThat(kvStore.get(KvMigrator.KV_SCHEMA_VERSION).join()).isNull();
    }

    @Test
    void vectorMigrationDuringRegisterStore() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        TestDbFixture dbFixture = dbFixture();
        vectorStore.createCollection("user_scope_summary", new CollectionSchema(), Map.of()).join();
        vectorStore.updateCollectionMetadata("user_scope_summary", Map.of("schema_version", 0)).join();
        MigrationPlan.getVectorRegistry().register("summary",
                new RenameScalarFieldOperation(version(1, "Rename field"), "summary", "old_field", "new_field"));

        new LongTermMemory().registerStore(kvStore, vectorStore, dbFixture.dbStore(), embedding()).join();

        assertThat(vectorStore.getCollectionMetadata("user_scope_summary").join())
                .containsEntry("schema_version", 1);
        assertThat(vectorStore.schemaUpdates).containsExactly("user_scope_summary");
    }

    @Test
    void sqlMigrationDuringRegisterStore() throws SQLException {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        TestDbFixture dbFixture = dbFixture();
        DbModelSupport.createTables(dbFixture.dbStore()).join();
        MigrationPlan.getSqlRegistry().register("user_message",
                new AddColumnOperation(version(1, "Add column"),
                        "user_message",
                        "new_column",
                        "STRING",
                        true,
                        null));

        new LongTermMemory().registerStore(kvStore, vectorStore, dbFixture.dbStore(), embedding()).join();

        assertThat(hasColumn(dbFixture.dataSource(), "user_message", "new_column")).isTrue();
    }

    @Test
    void allMigrationsTogether() throws SQLException {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        TestDbFixture dbFixture = dbFixture();
        DbModelSupport.createTables(dbFixture.dbStore()).join();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "0").join();
        kvStore.set("old_key", "old_value").join();
        MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY,
                kvOperation(1, "Migrate v1", store -> store.get("old_key")
                        .thenCompose(oldValue -> oldValue == null ? CompletableFuture.completedFuture(null)
                                : store.set("new_key", oldValue).thenCompose(ignored -> store.delete("old_key")))));
        vectorStore.createCollection("user_scope_summary", new CollectionSchema(), Map.of()).join();
        vectorStore.updateCollectionMetadata("user_scope_summary", Map.of("schema_version", 0)).join();
        MigrationPlan.getVectorRegistry().register("summary",
                new RenameScalarFieldOperation(version(1, "Rename field"), "summary", "old_field", "new_field"));
        MigrationPlan.getSqlRegistry().register("user_message",
                new AddColumnOperation(version(1, "Add column"),
                        "user_message",
                        "new_column",
                        "STRING",
                        true,
                        null));

        new LongTermMemory().registerStore(kvStore, vectorStore, dbFixture.dbStore(), embedding()).join();

        assertThat(kvStore.get("old_key").join()).isNull();
        assertThat(kvStore.get("new_key").join()).isEqualTo("old_value");
        assertThat(kvStore.get(KvMigrator.KV_SCHEMA_VERSION).join()).isEqualTo("1");
        assertThat(vectorStore.getCollectionMetadata("user_scope_summary").join())
                .containsEntry("schema_version", 1);
        assertThat(hasColumn(dbFixture.dataSource(), "user_message", "new_column")).isTrue();
    }

    @Test
    void vectorMigrationFailurePreventsInitialization() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        FailingVectorStore vectorStore = new FailingVectorStore();
        TestDbFixture dbFixture = dbFixture();
        vectorStore.createCollection("user_scope_summary", new CollectionSchema(), Map.of()).join();
        vectorStore.updateCollectionMetadata("user_scope_summary", Map.of("schema_version", 0)).join();
        MigrationPlan.getVectorRegistry().register("summary",
                new RenameScalarFieldOperation(version(1, "Rename field"), "summary", "old_field", "new_field"));

        Throwable thrown = catchThrowable(
                () -> new LongTermMemory().registerStore(kvStore, vectorStore, dbFixture.dbStore(), embedding()).join()
        );

        assertThat(messageChain(thrown))
                .contains("vector store migration failed")
                .contains("vector store migrations failed");
    }

    private static UpdateKVOperation kvOperation(int version,
                                                 String description,
                                                 java.util.function.Function<BaseKVStore, CompletableFuture<Void>>
                                                         updateFunc) {
        return new UpdateKVOperation(version(version, description), updateFunc);
    }

    private static OperationMetadata version(int schemaVersion, String description) {
        return new OperationMetadata(schemaVersion, description);
    }

    private static TestDbFixture dbFixture() {
        String dbName = "long_term_memory_migration_"
                + UUID.randomUUID().toString().replace("-", "");
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return new TestDbFixture(dataSource, new DefaultDbStore<>(dataSource));
    }

    private static Embedding embedding() {
        return new Embedding() {
            @Override
            public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
                return CompletableFuture.completedFuture(List.of(0.0d));
            }

            @Override
            public CompletableFuture<List<List<Double>>> embedDocuments(List<String> texts,
                    Integer batchSize,
                    Map<String, Object> kwargs) {
                return CompletableFuture.completedFuture(
                        texts.stream().map(ignored -> List.of(0.0d)).toList()
                );
            }

            @Override
            public int getDimension() {
                return 1;
            }
        };
    }

    private static boolean hasColumn(JdbcDataSource dataSource, String tableName, String columnName)
            throws SQLException {
        try (Connection connection = dataSource.getConnection();
             ResultSet resultSet = connection.getMetaData()
                     .getColumns(connection.getCatalog(), null, tableName, null)) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String messageChain(Throwable throwable) {
        assertThat(throwable).isNotNull();
        List<String> messages = new ArrayList<>();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                messages.add(current.getMessage());
            }
            current = current.getCause();
        }
        return String.join("\n", messages);
    }

    private static void clearRegistries() {
        MigrationPlan.getSqlRegistry().clear();
        MigrationPlan.getVectorRegistry().clear();
        MigrationPlan.getKvRegistry().clear();
        MigrationPlan.getMessageRegistry().clear();
        MigrationPlan.getIndexRegistry().clear();
    }

    private record TestDbFixture(JdbcDataSource dataSource, DefaultDbStore<JdbcDataSource> dbStore) {
    }

    private static class RecordingVectorStore extends BaseVectorStore {
        private final List<String> collections = new ArrayList<>();
        private final Map<String, Map<String, Object>> metadata = new LinkedHashMap<>();
        private final List<String> schemaUpdates = new ArrayList<>();

        @Override
        public CompletableFuture<Void> createCollection(String collectionName, Object schema, Map<String, Object> kwargs) {
            collections.add(collectionName);
            metadata.put(collectionName, new LinkedHashMap<>());
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteCollection(String collectionName, Map<String, Object> kwargs) {
            collections.remove(collectionName);
            metadata.remove(collectionName);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> collectionExists(String collectionName, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(collections.contains(collectionName));
        }

        @Override
        public CompletableFuture<CollectionSchema> getSchema(String collectionName, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(new CollectionSchema());
        }

        @Override
        public CompletableFuture<Void> addDocs(String collectionName,
                List<Map<String, Object>> docs,
                Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<VectorSearchResult>> search(String collectionName,
                List<Double> queryVector,
                String vectorField,
                int topK,
                Map<String, Object> filters,
                Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Void> deleteDocsByIds(String collectionName,
                List<String> ids,
                Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteDocsByFilters(String collectionName,
                Map<String, Object> filters,
                Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<String>> listCollectionNames() {
            return CompletableFuture.completedFuture(List.copyOf(collections));
        }

        @Override
        public CompletableFuture<Void> updateSchema(String collectionName, List<BaseOperation> operations) {
            schemaUpdates.add(collectionName);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> updateCollectionMetadata(String collectionName, Map<String, Object> metadataUpdate) {
            metadata.computeIfAbsent(collectionName, ignored -> new LinkedHashMap<>()).putAll(metadataUpdate);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> getCollectionMetadata(String collectionName) {
            Map<String, Object> values = new LinkedHashMap<>(
                    metadata.getOrDefault(collectionName, Map.of("schema_version", 0))
            );
            values.putIfAbsent("schema_version", 0);
            return CompletableFuture.completedFuture(values);
        }
    }

    private static final class FailingVectorStore extends RecordingVectorStore {
        @Override
        public CompletableFuture<Void> updateSchema(String collectionName, List<BaseOperation> operations) {
            return CompletableFuture.failedFuture(new IllegalStateException("Vector migration failed"));
        }
    }
}
