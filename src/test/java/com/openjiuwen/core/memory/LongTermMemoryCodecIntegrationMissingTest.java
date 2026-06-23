/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.security.AesGcmCrypt;
import com.openjiuwen.core.common.security.CryptUtils;
import com.openjiuwen.core.common.utils.Singleton;
import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.foundation.store.MemoryDoc;
import com.openjiuwen.core.foundation.store.VectorSearchResult;
import com.openjiuwen.core.foundation.store.db.DefaultDbStore;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.config.MemoryEngineConfig;
import com.openjiuwen.core.memory.migration.MigrationPlan;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code TestLongTermMemoryCodecInjection} in
 * {@code tests/unit_tests/core/memory/test_long_term_memory_codec_integration.py}.
 */
class LongTermMemoryCodecIntegrationMissingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final byte[] VALID_KEY = "0123456789abcdef0123456789abcdef"
            .getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void clearGlobalStateBeforeTest() {
        clearGlobalState();
    }

    @AfterEach
    void clearGlobalStateAfterTest() {
        clearGlobalState();
    }

    @Test
    void setConfigInjectsCodec() {
        registerAesGcm();
        LongTermMemory memory = setupMinimalLongTermMemory(VALID_KEY);
        MemoryDoc doc = memoryDoc("test_m1", "encrypted memory content");

        memory.getMemoryIndex().addMemories("u1", "s1", List.of(doc)).join();

        Map<String, Object> rawEntry = firstStoredMemoryEntry((InMemoryKVStore) memory.getKvStore());
        assertThat(memory.getMemoryIndex()).isNotNull();
        assertThat(rawEntry.get("mem")).isNotEqualTo("encrypted memory content");
    }

    @Test
    void setConfigEmptyKeyCodecStillPresent() {
        LongTermMemory memory = setupMinimalLongTermMemory(new byte[0]);
        MemoryDoc doc = memoryDoc("test_m1", "hello");

        memory.getMemoryIndex().addMemories("u1", "s1", List.of(doc)).join();

        Map<String, Object> rawEntry = firstStoredMemoryEntry((InMemoryKVStore) memory.getKvStore());
        MemoryDoc result = memory.getMemoryIndex().getById("u1", "s1", "test_m1").join();
        assertThat(memory.getMemoryIndex()).isNotNull();
        assertThat(rawEntry.get("mem")).isEqualTo("hello");
        assertThat(result.getText()).isEqualTo("hello");
    }

    @Test
    void fullWriteReadCycle() {
        registerAesGcm();
        LongTermMemory memory = setupMinimalLongTermMemory(VALID_KEY);
        MemoryDoc doc = memoryDoc("test_m1", "encrypted memory content");

        memory.getMemoryIndex().addMemories("u1", "s1", List.of(doc)).join();

        MemoryDoc result = memory.getMemoryIndex().getById("u1", "s1", "test_m1").join();
        Map<String, Object> rawEntry = firstStoredMemoryEntry((InMemoryKVStore) memory.getKvStore());
        assertThat(result).isNotNull();
        assertThat(result.getText()).isEqualTo("encrypted memory content");
        assertThat(rawEntry.get("mem")).isNotEqualTo("encrypted memory content");
    }

    private static LongTermMemory setupMinimalLongTermMemory(byte[] cryptoKey) {
        LongTermMemory memory = new LongTermMemory();
        memory.registerStore(new InMemoryKVStore(), new RecordingVectorStore(), dbStore(), new TestEmbedding()).join();
        MemoryEngineConfig config = new MemoryEngineConfig();
        config.setCryptoKey(cryptoKey);
        memory.setConfig(config);
        return memory;
    }

    private static MemoryDoc memoryDoc(String id, String text) {
        return new MemoryDoc(id, text, "user_profile", ZonedDateTime.now(ZoneOffset.UTC), Map.of());
    }

    private static DefaultDbStore<JdbcDataSource> dbStore() {
        JdbcDataSource dataSource = new JdbcDataSource();
        String dbName = "long_term_memory_codec_" + UUID.randomUUID().toString().replace("-", "");
        dataSource.setURL("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return new DefaultDbStore<>(dataSource);
    }

    private static Map<String, Object> firstStoredMemoryEntry(InMemoryKVStore kvStore) {
        for (Map.Entry<String, Object> entry : kvStore.getByPrefix("UMD").join().entrySet()) {
            if (entry.getKey().endsWith("/ids")) {
                continue;
            }
            return readJson(readStoreValue(entry.getValue()));
        }
        throw new AssertionError("Expected at least one stored memory entry");
    }

    private static String readStoreValue(Object rawValue) {
        if (rawValue instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(rawValue);
    }

    private static Map<String, Object> readJson(String payload) {
        try {
            return OBJECT_MAPPER.readValue(payload, MAP_TYPE);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void registerAesGcm() {
        AesGcmCrypt crypt = AesGcmCrypt.getInstance();
        CryptUtils.registerCrypt(CryptUtils.AES_GCM_CRYPT_NAME, crypt);
    }

    private static void clearGlobalState() {
        Singleton.clearAll();
        CryptUtils.unregisterCrypt(CryptUtils.AES_GCM_CRYPT_NAME);
        MigrationPlan.getSqlRegistry().clear();
        MigrationPlan.getVectorRegistry().clear();
        MigrationPlan.getKvRegistry().clear();
        MigrationPlan.getMessageRegistry().clear();
        MigrationPlan.getIndexRegistry().clear();
    }

    private static final class TestEmbedding extends Embedding {

        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of(0.1d));
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(
                List<String> texts,
                Integer batchSize,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(texts.stream().map(ignored -> List.of(0.1d)).toList());
        }

        @Override
        public int getDimension() {
            return 1;
        }
    }

    private static final class RecordingVectorStore extends BaseVectorStore {

        private final Map<String, List<Map<String, Object>>> documents = new LinkedHashMap<>();
        private final Map<String, Map<String, Object>> metadata = new LinkedHashMap<>();

        @Override
        public CompletableFuture<Void> createCollection(String collectionName, Object schema, Map<String, Object> kwargs) {
            documents.computeIfAbsent(collectionName, ignored -> new ArrayList<>());
            metadata.computeIfAbsent(collectionName, ignored -> new LinkedHashMap<>());
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteCollection(String collectionName, Map<String, Object> kwargs) {
            documents.remove(collectionName);
            metadata.remove(collectionName);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> collectionExists(String collectionName, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(documents.containsKey(collectionName));
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
            List<Map<String, Object>> bucket = documents.computeIfAbsent(collectionName, ignored -> new ArrayList<>());
            for (Map<String, Object> doc : docs) {
                String id = String.valueOf(doc.get("id"));
                bucket.removeIf(existing -> Objects.equals(String.valueOf(existing.get("id")), id));
                bucket.add(new LinkedHashMap<>(doc));
            }
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
            documents.getOrDefault(collectionName, List.of())
                    .removeIf(doc -> ids.contains(String.valueOf(doc.get("id"))));
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
            return CompletableFuture.completedFuture(new ArrayList<>(documents.keySet()));
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
