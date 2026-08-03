/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.store.index.SimpleMemoryIndex;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.MemResult;
import com.openjiuwen.core.memory.manage.index.BaseMemoryManager;
import com.openjiuwen.core.memory.manage.index.FragmentMemoryManager;
import com.openjiuwen.core.memory.manage.index.WriteManager;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.FragmentMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.OperationType;
import com.openjiuwen.core.memory.manage.search.SearchManager;
import com.openjiuwen.core.memory.manage.search.SearchParams;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code TestMemoryDoc}, {@code TestSimpleMemoryIndex},
 * {@code TestWriteManagerIntegration}, and {@code TestLongTermMemoryIndexIntegration} in
 * {@code tests/unit_tests/core/foundation/store/test_base_memory_index.py}.
 */
class BaseMemoryIndexTest {
    private static final ZonedDateTime TEST_DT = ZonedDateTime.parse("2025-01-01T00:00:00Z");

    @Test
    void memoryDocCreation() {
        MemoryDoc memoryDoc = new MemoryDoc("test_id_123", "Test memory content", "fragment", TEST_DT, null);

        assertThat(memoryDoc.getId()).isEqualTo("test_id_123");
        assertThat(memoryDoc.getText()).isEqualTo("Test memory content");
        assertThat(memoryDoc.getType()).isEqualTo("fragment");
        assertThat(memoryDoc.getTimestamp()).isEqualTo(TEST_DT);
        assertThat(memoryDoc.getFields()).isEmpty();
    }

    @Test
    void memoryDocWithFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("session_id", "session_123");
        fields.put("metadata", Map.of("key", "value"));

        MemoryDoc memoryDoc = new MemoryDoc("test_id_123", "Test memory content", "fragment", TEST_DT, fields);

        assertThat(memoryDoc.getFields()).isEqualTo(fields);
    }

    @Test
    void memoryDocDictConversion() {
        MemoryDoc memoryDoc = new MemoryDoc(
                "test_id_123",
                "Test memory content",
                "fragment",
                TEST_DT,
                Map.of("session_id", "session_123")
        );

        Map<String, Object> dumped = memoryDoc.toMap();
        MemoryDoc restored = MemoryDoc.fromMap(new LinkedHashMap<>(dumped));

        assertThat(dumped).containsEntry("id", "test_id_123");
        assertThat(dumped).containsEntry("text", "Test memory content");
        assertThat(dumped).containsEntry("type", "fragment");
        assertThat(dumped.get("fields")).isEqualTo(Map.of("session_id", "session_123"));
        assertThat(restored).isEqualTo(memoryDoc);
    }

    @Test
    void simpleMemoryIndexAddMemoriesGroupsByType() {
        RecordingKVStore kvStore = new RecordingKVStore();
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        RecordingEmbedding embedding = new RecordingEmbedding();
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);

        index.addMemories("user_1", "scope_1", List.of(
                new MemoryDoc("test_id_1", "Test memory 1", "fragment", TEST_DT, Map.of("session_id", "session_1")),
                new MemoryDoc("test_id_2", "Test memory 2", "summary", TEST_DT, Map.of("session_id", "session_2"))
        )).join();

        assertThat(embedding.embedDocumentsCount).isEqualTo(2);
        assertThat(vectorStore.addDocsCount).isEqualTo(2);
        assertThat(kvStore.setCount).isGreaterThanOrEqualTo(2);
    }

    @Test
    void simpleMemoryIndexDeleteByUserDeletesMatchingCollections() {
        RecordingKVStore kvStore = new RecordingKVStore();
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        vectorStore.collections.addAll(List.of(
                "uid_user_1_gid_scope_1_mtype_fragment",
                "uid_user_1_gid_scope_2_mtype_summary",
                "uid_user_2_gid_scope_1_mtype_fragment"
        ));
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore);

        index.deleteByUser("user_1").join();

        assertThat(vectorStore.listCollectionNamesCount).isEqualTo(1);
        assertThat(vectorStore.deleteCollectionCount).isEqualTo(2);
        assertThat(vectorStore.collections).containsExactly("uid_user_2_gid_scope_1_mtype_fragment");
    }

    @Test
    void writeManagerAddMemoriesRoutesThroughMemoryIndex() {
        ConfiguredMemory configured = configuredMemory();
        Map<String, List<BaseMemoryUnit>> memories = new LinkedHashMap<>();
        memories.put(MemoryType.USER_PROFILE.getValue(), List.of(fragment(
                MemoryType.USER_PROFILE,
                "test_id_1",
                "Test memory 1",
                "source_1"
        )));
        memories.put(MemoryType.SEMANTIC_MEMORY.getValue(), List.of(fragment(
                MemoryType.SEMANTIC_MEMORY,
                "test_id_2",
                "Test memory 2",
                "source_2"
        )));

        configured.writeManager.addMemories("user_1", "scope_1", memories, null)
                .toCompletableFuture()
                .join();

        assertThat(configured.embedding.embedDocumentsCount).isEqualTo(2);
        assertThat(configured.vectorStore.addDocsCount).isEqualTo(2);
    }

    @Test
    void writeManagerSearchReturnsStoredVectorHit() {
        ConfiguredMemory configured = configuredMemory();
        configured.index.addMemories("user_1", "scope_1", List.of(
                new MemoryDoc(
                        "test_id_1",
                        "Test memory 1",
                        MemoryType.USER_PROFILE.getValue(),
                        TEST_DT,
                        Map.of("source_id", "source_1")
                )
        )).join();

        List<Map<String, Object>> results = configured.searchManager.search(SearchParams.builder()
                        .userId("user_1")
                        .scopeId("scope_1")
                        .query("test query")
                        .topK(2)
                        .threshold(0.0d)
                        .searchType(List.of(MemoryType.USER_PROFILE.getValue()))
                        .build())
                .toCompletableFuture()
                .join();

        assertThat(configured.embedding.embedQueryCount).isEqualTo(1);
        assertThat(results).hasSize(1);
        assertThat(results.get(0)).containsEntry("id", "test_id_1");
        assertThat(results.get(0)).containsEntry("mem", "Test memory 1");
        assertThat(results.get(0)).containsEntry("mem_type", MemoryType.USER_PROFILE.getValue());
        assertThat(results.get(0)).containsEntry("score", 0.95d);
    }

    @Test
    void longTermMemoryWriteThroughMemoryIndex() {
        ConfiguredMemory configured = configuredMemory();

        configured.writeManager.addMemories("user_ltm", "scope_ltm", Map.of(
                MemoryType.USER_PROFILE.getValue(), List.of(fragment(
                        MemoryType.USER_PROFILE,
                        "ltm_test_id_1",
                        "LTM test memory 1",
                        "source_1"
                )),
                MemoryType.SEMANTIC_MEMORY.getValue(), List.of(fragment(
                        MemoryType.SEMANTIC_MEMORY,
                        "ltm_test_id_2",
                        "LTM test memory 2",
                        "source_2"
                ))
        ), null).toCompletableFuture().join();

        assertThat(configured.embedding.embedDocumentsCount).isGreaterThanOrEqualTo(2);
        assertThat(configured.vectorStore.addDocsCount).isGreaterThanOrEqualTo(2);
    }

    @Test
    void longTermMemorySearchThroughMemoryIndex() {
        ConfiguredMemory configured = configuredMemory();
        configured.index.addMemories("user_ltm", "scope_ltm", List.of(
                new MemoryDoc(
                        "ltm_search_id_1",
                        "LTM search result",
                        MemoryType.USER_PROFILE.getValue(),
                        TEST_DT,
                        Map.of("source_id", "src_1")
                )
        )).join();

        List<MemResult> results = configured.memory.searchUserMem("test query", 5, "user_ltm", "scope_ltm", 0.0d)
                .join();

        assertThat(configured.embedding.embedQueryCount).isEqualTo(1);
        assertThat(configured.vectorStore.searchCount).isGreaterThanOrEqualTo(1);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMemInfo().getMemId()).isEqualTo("ltm_search_id_1");
        assertThat(results.get(0).getMemInfo().getContent()).isEqualTo("LTM search result");
        assertThat(results.get(0).getScore()).isEqualTo(0.95d);
    }

    @Test
    void longTermMemoryDeleteThroughMemoryIndex() {
        ConfiguredMemory configured = configuredMemory();
        configured.index.addMemories("user_ltm", "scope_ltm", List.of(new MemoryDoc(
                "ltm_del_id_1",
                "Memory to delete",
                MemoryType.USER_PROFILE.getValue(),
                TEST_DT,
                Map.of("source_id", "src_1")
        ))).join();

        configured.memory.deleteMemById("ltm_del_id_1", "user_ltm", "scope_ltm").join();

        assertThat(configured.kvStore.deleteCount).isGreaterThanOrEqualTo(1);
        assertThat(configured.vectorStore.deleteDocsByIdsCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void longTermMemoryUpdateThroughMemoryIndex() {
        ConfiguredMemory configured = configuredMemory();
        configured.index.addMemories("user_ltm", "scope_ltm", List.of(new MemoryDoc(
                "ltm_update_id_1",
                "Old memory content",
                MemoryType.USER_PROFILE.getValue(),
                TEST_DT,
                Map.of("source_id", "src_1")
        ))).join();
        int deleteCountBeforeUpdate = configured.kvStore.deleteCount;
        int addCountBeforeUpdate = configured.vectorStore.addDocsCount;

        configured.memory.updateMemById("ltm_update_id_1", "Updated memory content", "user_ltm", "scope_ltm").join();

        assertThat(configured.kvStore.deleteCount).isGreaterThan(deleteCountBeforeUpdate);
        assertThat(configured.vectorStore.deleteDocsByIdsCount).isGreaterThanOrEqualTo(1);
        assertThat(configured.vectorStore.addDocsCount).isGreaterThan(addCountBeforeUpdate);
        assertThat(configured.index.getById("user_ltm", "scope_ltm", "ltm_update_id_1").join().getText())
                .isEqualTo("Updated memory content");
    }

    @Test
    void longTermMemoryDeleteByUserThroughMemoryIndex() {
        ConfiguredMemory configured = configuredMemory();
        configured.index.addMemories("user_ltm", "scope_ltm", List.of(new MemoryDoc(
                "ltm_user_id_1",
                "Memory to delete by user",
                MemoryType.USER_PROFILE.getValue(),
                TEST_DT,
                Map.of("source_id", "src_1")
        ))).join();

        configured.memory.deleteMemByUserId("user_ltm", "scope_ltm").join();

        assertThat(configured.vectorStore.deleteCollectionCount).isGreaterThanOrEqualTo(1);
        assertThat(configured.kvStore.deleteByPrefixCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void baseMemoryIndexDefaultsMirrorPythonHooks() {
        BaseMemoryIndex index = new BaseMemoryIndex() {
            @Override
            public void setStorageCodec(StorageCodec codec) {
            }

            @Override
            public CompletableFuture<Void> addMemories(String userId, String scopeId, List<MemoryDoc> memories) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> updateMemories(String userId, String scopeId, List<MemoryDoc> memories) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> deleteMemories(String userId, String scopeId, List<String> ids) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> deleteByUser(String userId) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> deleteByScope(String scopeId) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> deleteByUserAndScope(String userId, String scopeId) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<List<MemorySearchResult>> search(
                    String userId,
                    String scopeId,
                    String query,
                    List<String> memTypes,
                    int topK
            ) {
                return CompletableFuture.completedFuture(List.of(new MemorySearchResult(new MemoryDoc(), 0.95)));
            }

            @Override
            public CompletableFuture<MemoryDoc> getById(String userId, String scopeId, String memId) {
                return CompletableFuture.completedFuture(new MemoryDoc());
            }

            @Override
            public CompletableFuture<Void> cleanupBackup(String backupId) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<List<UserScopeKey>> listUserScopes() {
                return CompletableFuture.completedFuture(List.of(new UserScopeKey("u", "s")));
            }
        };

        assertThat(index.listMemories("u", "s", 0, 100, null).join()).isEmpty();
        assertThat(index.getSchemaVersion()).isZero();
        assertThat(index.createBackup().join()).isEmpty();
        assertThat(index.search("u", "s", "q", null, 10).join()).hasSize(1);
        assertThat(index.listUserScopes().join()).containsExactly(new BaseMemoryIndex.UserScopeKey("u", "s"));
    }

    private static FragmentMemoryUnit fragment(MemoryType type, String id, String content, String sourceId) {
        return new FragmentMemoryUnit(type, id, content, sourceId, "2025-01-01 00:00:00", OperationType.ADD);
    }

    private static ConfiguredMemory configuredMemory() {
        RecordingKVStore kvStore = new RecordingKVStore();
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        RecordingEmbedding embedding = new RecordingEmbedding();
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);
        FragmentMemoryManager fragmentManager = new FragmentMemoryManager(index, new byte[0]);
        Map<String, BaseMemoryManager> managers = new LinkedHashMap<>();
        managers.put(MemoryType.USER_PROFILE.getValue(), fragmentManager);
        managers.put(MemoryType.EPISODIC_MEMORY.getValue(), fragmentManager);
        managers.put(MemoryType.SEMANTIC_MEMORY.getValue(), fragmentManager);
        WriteManager writeManager = new WriteManager(managers, index);
        SearchManager searchManager = new SearchManager(managers, new byte[0], index);
        LongTermMemory memory = new LongTermMemory();
        setField(memory, "kvStore", kvStore);
        setField(memory, "memoryIndex", index);
        setField(memory, "writeManager", writeManager);
        setField(memory, "searchManager", searchManager);
        setField(memory, "fragmentType", List.of(
                MemoryType.USER_PROFILE.getValue(),
                MemoryType.EPISODIC_MEMORY.getValue(),
                MemoryType.SEMANTIC_MEMORY.getValue()
        ));
        setField(memory, "baseEmbed", embedding);
        return new ConfiguredMemory(memory, index, writeManager, searchManager, kvStore, vectorStore, embedding);
    }

    private static void setField(LongTermMemory memory, String name, Object value) {
        try {
            Field field = LongTermMemory.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(memory, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to configure LongTermMemory test field " + name, exception);
        }
    }

    private record ConfiguredMemory(
            LongTermMemory memory,
            SimpleMemoryIndex index,
            WriteManager writeManager,
            SearchManager searchManager,
            RecordingKVStore kvStore,
            RecordingVectorStore vectorStore,
            RecordingEmbedding embedding
    ) {
    }

    private static final class RecordingKVStore extends BaseKVStore {
        private final Map<String, Object> values = new LinkedHashMap<>();
        private int setCount;
        private int getCount;
        private int deleteCount;
        private int deleteByPrefixCount;

        @Override
        public CompletableFuture<Void> set(String key, Object value) {
            setCount++;
            values.put(key, value);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> exclusiveSet(String key, Object value, Integer expiry) {
            if (values.containsKey(key)) {
                return CompletableFuture.completedFuture(false);
            }
            values.put(key, value);
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Object> get(String key) {
            getCount++;
            return CompletableFuture.completedFuture(values.get(key));
        }

        @Override
        public CompletableFuture<Boolean> exists(String key) {
            return CompletableFuture.completedFuture(values.containsKey(key));
        }

        @Override
        public CompletableFuture<Void> delete(String key) {
            deleteCount++;
            values.remove(key);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> getByPrefix(String prefix) {
            Map<String, Object> matching = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    matching.put(entry.getKey(), entry.getValue());
                }
            }
            return CompletableFuture.completedFuture(matching);
        }

        @Override
        public CompletableFuture<Void> deleteByPrefix(String prefix, Integer batchSize) {
            deleteByPrefixCount++;
            values.keySet().removeIf(key -> key.startsWith(prefix));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<Object>> mget(List<String> keys) {
            List<Object> result = new ArrayList<>();
            for (String key : keys) {
                result.add(values.get(key));
            }
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletableFuture<Integer> batchDelete(List<String> keys, Integer batchSize) {
            int deleted = 0;
            for (String key : keys) {
                if (values.remove(key) != null) {
                    deleted++;
                }
            }
            return CompletableFuture.completedFuture(deleted);
        }

        @Override
        public BasedKVStorePipeline pipeline() {
            return null;
        }
    }

    private static final class RecordingVectorStore extends BaseVectorStore {
        private final Set<String> collections = new LinkedHashSet<>();
        private final Map<String, List<Map<String, Object>>> docsByCollection = new LinkedHashMap<>();
        private int addDocsCount;
        private int deleteCollectionCount;
        private int deleteDocsByIdsCount;
        private int listCollectionNamesCount;
        private int searchCount;

        @Override
        public CompletableFuture<Void> createCollection(String collectionName, Object schema, Map<String, Object> kwargs) {
            collections.add(collectionName);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteCollection(String collectionName, Map<String, Object> kwargs) {
            deleteCollectionCount++;
            collections.remove(collectionName);
            docsByCollection.remove(collectionName);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> collectionExists(String collectionName, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(collections.contains(collectionName));
        }

        @Override
        public CompletableFuture<CollectionSchema> getSchema(String collectionName, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(new CollectionSchema(List.of(), "", false));
        }

        @Override
        public CompletableFuture<Void> addDocs(
                String collectionName,
                List<Map<String, Object>> docs,
                Map<String, Object> kwargs
        ) {
            addDocsCount++;
            collections.add(collectionName);
            docsByCollection.computeIfAbsent(collectionName, ignored -> new ArrayList<>()).addAll(docs);
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
            searchCount++;
            List<VectorSearchResult> results = new ArrayList<>();
            for (Map<String, Object> doc : docsByCollection.getOrDefault(collectionName, List.of())) {
                results.add(new VectorSearchResult(0.95d, Map.of("id", doc.get("id"))));
            }
            return CompletableFuture.completedFuture(
                    results.size() <= topK ? results : new ArrayList<>(results.subList(0, topK))
            );
        }

        @Override
        public CompletableFuture<Void> deleteDocsByIds(String collectionName, List<String> ids, Map<String, Object> kwargs) {
            deleteDocsByIdsCount++;
            docsByCollection.getOrDefault(collectionName, List.of())
                    .removeIf(doc -> ids.contains(String.valueOf(doc.get("id"))));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteDocsByFilters(
                String collectionName,
                Map<String, Object> filters,
                Map<String, Object> kwargs
        ) {
            docsByCollection.remove(collectionName);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<String>> listCollectionNames() {
            listCollectionNamesCount++;
            return CompletableFuture.completedFuture(new ArrayList<>(collections));
        }

        @Override
        public CompletableFuture<Void> updateSchema(String collectionName, List<BaseOperation> operations) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> updateCollectionMetadata(String collectionName, Map<String, Object> metadata) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> getCollectionMetadata(String collectionName) {
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    private static final class RecordingEmbedding extends Embedding {
        private int embedDocumentsCount;
        private int embedQueryCount;

        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            embedQueryCount++;
            return CompletableFuture.completedFuture(List.of(0.1d, 0.2d, 0.3d));
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(
                List<String> texts,
                Integer batchSize,
                Map<String, Object> kwargs
        ) {
            embedDocumentsCount++;
            List<List<Double>> vectors = new ArrayList<>();
            for (int index = 0; index < texts.size(); index++) {
                vectors.add(Arrays.asList(0.1d + index, 0.2d + index, 0.3d + index));
            }
            return CompletableFuture.completedFuture(vectors);
        }

        @Override
        public int getDimension() {
            return 3;
        }
    }
}
