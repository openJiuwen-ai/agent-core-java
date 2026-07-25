/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.FieldSchema;
import com.openjiuwen.core.foundation.store.VectorDataType;
import com.openjiuwen.core.foundation.store.VectorSearchResult;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticStoreMilvusTest {

    @Test
    void createCollectionUsesVectorStoreBootstrapWithoutEmptyInsert() {
        StubVectorStore vectorStore = new StubVectorStore();
        SemanticStore semanticStore = new SemanticStore(vectorStore);

        semanticStore.addDocs(List.of(Map.entry("mem-1", "remember this")), "memory_fragments")
                .thenAccept(stored -> assertTrue(stored)).join();

        assertTrue(vectorStore.createCollectionCalled);
        assertNotNull(vectorStore.lastSchema);
        assertEquals("memory_fragments", vectorStore.lastCollectionName);
    }

    @Test
    void addDocsBootstrapsVectorOnlyMilvusCollection() {
        StubVectorStore vectorStore = new StubVectorStore();
        SemanticStore semanticStore = new SemanticStore(vectorStore, new FixedEmbedding());

        boolean stored = semanticStore.addDocs(List.of(Map.entry("mem-1", "remember this")), "memory_fragments").join();

        assertTrue(stored);

        assertTrue(vectorStore.createCollectionCalled);
        assertEquals("memory_fragments", vectorStore.lastCollectionName);
        assertTrue(vectorStore.addDocsCalled);
        assertEquals("memory_fragments", vectorStore.addDocsCollectionName);
    }

    private static final class FixedEmbedding extends Embedding {
        @Override
        public java.util.concurrent.CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            return java.util.concurrent.CompletableFuture.completedFuture(List.of(1.0, 0.0, 0.5));
        }

        @Override
        public java.util.concurrent.CompletableFuture<List<List<Double>>> embedDocuments(
                List<String> texts, Integer batchSize, Map<String, Object> kwargs) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    texts.stream().map(text -> List.of(1.0, 0.0, 0.5)).toList());
        }

        @Override
        public int getDimension() {
            return 3;
        }
    }

    private static final class StubVectorStore extends BaseVectorStore {
        boolean createCollectionCalled;
        String lastCollectionName;
        CollectionSchema lastSchema;
        boolean addDocsCalled;
        String addDocsCollectionName;

        @Override
        public CompletableFuture<Void> createCollection(String collectionName, Object schema, Map<String, Object> kwargs) {
            createCollectionCalled = true;
            lastCollectionName = collectionName;
            lastSchema = schema instanceof CollectionSchema ? (CollectionSchema) schema : null;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteCollection(String collectionName, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> collectionExists(String collectionName, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<CollectionSchema> getSchema(String collectionName, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> addDocs(String collectionName, List<Map<String, Object>> docs, Map<String, Object> kwargs) {
            addDocsCalled = true;
            addDocsCollectionName = collectionName;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<VectorSearchResult>> search(String collectionName, List<Double> queryVector, String vectorField, int topK, Map<String, Object> filters, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Void> deleteDocsByIds(String collectionName, List<String> ids, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteDocsByFilters(String collectionName, Map<String, Object> filters, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<String>> listCollectionNames() {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Void> updateSchema(String collectionName, List<com.openjiuwen.core.memory.migration.operation.BaseOperation> operations) {
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
}
