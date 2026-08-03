/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.FieldSchema;
import com.openjiuwen.core.foundation.store.VectorSearchResult;
import com.openjiuwen.core.memory.migration.MigrationPlan;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's old-framework semantic-store coverage in
 * {@code tests/unit_tests/core/foundation/store/test_simple_memory_index.py}.
 */
class SemanticStoreTest {

    @BeforeEach
    void resetVectorRegistry() {
        MigrationPlan.getVectorRegistry().clear();
    }

    @AfterEach
    void clearVectorRegistry() {
        MigrationPlan.getVectorRegistry().clear();
    }

    @Test
    void addDocsCreatesCollectionWithSchemaMetadataAndCache() {
        MigrationPlan.getVectorRegistry().register("vector_profile", new TestOperation(2));
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        SemanticStore semanticStore = new SemanticStore(vectorStore, new TestEmbedding());

        boolean firstWrite = semanticStore.addDocs(
                List.of(Map.entry("m1", "alpha memory")),
                "uid_alice_gid_workspace_mtype_user_profile",
                "workspace"
        ).join();
        boolean secondWrite = semanticStore.addDocs(
                List.of(Map.entry("m2", "beta memory")),
                "uid_alice_gid_workspace_mtype_user_profile",
                "workspace"
        ).join();

        assertThat(firstWrite).isTrue();
        assertThat(secondWrite).isTrue();
        assertThat(vectorStore.createCalls).isEqualTo(1);
        assertThat(vectorStore.collectionSchema("uid_alice_gid_workspace_mtype_user_profile").getFields())
                .extracting(FieldSchema::getName)
                .containsExactly("id", "embedding");
        assertThat(vectorStore.metadataFor("uid_alice_gid_workspace_mtype_user_profile"))
                .containsEntry("schema_version", 2);
        assertThat(vectorStore.docsFor("uid_alice_gid_workspace_mtype_user_profile"))
                .extracting(doc -> doc.get("id"))
                .containsExactly("m1", "m2");
    }

    @Test
    void searchReturnsIdScorePairsFromStoredEmbeddings() {
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        SemanticStore semanticStore = new SemanticStore(vectorStore, new TestEmbedding());
        semanticStore.addDocs(
                List.of(
                        Map.entry("m1", "alpha alpha"),
                        Map.entry("m2", "beta beta")
                ),
                "uid_alice_gid_workspace_mtype_user_profile",
                "workspace"
        ).join();

        List<Map.Entry<String, Double>> results = semanticStore.search(
                "alpha alpha",
                "uid_alice_gid_workspace_mtype_user_profile",
                "workspace",
                2
        ).join();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getKey()).isEqualTo("m1");
        assertThat(results.get(0).getValue()).isGreaterThanOrEqualTo(results.get(1).getValue());
    }

    @Test
    void addDocsWithoutEmbeddingModelReturnsFalseAndSearchReturnsEmpty() {
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        SemanticStore semanticStore = new SemanticStore(vectorStore);

        boolean stored = semanticStore.addDocs(
                List.of(Map.entry("m1", "alpha")),
                "uid_alice_gid_workspace_mtype_user_profile",
                "workspace"
        ).join();
        List<Map.Entry<String, Double>> results = semanticStore.search(
                "alpha",
                "uid_alice_gid_workspace_mtype_user_profile",
                "workspace",
                5
        ).join();

        assertThat(stored).isFalse();
        assertThat(results).isEmpty();
        assertThat(vectorStore.createCalls).isZero();
    }

    @Test
    void deleteDocsAndDeleteTableFollowCollectionExistence() {
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        SemanticStore semanticStore = new SemanticStore(vectorStore, new TestEmbedding());
        String collectionName = "uid_alice_gid_workspace_mtype_user_profile";
        semanticStore.addDocs(
                List.of(
                        Map.entry("m1", "alpha"),
                        Map.entry("m2", "beta")
                ),
                collectionName,
                "workspace"
        ).join();

        semanticStore.deleteDocs(List.of("m1"), collectionName).join();
        semanticStore.deleteDocs(List.of("ghost"), "uid_alice_gid_workspace_mtype_summary").join();
        semanticStore.deleteTable(collectionName).join();

        assertThat(vectorStore.docsFor(collectionName)).isEmpty();
        assertThat(vectorStore.deleteByIdCalls).isEqualTo(1);
        assertThat(vectorStore.deleteCollectionCalls).isEqualTo(1);
        assertThat(vectorStore.collectionExists(collectionName, Map.of()).join()).isFalse();
    }

    /**
     * Mirrors Python's fake embedding used by semantic-store compatibility tests in
     * {@code tests/unit_tests/core/foundation/store/test_simple_memory_index.py}.
     */
    private static final class TestEmbedding extends Embedding {

        private static final int DIMENSION = 8;

        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(embedForTest(text));
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(
                List<String> texts,
                Integer batchSize,
                Map<String, Object> kwargs
        ) {
            List<List<Double>> embeddings = new ArrayList<>();
            for (String text : texts) {
                embeddings.add(embedForTest(text));
            }
            return CompletableFuture.completedFuture(embeddings);
        }

        @Override
        public int getDimension() {
            return DIMENSION;
        }

        private List<Double> embedForTest(String text) {
            double[] values = new double[DIMENSION];
            String safeText = text == null ? "" : text;
            for (int index = 0; index < safeText.length(); index++) {
                values[index % DIMENSION] += safeText.charAt(index) * 0.01d;
            }
            double norm = 0.0d;
            for (double value : values) {
                norm += value * value;
            }
            norm = Math.sqrt(norm);
            List<Double> embedding = new ArrayList<>(DIMENSION);
            for (double value : values) {
                embedding.add(norm == 0.0d ? value : value / norm);
            }
            return embedding;
        }
    }

    /**
     * Mirrors Python's minimal in-memory vector store test seam in
     * {@code tests/unit_tests/core/foundation/store/test_simple_memory_index.py}.
     */
    private static final class RecordingVectorStore extends BaseVectorStore {

        private final Map<String, CollectionSchema> schemas = new LinkedHashMap<>();
        private final Map<String, List<Map<String, Object>>> docs = new LinkedHashMap<>();
        private final Map<String, Map<String, Object>> metadata = new LinkedHashMap<>();

        private int createCalls;
        private int deleteByIdCalls;
        private int deleteCollectionCalls;

        @Override
        public CompletableFuture<Void> createCollection(String collectionName, Object schema, Map<String, Object> kwargs) {
            createCalls++;
            schemas.put(collectionName, (CollectionSchema) schema);
            docs.computeIfAbsent(collectionName, ignored -> new ArrayList<>());
            metadata.computeIfAbsent(collectionName, ignored -> new LinkedHashMap<>());
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteCollection(String collectionName, Map<String, Object> kwargs) {
            deleteCollectionCalls++;
            schemas.remove(collectionName);
            docs.remove(collectionName);
            metadata.remove(collectionName);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> collectionExists(String collectionName, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(schemas.containsKey(collectionName));
        }

        @Override
        public CompletableFuture<CollectionSchema> getSchema(String collectionName, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(schemas.get(collectionName));
        }

        @Override
        public CompletableFuture<Void> addDocs(String collectionName, List<Map<String, Object>> docsToAdd, Map<String, Object> kwargs) {
            List<Map<String, Object>> bucket = docs.computeIfAbsent(collectionName, ignored -> new ArrayList<>());
            for (Map<String, Object> doc : docsToAdd) {
                String id = String.valueOf(doc.get("id"));
                int existingIndex = -1;
                for (int index = 0; index < bucket.size(); index++) {
                    if (Objects.equals(bucket.get(index).get("id"), id)) {
                        existingIndex = index;
                        break;
                    }
                }
                if (existingIndex >= 0) {
                    bucket.set(existingIndex, new LinkedHashMap<>(doc));
                } else {
                    bucket.add(new LinkedHashMap<>(doc));
                }
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
            List<VectorSearchResult> results = new ArrayList<>();
            for (Map<String, Object> doc : docs.getOrDefault(collectionName, List.of())) {
                if (filters != null && !filters.isEmpty()) {
                    boolean match = true;
                    for (Map.Entry<String, Object> filter : filters.entrySet()) {
                        if (!Objects.equals(doc.get(filter.getKey()), filter.getValue())) {
                            match = false;
                            break;
                        }
                    }
                    if (!match) {
                        continue;
                    }
                }
                @SuppressWarnings("unchecked")
                List<Double> embedding = (List<Double>) doc.getOrDefault(vectorField, List.of());
                double score = cosine(queryVector, embedding);
                Map<String, Object> fields = new LinkedHashMap<>(doc);
                fields.remove(vectorField);
                results.add(new VectorSearchResult(score, fields));
            }
            results.sort(Comparator.comparingDouble(VectorSearchResult::getScore).reversed());
            if (results.size() > topK) {
                results = new ArrayList<>(results.subList(0, topK));
            }
            return CompletableFuture.completedFuture(results);
        }

        @Override
        public CompletableFuture<Void> deleteDocsByIds(String collectionName, List<String> ids, Map<String, Object> kwargs) {
            deleteByIdCalls++;
            List<Map<String, Object>> bucket = docs.get(collectionName);
            if (bucket != null) {
                bucket.removeIf(doc -> ids.contains(String.valueOf(doc.get("id"))));
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteDocsByFilters(
                String collectionName,
                Map<String, Object> filters,
                Map<String, Object> kwargs
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<String>> listCollectionNames() {
            return CompletableFuture.completedFuture(new ArrayList<>(schemas.keySet()));
        }

        @Override
        public CompletableFuture<Void> updateSchema(
                String collectionName,
                List<BaseOperation> operations
        ) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> updateCollectionMetadata(String collectionName, Map<String, Object> metadataUpdate) {
            metadata.computeIfAbsent(collectionName, ignored -> new LinkedHashMap<>()).putAll(metadataUpdate);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> getCollectionMetadata(String collectionName) {
            return CompletableFuture.completedFuture(metadataFor(collectionName));
        }

        private CollectionSchema collectionSchema(String collectionName) {
            return schemas.get(collectionName);
        }

        private Map<String, Object> metadataFor(String collectionName) {
            return new LinkedHashMap<>(metadata.getOrDefault(collectionName, Map.of()));
        }

        private List<Map<String, Object>> docsFor(String collectionName) {
            return new ArrayList<>(docs.getOrDefault(collectionName, List.of()));
        }

        private static double cosine(List<Double> left, List<Double> right) {
            double dot = 0.0d;
            double leftNorm = 0.0d;
            double rightNorm = 0.0d;
            for (int index = 0; index < Math.min(left.size(), right.size()); index++) {
                dot += left.get(index) * right.get(index);
            }
            for (double value : left) {
                leftNorm += value * value;
            }
            for (double value : right) {
                rightNorm += value * value;
            }
            if (leftNorm == 0.0d || rightNorm == 0.0d) {
                return 0.0d;
            }
            return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
        }
    }

    /**
     * Mirrors Python's vector migration operation metadata used to seed schema versions in tests.
     */
    private static final class TestOperation extends BaseOperation {

        private TestOperation(int schemaVersion) {
            super(new OperationMetadata(schemaVersion, "v" + schemaVersion));
        }
    }
}
