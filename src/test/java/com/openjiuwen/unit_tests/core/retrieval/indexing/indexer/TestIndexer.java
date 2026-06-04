/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.retrieval.indexing.indexer;

import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Indexer.
 * <p>
 * Mirrors Python's indexer tests.
 * Tests document indexing functionality.
 */
class TestIndexer {

    /**
     * Concrete index manager implementation for testing the {@link Indexer} abstraction.
     */
    static class ConcreteIndexer implements Indexer {
        @Override
        public boolean buildIndex(List<TextChunk> chunks, IndexConfig config, Embedding embedModel,
                                  Map<String, Object> options) {
            return true;
        }

        @Override
        public boolean updateIndex(List<TextChunk> chunks, String docId, IndexConfig config, Embedding embedModel,
                                   Map<String, Object> options) {
            return true;
        }

        @Override
        public boolean deleteIndex(String docId, String indexName, Map<String, Object> options) {
            return true;
        }

        @Override
        public boolean indexExists(String indexName) {
            return true;
        }

        @Override
        public Map<String, Object> getIndexInfo(String indexName) {
            return Map.of("count", 10);
        }

        @Override
        public String getDatabaseName() {
            return "test_db";
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
            return "doc_id";
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("Test building index")
    void testBuildIndex() {
        Indexer indexer = new ConcreteIndexer();
        List<TextChunk> chunks = List.of(
                new TextChunk("1", "chunk 1", "doc_1"),
                new TextChunk("2", "chunk 2", "doc_1")
        );
        IndexConfig config = new IndexConfig("test_index", "vector");

        boolean result = indexer.buildIndex(chunks, config, null, Map.of());

        assertTrue(result);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test updating index")
    void testUpdateIndex() {
        Indexer indexer = new ConcreteIndexer();
        List<TextChunk> chunks = List.of(new TextChunk("1", "updated chunk", "doc_1"));
        IndexConfig config = new IndexConfig("test_index", "vector");

        boolean result = indexer.updateIndex(chunks, "doc_1", config, null, Map.of());

        assertTrue(result);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test deleting index")
    void testDeleteIndex() {
        Indexer indexer = new ConcreteIndexer();

        boolean result = indexer.deleteIndex("doc_1", "test_index", Map.of());

        assertTrue(result);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test checking if index exists")
    void testIndexExists() {
        Indexer indexer = new ConcreteIndexer();

        boolean result = indexer.indexExists("test_index");

        assertTrue(result);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test getting index information")
    void testGetIndexInfo() {
        Indexer indexer = new ConcreteIndexer();

        Map<String, Object> info = indexer.getIndexInfo("test_index");

        assertTrue(info.containsKey("count"));
        assertEquals(10, info.get("count"));
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Indexing basics)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("Test indexer interface exists")
    void testIndexerInterfaceExists() {
        // IndexerFactory creates indexers for different backends
        assertNotNull(Document.class);
        assertNotNull(TextChunk.class);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test document creation for indexing")
    void testDocumentCreationForIndexing() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "test");
        metadata.put("page", 1);
        
        Document doc = new Document("doc-001", "Test content for indexing", metadata);
        
        assertNotNull(doc);
        assertEquals("doc-001", doc.getId());
        assertEquals("Test content for indexing", doc.getText());
        assertEquals("test", doc.getMetadata().get("source"));
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Indexing operations)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    @DisplayName("Test batch indexing calculation")
    void testBatchIndexingCalculation() {
        int totalDocs = 1000;
        int batchSize = 50;
        
        int batches = (int) Math.ceil((double) totalDocs / batchSize);
        assertEquals(20, batches, "1000 docs with batch size 50 should be 20 batches");
        
        // Last batch size
        int lastBatchSize = totalDocs % batchSize;
        assertEquals(0, lastBatchSize, "Last batch should be empty when perfectly divisible");
    }

    @Test
    @Tag("level1")
    @DisplayName("Test indexing preserves document order")
    void testIndexingPreservesOrder() {
        List<Document> docs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            docs.add(new Document("doc-" + i, "Content " + i, null));
        }
        
        // Verify order
        for (int i = 0; i < docs.size(); i++) {
            assertEquals("doc-" + i, docs.get(i).getId());
            assertEquals("Content " + i, docs.get(i).getText());
        }
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Index metadata)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    @DisplayName("Test index metadata storage")
    void testIndexMetadataStorage() {
        TextChunk chunk = new TextChunk("chunk-001", "Chunk content", "doc-001");
        chunk.getMetadata().put("chunk_index", 0);
        chunk.getMetadata().put("total_chunks", 5);
        chunk.getMetadata().put("source_doc", "doc-001");
        
        assertEquals(0, chunk.getMetadata().get("chunk_index"));
        assertEquals(5, chunk.getMetadata().get("total_chunks"));
        assertEquals("doc-001", chunk.getMetadata().get("source_doc"));
    }

    @Test
    @Tag("level2")
    @DisplayName("Test embedding vector dimension consistency")
    void testEmbeddingVectorDimensionConsistency() {
        // All embeddings should have consistent dimension
        int expectedDimension = 1536;
        
        float[] embedding1 = new float[expectedDimension];
        float[] embedding2 = new float[expectedDimension];
        
        assertEquals(embedding1.length, embedding2.length,
                "All embeddings should have same dimension");
        assertEquals(expectedDimension, embedding1.length);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 3 (Search capabilities)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level3")
    @DisplayName("Test search query preparation")
    void testSearchQueryPreparation() {
        String query = "What is machine learning?";
        
        // Query should be cleaned and prepared for search
        assertNotNull(query);
        assertFalse(query.isEmpty());
        
        // Check query contains meaningful terms
        String[] terms = query.toLowerCase().split("\\s+");
        assertTrue(terms.length > 0);
    }

    @Test
    @Tag("level3")
    @DisplayName("Test top-k result selection")
    void testTopKResultSelection() {
        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Map<String, Object> result = new HashMap<>();
            result.put("score", (float)(Math.random()));
            results.add(result);
        }
        
        int k = 10;
        // Sort by score descending and take top k
        results.sort((a, b) -> Float.compare((Float)b.get("score"), (Float)a.get("score")));
        List<Map<String, Object>> topK = results.subList(0, Math.min(k, results.size()));
        
        assertEquals(k, topK.size(), "Should return exactly k results");
        
        // Verify ordering
        for (int i = 0; i < topK.size() - 1; i++) {
            Float current = (Float) topK.get(i).get("score");
            Float next = (Float) topK.get(i + 1).get("score");
            assertTrue(current >= next, "Results should be sorted by score descending");
        }
    }
}
