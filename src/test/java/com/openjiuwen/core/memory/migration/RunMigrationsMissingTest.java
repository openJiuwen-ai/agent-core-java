/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration;

import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.VectorSearchResult;
import com.openjiuwen.core.foundation.store.db.DefaultDbStore;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.manage.mem_model.DbModelSupport;
import com.openjiuwen.core.memory.manage.mem_model.SqlDbStore;
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
 * Mirrors Python's run-migrations tests in
 * {@code tests/unit_tests/core/memory/migration/test_run_migrations.py}.
 */
class RunMigrationsMissingTest {

    @BeforeEach
    void clearRegistriesBeforeTest() {
        clearRegistries();
    }

    @AfterEach
    void clearRegistriesAfterTest() {
        clearRegistries();
    }

    @Test
    void schemaVersionBehindUpgrade() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "1").join();
        kvStore.set("old_key_v1", "value_v1").join();
        kvStore.set("old_key_v2", "value_v2").join();

        MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY,
                kvOperation(2, "Migrate v2", store -> store.get("old_key_v1")
                        .thenCompose(oldValue -> oldValue == null ? CompletableFuture.completedFuture(null)
                                : store.set("new_key_v1", oldValue).thenCompose(ignored -> store.delete("old_key_v1")))));
        MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY,
                kvOperation(3, "Migrate v3", store -> store.get("old_key_v2")
                        .thenCompose(oldValue -> oldValue == null ? CompletableFuture.completedFuture(null)
                                : store.set("new_key_v2", oldValue).thenCompose(ignored -> store.delete("old_key_v2")))));

        RunMigrations.runKvMigrations(kvStore).join();

        assertThat(kvStore.get("old_key_v1").join()).isNull();
        assertThat(kvStore.get("old_key_v2").join()).isNull();
        assertThat(kvStore.get("new_key_v1").join()).isEqualTo("value_v1");
        assertThat(kvStore.get("new_key_v2").join()).isEqualTo("value_v2");
        assertThat(kvStore.get(KvMigrator.KV_SCHEMA_VERSION).join()).isEqualTo("3");
    }

    @Test
    void schemaVersionUpToDate() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "3").join();
        kvStore.set("key1", "value1").join();

        for (int version = 1; version <= 3; version++) {
            MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY,
                    kvOperation(version, "Migrate v" + version,
                            store -> store.set("should_not_execute", "true")));
        }

        RunMigrations.runKvMigrations(kvStore).join();

        assertThat(kvStore.get("key1").join()).isEqualTo("value1");
        assertThat(kvStore.get("should_not_execute").join()).isNull();
        assertThat(kvStore.get(KvMigrator.KV_SCHEMA_VERSION).join()).isEqualTo("3");
    }

    @Test
    void noSchemaVersionFirstTime() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "0").join();
        kvStore.set("TEST_PREFIX_INIT/existing_data", "value").join();

        MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY,
                kvOperation(1, "Initialize v1", store -> store.set("TEST_PREFIX_INIT/initialized_v1", "true")));
        MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY,
                kvOperation(2, "Initialize v2", store -> store.set("TEST_PREFIX_INIT/initialized_v2", "true")));

        RunMigrations.runKvMigrations(kvStore).join();

        assertThat(kvStore.get("TEST_PREFIX_INIT/initialized_v1").join()).isEqualTo("true");
        assertThat(kvStore.get("TEST_PREFIX_INIT/initialized_v2").join()).isEqualTo("true");
        assertThat(kvStore.get(KvMigrator.KV_SCHEMA_VERSION).join()).isEqualTo("2");
    }

    @Test
    void emptyMigrationPlan() {
        InMemoryKVStore kvStore = new InMemoryKVStore();

        RunMigrations.runKvMigrations(kvStore).join();

        assertThat(kvStore.getByPrefix("").join()).isEmpty();
    }

    @Test
    void migrationFailureHandling() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "1").join();
        MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY,
                kvOperation(2, "Failing migration",
                        store -> CompletableFuture.failedFuture(new IllegalStateException("Simulated migration failure"))));

        Throwable thrown = catchThrowable(() -> RunMigrations.runKvMigrations(kvStore).join());

        assertThat(messageChain(thrown)).contains("kv store migrations failed for entity: kv_global");
    }

    @Test
    void multipleOperationsMigration() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "1").join();

        MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY,
                kvOperation(2, "Migrate v2", store -> store.set("v2_migrated", "true")));
        MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY,
                kvOperation(3, "Migrate v3", store -> store.set("v3_migrated", "true")));

        RunMigrations.runKvMigrations(kvStore).join();

        assertThat(kvStore.get("v2_migrated").join()).isEqualTo("true");
        assertThat(kvStore.get("v3_migrated").join()).isEqualTo("true");
        assertThat(kvStore.get(KvMigrator.KV_SCHEMA_VERSION).join()).isEqualTo("3");
    }

    @Test
    void invalidVersionFormat() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "invalid_version").join();
        MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY,
                kvOperation(1, "Migrate", store -> store.set("migrated", "true")));

        Throwable thrown = catchThrowable(() -> RunMigrations.runKvMigrations(kvStore).join());

        assertThat(messageChain(thrown)).contains("kv store migrations failed for entity: kv_global");
        assertThat(messageChain(thrown)).contains("Invalid SCHEMA_VERSION format");
    }

    @Test
    void kvStoreConnectionInterrupted() {
        BaseKVStore kvStore = new ConnectionErrorKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "1").join();
        MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY,
                kvOperation(2, "Migrate", store -> store.get("test_key")
                        .thenCompose(ignored -> store.set("migrated", "true"))));

        Throwable thrown = catchThrowable(() -> RunMigrations.runKvMigrations(kvStore).join());

        assertThat(messageChain(thrown)).contains("kv store migrations failed for entity");
    }

    @Test
    void vectorMigrationWithMatchingCollections() {
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        vectorStore.createCollection("user_scope_summary", new CollectionSchema(), Map.of()).join();
        vectorStore.updateCollectionMetadata("user_scope_summary", Map.of("schema_version", 0)).join();
        MigrationPlan.getVectorRegistry().register("summary",
                new RenameScalarFieldOperation(version(1), "summary", "old_field", "new_field"));

        RunMigrations.runVectorMigrations(vectorStore).join();

        assertThat(vectorStore.metadata.get("user_scope_summary")).containsEntry("schema_version", 1);
        assertThat(vectorStore.schemaUpdates).containsExactly("user_scope_summary");
    }

    @Test
    void vectorMigrationEmptyRegistry() {
        RecordingVectorStore vectorStore = new RecordingVectorStore();

        RunMigrations.runVectorMigrations(vectorStore).join();

        assertThat(vectorStore.listCollectionNames().join()).isEmpty();
    }

    @Test
    void vectorMigrationNoMatchingCollections() {
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        vectorStore.createCollection("other_collection", new CollectionSchema(), Map.of()).join();
        MigrationPlan.getVectorRegistry().register("summary",
                new RenameScalarFieldOperation(version(1), "summary", "old_field", "new_field"));

        RunMigrations.runVectorMigrations(vectorStore).join();

        assertThat(vectorStore.listCollectionNames().join()).containsExactly("other_collection");
        assertThat(vectorStore.schemaUpdates).isEmpty();
    }

    @Test
    void sqlMigrationEmptyRegistry() {
        SqlFixture fixture = sqlFixture();

        RunMigrations.runSqlMigrations(fixture.sqlDbStore()).join();
    }

    @Test
    void sqlMigrationWithOperations() throws SQLException {
        SqlFixture fixture = sqlFixture();
        MigrationPlan.getSqlRegistry().register("user_message",
                new AddColumnOperation(version(1), "user_message", "new_column", "STRING", true, null));

        RunMigrations.runSqlMigrations(fixture.sqlDbStore()).join();

        assertThat(hasColumn(fixture.dataSource(), "user_message", "new_column")).isTrue();
    }

    @Test
    void sqlMigrationMultipleTables() throws SQLException {
        SqlFixture fixture = sqlFixture();
        MigrationPlan.getSqlRegistry().register("user_message",
                new AddColumnOperation(version(1), "user_message", "col1", "STRING", true, null));
        MigrationPlan.getSqlRegistry().register("scope_user_mapping",
                new AddColumnOperation(version(1), "scope_user_mapping", "col2", "INTEGER", true, null));

        RunMigrations.runSqlMigrations(fixture.sqlDbStore()).join();

        assertThat(hasColumn(fixture.dataSource(), "user_message", "col1")).isTrue();
        assertThat(hasColumn(fixture.dataSource(), "scope_user_mapping", "col2")).isTrue();
    }

    private static UpdateKVOperation kvOperation(int version,
                                                 String description,
                                                 java.util.function.Function<BaseKVStore, CompletableFuture<Void>>
                                                         updateFunc) {
        return new UpdateKVOperation(version(version, description), updateFunc);
    }

    private static OperationMetadata version(int schemaVersion) {
        return version(schemaVersion, null);
    }

    private static OperationMetadata version(int schemaVersion, String description) {
        return new OperationMetadata(schemaVersion, description);
    }

    private static void clearRegistries() {
        MigrationPlan.getSqlRegistry().clear();
        MigrationPlan.getVectorRegistry().clear();
        MigrationPlan.getKvRegistry().clear();
        MigrationPlan.getMessageRegistry().clear();
        MigrationPlan.getIndexRegistry().clear();
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

    private static SqlFixture sqlFixture() {
        String dbName = "run_migrations_missing_" + UUID.randomUUID().toString().replace("-", "");
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        DefaultDbStore<JdbcDataSource> dbStore = new DefaultDbStore<>(dataSource);
        DbModelSupport.createTables(dbStore).join();
        return new SqlFixture(dataSource, new SqlDbStore(dbStore));
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

    private record SqlFixture(JdbcDataSource dataSource, SqlDbStore sqlDbStore) {
    }

    private static final class ConnectionErrorKVStore extends InMemoryKVStore {
        @Override
        public CompletableFuture<Object> get(String key) {
            if ("test_key".equals(key)) {
                return CompletableFuture.failedFuture(new IllegalStateException("Connection interrupted"));
            }
            return super.get(key);
        }
    }

    private static final class RecordingVectorStore extends BaseVectorStore {
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
        public CompletableFuture<Void> addDocs(String collectionName, List<Map<String, Object>> docs,
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
        public CompletableFuture<Void> deleteDocsByIds(String collectionName, List<String> ids,
                Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteDocsByFilters(String collectionName, Map<String, Object> filters,
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
}
