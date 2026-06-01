/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.memory;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.migration.MigrationPlan;
import com.openjiuwen.core.memory.migration.migrator.KvMigrator;
import com.openjiuwen.core.memory.migration.operation.AddColumnOperation;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.RenameScalarFieldOperation;
import com.openjiuwen.core.memory.migration.operation.UpdateKVOperation;
import com.openjiuwen.core.memory.support.LongTermMemoryTestSupport;
import com.openjiuwen.core.memory.support.TestDbStore;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.embedding.HashEmbedding;
import com.openjiuwen.core.retrieval.vector_store.SchemaMutableVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import com.openjiuwen.spi.store.BaseDbStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for memory migrations during LongTermMemory.registerStore.
 * Mirrors Python's tests/unit_tests/core/memory/test_long_term_memory_migration_integration.py.
 */
@DisplayName("LongTermMemory migration integration tests")
class TestLongTermMemoryMigrationIntegration {

    private Map<String, List<BaseOperation>> kvBackup;
    private Map<String, List<BaseOperation>> vectorBackup;
    private Map<String, List<BaseOperation>> sqlBackup;

    @BeforeEach
    void backupAndClearRegistries() {
        kvBackup = copyOperations(MigrationPlan.getKvRegistry().getAllOperations());
        vectorBackup = copyOperations(MigrationPlan.getVectorRegistry().getAllOperations());
        sqlBackup = copyOperations(MigrationPlan.getSqlRegistry().getAllOperations());

        MigrationPlan.getKvRegistry().clear();
        MigrationPlan.getVectorRegistry().clear();
        MigrationPlan.getSqlRegistry().clear();
        LongTermMemory.resetInstance();
    }

    @AfterEach
    void restoreRegistries() {
        MigrationPlan.getKvRegistry().clear();
        MigrationPlan.getKvRegistry().setOperations(kvBackup);
        MigrationPlan.getVectorRegistry().clear();
        MigrationPlan.getVectorRegistry().setOperations(vectorBackup);
        MigrationPlan.getSqlRegistry().clear();
        MigrationPlan.getSqlRegistry().setOperations(sqlBackup);
        LongTermMemory.resetInstance();
    }

    @Test
    void testMigrationDuringRegisterStore() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "1");
        kvStore.set("old_key", "old_value");

        MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY,
                new UpdateKVOperation(new OperationMetadata(2, "Migrate v2"), store -> {
                    Object oldValue = store.get("old_key");
                    if (oldValue != null) {
                        store.set("new_key", oldValue);
                        store.delete("old_key");
                    }
                }));

        registerMemory(kvStore, new MockVectorStore(), newDbStore());

        assertNull(kvStore.get("old_key"));
        assertEquals("old_value", kvStore.get("new_key"));
        assertEquals("2", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void testMigrationFailurePreventsInitialization() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "1");

        MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY,
                new UpdateKVOperation(new OperationMetadata(2, "Failing migration"), store -> {
                    throw new IllegalStateException("Migration failed due to data corruption");
                }));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> registerMemory(kvStore, new MockVectorStore(), newDbStore()));

        assertTrue(error.getMessage().contains("kv store migration failed"));
    }

    @Test
    void testIdempotentMigration() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "0");

        MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY,
                new UpdateKVOperation(new OperationMetadata(1, "Initialize"), store -> {
                    store.set("TEST_PREFIX_IDEMPOTENT/initialized", "true");
                    store.set("TEST_PREFIX_IDEMPOTENT/data", "value");
                }));

        registerMemory(kvStore, new MockVectorStore(), newDbStore());
        assertEquals("true", kvStore.get("TEST_PREFIX_IDEMPOTENT/initialized"));
        assertEquals("value", kvStore.get("TEST_PREFIX_IDEMPOTENT/data"));

        registerMemory(kvStore, new MockVectorStore(), newDbStore());
        assertEquals("true", kvStore.get("TEST_PREFIX_IDEMPOTENT/initialized"));
        assertEquals("value", kvStore.get("TEST_PREFIX_IDEMPOTENT/data"));
        assertEquals("1", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void testEmptyMigrationPlan() {
        InMemoryKVStore kvStore = new InMemoryKVStore();

        registerMemory(kvStore, new MockVectorStore(), newDbStore());

        assertNull(kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void testVectorMigrationDuringRegisterStore() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        MockVectorStore vectorStore = new MockVectorStore();
        vectorStore.createCollection("user_scope_summary");
        vectorStore.updateCollectionMetadata("user_scope_summary", Map.of("schema_version", 0));

        MigrationPlan.getVectorRegistry().register("summary",
                new RenameScalarFieldOperation(new OperationMetadata(1, "Rename field"),
                        "summary", "old_field", "new_field"));

        registerMemory(kvStore, vectorStore, newDbStore());

        assertTrue(vectorStore.updateSchemaCalled);
        assertEquals(1, vectorStore.getCollectionMetadata("user_scope_summary").get("schema_version"));
    }

    @Test
    void testSqlMigrationDuringRegisterStore() throws Exception {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestDbStore dbStore = newDbStore();
        createTable(dbStore, "test_table");

        MigrationPlan.getSqlRegistry().register("test_table",
                new AddColumnOperation(new OperationMetadata(1, "Add column"),
                        "test_table", "new_column", "VARCHAR(255)", true, null));

        registerMemory(kvStore, new MockVectorStore(), dbStore);

        assertTrue(columnExists(dbStore, "TEST_TABLE", "NEW_COLUMN"));
    }

    @Test
    void testAllMigrationsTogether() throws Exception {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "0");
        kvStore.set("old_key", "old_value");
        MockVectorStore vectorStore = new MockVectorStore();
        vectorStore.createCollection("user_scope_summary");
        vectorStore.updateCollectionMetadata("user_scope_summary", Map.of("schema_version", 0));
        TestDbStore dbStore = newDbStore();
        createTable(dbStore, "test_table");

        MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY,
                new UpdateKVOperation(new OperationMetadata(1, "Migrate v1"), store -> {
                    Object oldValue = store.get("old_key");
                    if (oldValue != null) {
                        store.set("new_key", oldValue);
                        store.delete("old_key");
                    }
                }));
        MigrationPlan.getVectorRegistry().register("summary",
                new RenameScalarFieldOperation(new OperationMetadata(1, "Rename field"),
                        "summary", "old_field", "new_field"));
        MigrationPlan.getSqlRegistry().register("test_table",
                new AddColumnOperation(new OperationMetadata(1, "Add column"),
                        "test_table", "new_column", "VARCHAR(255)", true, null));

        registerMemory(kvStore, vectorStore, dbStore);

        assertNull(kvStore.get("old_key"));
        assertEquals("old_value", kvStore.get("new_key"));
        assertEquals("1", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
        assertEquals(1, vectorStore.getCollectionMetadata("user_scope_summary").get("schema_version"));
        assertTrue(columnExists(dbStore, "TEST_TABLE", "NEW_COLUMN"));
    }

    @Test
    void testVectorMigrationFailurePreventsInitialization() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        MockVectorStore vectorStore = new FailingVectorStore();
        vectorStore.createCollection("user_scope_summary");
        vectorStore.updateCollectionMetadata("user_scope_summary", Map.of("schema_version", 0));

        MigrationPlan.getVectorRegistry().register("summary",
                new RenameScalarFieldOperation(new OperationMetadata(1, "Rename field"),
                        "summary", "old_field", "new_field"));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> registerMemory(kvStore, vectorStore, newDbStore()));

        assertTrue(error.getMessage().contains("vector store migration failed"));
    }

    private static LongTermMemory registerMemory(InMemoryKVStore kvStore,
                                                 VectorStore vectorStore,
                                                 BaseDbStore<?> dbStore) {
        LongTermMemory.resetInstance();
        LongTermMemory memory = LongTermMemory.getInstance();
        memory.registerStore(kvStore, vectorStore, dbStore, new HashEmbedding());
        return memory;
    }

    private static TestDbStore newDbStore() {
        return new TestDbStore(LongTermMemoryTestSupport.createDataSource());
    }

    private static void createTable(TestDbStore dbStore, String tableName) throws SQLException {
        DataSource dataSource = dbStore.getEngine();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + tableName + " (id VARCHAR(64) PRIMARY KEY)");
        }
    }

    private static boolean columnExists(TestDbStore dbStore, String tableName, String columnName) throws SQLException {
        try (Connection connection = dbStore.getEngine().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet columns = metaData.getColumns(null, null, tableName, columnName)) {
                return columns.next();
            }
        }
    }

    private static Map<String, List<BaseOperation>> copyOperations(Map<String, List<BaseOperation>> source) {
        Map<String, List<BaseOperation>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, new ArrayList<>(value)));
        return copy;
    }

    private static class MockVectorStore implements SchemaMutableVectorStore {
        private final Set<String> collections = new LinkedHashSet<>();
        private final Map<String, Map<String, Object>> metadata = new LinkedHashMap<>();
        private String collectionName = "";
        private boolean updateSchemaCalled;

        void createCollection(String collectionName) {
            collections.add(collectionName);
            metadata.computeIfAbsent(collectionName, key -> new LinkedHashMap<>());
        }

        @Override
        public List<String> listCollectionNames() {
            return new ArrayList<>(collections);
        }

        @Override
        public Map<String, Object> getCollectionMetadata(String collectionName) {
            return metadata.getOrDefault(collectionName, Map.of("schema_version", 0));
        }

        @Override
        public void updateCollectionMetadata(String collectionName, Map<String, Object> metadata) {
            this.metadata.computeIfAbsent(collectionName, key -> new LinkedHashMap<>()).putAll(metadata);
        }

        @Override
        public void updateSchema(String collectionName, List<?> operations) {
            updateSchemaCalled = true;
        }

        @Override
        public String getCollectionName() {
            return collectionName;
        }

        @Override
        public void setCollectionName(String collectionName) {
            this.collectionName = collectionName;
        }

        @Override
        public VectorStore withCollection(String collectionName) {
            this.collectionName = collectionName;
            createCollection(collectionName);
            return this;
        }

        @Override
        public void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options) {
        }

        @Override
        public List<SearchResult> search(List<Float> queryVector, int topK,
                                         Map<String, Object> filters, Map<String, Object> options) {
            return List.of();
        }

        @Override
        public List<SearchResult> sparseSearch(String queryText, int topK,
                                               Map<String, Object> filters, Map<String, Object> options) {
            return List.of();
        }

        @Override
        public List<SearchResult> hybridSearch(String queryText, List<Float> queryVector, int topK,
                                               double alpha, Map<String, Object> filters,
                                               Map<String, Object> options) {
            return List.of();
        }

        @Override
        public boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options) {
            return true;
        }

        @Override
        public boolean tableExists(String tableName) {
            return collections.contains(tableName);
        }

        @Override
        public void deleteTable(String tableName) {
            collections.remove(tableName);
            metadata.remove(tableName);
        }

        @Override
        public List<SearchResult> queryByFilters(Map<String, Object> filters, int limit) {
            return List.of();
        }

        @Override
        public long count(String tableName) {
            return collections.contains(tableName) ? 1 : 0;
        }

        @Override
        public String getDatabaseName() {
            return "memory_test";
        }

        @Override
        public String getDistanceMetric() {
            return "cosine";
        }

        @Override
        public String getIndexType() {
            return "vector";
        }

        @Override
        public String getTextField() {
            return "text";
        }

        @Override
        public String getVectorField() {
            return "vector";
        }

        @Override
        public String getSparseVectorField() {
            return "sparse_vector";
        }

        @Override
        public String getMetadataField() {
            return "metadata";
        }

        @Override
        public String getDocIdField() {
            return "id";
        }
    }

    private static final class FailingVectorStore extends MockVectorStore {
        @Override
        public void updateSchema(String collectionName, List<?> operations) {
            throw new IllegalStateException("Vector migration failed");
        }
    }
}
