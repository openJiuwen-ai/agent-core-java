
package com.openjiuwen.core.memory.manage.mem_model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore;
import com.openjiuwen.core.retrieval.vector_store.SchemaMutableVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import com.openjiuwen.spi.store.vector.CollectionSchema;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class SemanticStoreTest {
    @Test
    void addDocsUsesBackendVectorFieldAndSupportsSemanticSearch() {
        InMemoryVectorStore vectorStore = new InMemoryVectorStore("semantic_store_vector_field_test");
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
        RecordingVectorStore vectorStore = new RecordingVectorStore("default");
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
        RecordingVectorStore vectorStore = new RecordingVectorStore("default");
        SemanticStore semanticStore = new SemanticStore(vectorStore, new FixedEmbedding());

        assertEquals(List.of(), semanticStore.search("query", "missing", 5));
        semanticStore.deleteDocs(List.of("mem-1"), "missing");

        assertEquals(0, vectorStore.searchCalls);
        assertEquals(0, vectorStore.deleteCalls);
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

    private static final class RecordingVectorStore implements SchemaMutableVectorStore {
        private String collectionName;
        private final List<String> collections;
        private final Map<String, List<Map<String, Object>>> rows;
        private final Map<String, Map<String, Object>> metadata;
        private int searchCalls;
        private int deleteCalls;

        private RecordingVectorStore(String collectionName) {
            this(collectionName, new ArrayList<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
        }

        private RecordingVectorStore(String collectionName, List<String> collections,
                Map<String, List<Map<String, Object>>> rows, Map<String, Map<String, Object>> metadata) {
            this.collectionName = collectionName;
            this.collections = collections;
            this.rows = rows;
            this.metadata = metadata;
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
            return new RecordingVectorStore(collectionName, collections, rows, metadata);
        }

        @Override
        public void ensureCollection(String collectionName, String indexType, Integer dimension,
                Map<String, Object> options) {
            collections.add(collectionName);
        }

        @Override
        public void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options) {
            rows.computeIfAbsent(collectionName, key -> new ArrayList<>()).addAll(data);
        }

        @Override
        public List<SearchResult> search(List<Float> queryVector, int topK, Map<String, Object> filters,
                Map<String, Object> options) {
            searchCalls++;
            return List.of();
        }

        @Override
        public List<SearchResult> sparseSearch(String queryText, int topK, Map<String, Object> filters,
                Map<String, Object> options) {
            return List.of();
        }

        @Override
        public List<SearchResult> hybridSearch(String queryText, List<Float> queryVector, int topK, double alpha,
                Map<String, Object> filters, Map<String, Object> options) {
            return List.of();
        }

        @Override
        public boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options) {
            deleteCalls++;
            return true;
        }

        @Override
        public boolean tableExists(String tableName) {
            return collections.contains(tableName);
        }

        @Override
        public void deleteTable(String tableName) {
            collections.remove(tableName);
        }

        @Override
        public List<SearchResult> queryByFilters(Map<String, Object> filters, int limit) {
            return List.of();
        }

        @Override
        public long count(String tableName) {
            return rows.getOrDefault(tableName, List.of()).size();
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

        @Override
        public CollectionSchema getSchema(String collectionName) {
            return new CollectionSchema();
        }

        @Override
        public String getDatabaseName() {
            return "memory";
        }

        @Override
        public String getDistanceMetric() {
            return "cosine";
        }

        @Override
        public String getIndexType() {
            return "embedding";
        }

        @Override
        public String getTextField() {
            return "text";
        }

        @Override
        public String getVectorField() {
            return "embedding";
        }

        @Override
        public String getSparseVectorField() {
            return "sparse_embedding";
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
}
