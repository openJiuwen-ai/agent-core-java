
package com.openjiuwen.memory.manage.mem_model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.foundation.store.vector.InMemoryVectorStore;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.spi.store.vector.BaseVectorStore;
import com.openjiuwen.spi.store.vector.CollectionSchema;
import com.openjiuwen.spi.store.vector.VectorSearchResult;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class SemanticStoreTest {
    @Test
    void addDocsUsesBackendVectorFieldAndSupportsSemanticSearch() {
        InMemoryVectorStore vectorStore = new InMemoryVectorStore(
                Map.of("collection_name", "semantic_store_vector_field_test"));
        SemanticStore semanticStore = new SemanticStore(vectorStore, new KeywordEmbedding());

        assertTrue(semanticStore.addDocs(List.of(Map.entry("name", "name memory"), Map.entry("age", "age memory")),
                "semantic_store_vector_field_test"));

        List<Map.Entry<String, Double>> results =
            semanticStore.search("name query", "semantic_store_vector_field_test", 1);
        assertEquals(1, results.size());
        assertEquals("name", results.get(0).getKey());
    }

    @Test
    void addDocsCreatesCollectionMetadataAndUsesBackendFieldNames() {
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        SemanticStore semanticStore = new SemanticStore(vectorStore, new FixedEmbedding());

        boolean stored = semanticStore.addDocs(List.of(Map.entry("mem-1", "remember this")),
                "uid_user_gid_scope_mtype_user_profile");

        assertTrue(stored);
        assertTrue(vectorStore.collections.contains("uid_user_gid_scope_mtype_user_profile"));
        assertEquals(Map.of("schema_version", 0), vectorStore.metadata.get("uid_user_gid_scope_mtype_user_profile"));
        Map<String, Object> row = vectorStore.rows.get("uid_user_gid_scope_mtype_user_profile").get(0);
        assertEquals("mem-1", row.get("id"));
        assertTrue(row.containsKey("embedding"));
        assertEquals("remember this", row.get("text"));
        assertFalse(row.containsKey("vector"));
    }

    @Test
    void searchAndDeleteReturnWithoutBackendCallWhenCollectionMissing() {
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        SemanticStore semanticStore = new SemanticStore(vectorStore, new FixedEmbedding());

        assertEquals(List.of(), semanticStore.search("query", "missing", 5));
        semanticStore.deleteDocs(List.of("mem-1"), "missing");

        assertEquals(0, vectorStore.searchCalls);
        assertEquals(0, vectorStore.deleteCalls);
    }

    @Test
    void addDocsRejectsCollectionNamesUnsupportedByVectorBackends() {
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        SemanticStore semanticStore = new SemanticStore(vectorStore, new FixedEmbedding());

        assertFalse(semanticStore.addDocs(List.of(Map.entry("mem-1", "remember this")), "uid_用户😁"));
        assertFalse(semanticStore.addDocs(List.of(Map.entry("mem-2", "remember this")), "a".repeat(256)));
        assertEquals(List.of(), vectorStore.collections);
        assertTrue(vectorStore.rows.isEmpty());
    }

    private static final class FixedEmbedding implements Embedding {
        @Override
        public List<Float> embedQuery(String text) {
            return List.of(1.0f, 0.0f, 0.5f);
        }

        @Override
        public List<List<Float>> embedDocuments(List<?> texts, Integer batchSize) {
            return texts.stream().map(text -> embedQuery(String.valueOf(text))).toList();
        }

        @Override
        public int getDimension() {
            return 3;
        }
    }

    private static final class KeywordEmbedding implements Embedding {
        @Override
        public List<Float> embedQuery(String text) {
            return text.contains("name") ? List.of(1.0F, 0.0F) : List.of(0.0F, 1.0F);
        }

        @Override
        public List<List<Float>> embedDocuments(List<?> texts, Integer batchSize) {
            return texts.stream().map(text -> embedQuery(String.valueOf(text))).toList();
        }

        @Override
        public int getDimension() {
            return 2;
        }
    }

    private static final class RecordingVectorStore extends BaseVectorStore {
        private final List<String> collections;
        private final Map<String, List<Map<String, Object>>> rows;
        private final Map<String, Map<String, Object>> metadata;
        private int searchCalls;
        private int deleteCalls;

        private RecordingVectorStore() {
            this.collections = new ArrayList<>();
            this.rows = new LinkedHashMap<>();
            this.metadata = new LinkedHashMap<>();
        }

        @Override
        public void createCollection(String collectionName, Object schema, Map<String, Object> kwargs) {
            collections.add(collectionName);
        }

        @Override
        public void deleteCollection(String collectionName, Map<String, Object> kwargs) {
            collections.remove(collectionName);
        }

        @Override
        public boolean collectionExists(String collectionName, Map<String, Object> kwargs) {
            return collections.contains(collectionName);
        }

        @Override
        public CollectionSchema getSchema(String collectionName, Map<String, Object> kwargs) {
            return new CollectionSchema();
        }

        @Override
        public void addDocs(String collectionName, List<Map<String, Object>> docs, Map<String, Object> kwargs) {
            rows.computeIfAbsent(collectionName, key -> new ArrayList<>()).addAll(docs);
        }

        @Override
        public List<VectorSearchResult> search(String collectionName, List<Float> queryVector, String vectorField,
                int topK, Map<String, Object> filters, Map<String, Object> kwargs) {
            searchCalls++;
            return List.of();
        }

        @Override
        public void deleteDocsByIds(String collectionName, List<String> ids, Map<String, Object> kwargs) {
            deleteCalls++;
        }

        @Override
        public void deleteDocsByFilters(String collectionName, Map<String, Object> filters,
                Map<String, Object> kwargs) {
            deleteCalls++;
        }

        @Override
        public List<String> listCollectionNames() {
            return List.copyOf(collections);
        }

        @Override
        public Map<String, Object> getCollectionMetadata(String collectionName) {
            return metadata.getOrDefault(collectionName, Map.of());
        }

        @Override
        public void updateCollectionMetadata(String collectionName, Map<String, Object> metadata) {
            this.metadata.computeIfAbsent(collectionName, key -> new LinkedHashMap<>()).putAll(metadata);
        }

        @Override
        public void updateSchema(String collectionName, List<?> operations) {
        }

    }
}
