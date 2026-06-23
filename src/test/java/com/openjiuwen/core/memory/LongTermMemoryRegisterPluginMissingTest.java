/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.common.utils.Singleton;
import com.openjiuwen.core.foundation.store.BaseMemoryIndex;
import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.foundation.store.VectorSearchResult;
import com.openjiuwen.core.foundation.store.db.DefaultDbStore;
import com.openjiuwen.core.foundation.store.index.SimpleMemoryIndex;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.migration.MigrationPlan;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code TestLongTermMemoryRegisterPlugin} in
 * {@code tests/unit_tests/core/memory/test_long_term_memory_register_plugin.py}.
 */
class LongTermMemoryRegisterPluginMissingTest {

    @BeforeEach
    void clearGlobalStateBeforeTest() {
        clearGlobalState();
    }

    @AfterEach
    void clearGlobalStateAfterTest() {
        clearGlobalState();
    }

    @Test
    void registerPlugin() {
        LongTermMemory memory = new LongTermMemory();

        memory.registerPlugin("test_simple_index", SimpleMemoryIndex.class, simpleIndexParams()).join();

        assertThat(memory.getMemoryIndex()).isNotNull();
        assertThat(memory.getMemoryIndex()).isInstanceOf(SimpleMemoryIndex.class);
    }

    @Test
    void registerMultiplePlugins() {
        LongTermMemory memory = new LongTermMemory();

        memory.registerPlugin("test_simple_index1", SimpleMemoryIndex.class, simpleIndexParams()).join();
        BaseMemoryIndex firstIndex = memory.getMemoryIndex();
        memory.registerPlugin("test_simple_index2", SimpleMemoryIndex.class, simpleIndexParams()).join();

        assertThat(memory.getMemoryIndex()).isSameAs(firstIndex);
        assertThat(memory.getMemoryIndex()).isInstanceOf(SimpleMemoryIndex.class);
    }

    @Test
    void registerPluginAfterStoreRegistration() {
        LongTermMemory memory = new LongTermMemory();
        memory.registerStore(new InMemoryKVStore(), new RecordingVectorStore(), dbStore(), new TestEmbedding()).join();
        BaseMemoryIndex autoIndex = memory.getMemoryIndex();

        memory.registerPlugin("custom_simple_index", SimpleMemoryIndex.class, simpleIndexParams()).join();

        assertThat(autoIndex).isNotNull();
        assertThat(memory.getMemoryIndex()).isSameAs(autoIndex);
    }

    private static Map<String, Object> simpleIndexParams() {
        return Map.of(
                "kv_store", new InMemoryKVStore(),
                "vector_store", new RecordingVectorStore(),
                "embedding_model", new TestEmbedding()
        );
    }

    private static DefaultDbStore<JdbcDataSource> dbStore() {
        JdbcDataSource dataSource = new JdbcDataSource();
        String dbName = "long_term_memory_register_plugin_" + UUID.randomUUID().toString().replace("-", "");
        dataSource.setURL("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return new DefaultDbStore<>(dataSource);
    }

    private static void clearGlobalState() {
        Singleton.clearAll();
        MigrationPlan.getSqlRegistry().clear();
        MigrationPlan.getVectorRegistry().clear();
        MigrationPlan.getKvRegistry().clear();
        MigrationPlan.getMessageRegistry().clear();
        MigrationPlan.getIndexRegistry().clear();
    }

    private static final class TestEmbedding extends Embedding {

        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of(0.1d, 0.2d, 0.3d));
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(
                List<String> texts,
                Integer batchSize,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(
                    texts.stream().map(ignored -> List.of(0.1d, 0.2d, 0.3d)).toList()
            );
        }

        @Override
        public int getDimension() {
            return 3;
        }
    }

    private static final class RecordingVectorStore extends BaseVectorStore {

        private final Map<String, Map<String, Object>> metadata = new LinkedHashMap<>();
        private final List<String> collections = new ArrayList<>();

        @Override
        public CompletableFuture<Void> createCollection(String collectionName, Object schema, Map<String, Object> kwargs) {
            if (!collections.contains(collectionName)) {
                collections.add(collectionName);
            }
            metadata.computeIfAbsent(collectionName, ignored -> new LinkedHashMap<>());
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
        public CompletableFuture<Void> addDocs(
                String collectionName,
                List<Map<String, Object>> docs,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<VectorSearchResult>> search(
                String collectionName,
                List<Double> queryVector,
                String vectorField,
                int topK,
                Map<String, Object> filters,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Void> deleteDocsByIds(String collectionName, List<String> ids, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteDocsByFilters(
                String collectionName,
                Map<String, Object> filters,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<String>> listCollectionNames() {
            return CompletableFuture.completedFuture(List.copyOf(collections));
        }

        @Override
        public CompletableFuture<Void> updateSchema(String collectionName, List<BaseOperation> operations) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> updateCollectionMetadata(String collectionName, Map<String, Object> metadataUpdate) {
            metadata.computeIfAbsent(collectionName, ignored -> new LinkedHashMap<>()).putAll(metadataUpdate);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> getCollectionMetadata(String collectionName) {
            return CompletableFuture.completedFuture(metadata.getOrDefault(collectionName, Map.of()));
        }
    }
}
