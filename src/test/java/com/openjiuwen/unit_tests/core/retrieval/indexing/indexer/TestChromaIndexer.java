/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.indexer;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.indexing.indexer.ChromaIndexer;
import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;
import com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChromaDB index manager test cases.
 *
 * <p>Mirrors Python's {@code test_chroma_indexer.py} with Java's in-memory
 * Chroma-compatible indexer adaptation.</p>
 */
class TestChromaIndexer {

    @Test
    void testInitSuccess() {
        ChromaIndexer indexer = new ChromaIndexer(new InMemoryVectorStore("test_collection"));

        assertNotNull(indexer);
    }

    @Test
    void testIndexerIsIndexer() {
        ChromaIndexer indexer = new ChromaIndexer(new InMemoryVectorStore("test_collection"));

        assertInstanceOf(Indexer.class, indexer);
    }

    @Test
    void testBuildIndexVectorType() {
        InMemoryVectorStore store = newStore();
        ChromaIndexer indexer = new ChromaIndexer(store);

        boolean result = indexer.buildIndex(twoChunks(), new IndexConfig("test_index", "vector"),
                new StubEmbedding(List.of(List.of(0.1f, 0.1f), List.of(0.2f, 0.2f))), Map.of());

        assertTrue(result);
        assertTrue(indexer.indexExists("test_index"));
        assertEquals(2L, indexer.getIndexInfo("test_index").get("count"));
    }

    @Test
    void testBuildIndexBm25Type() {
        InMemoryVectorStore store = newStore();
        ChromaIndexer indexer = new ChromaIndexer(store);

        boolean result = indexer.buildIndex(List.of(chunk("1", "chunk 1")),
                new IndexConfig("test_index", "bm25"), null, Map.of());

        assertTrue(result);
        assertEquals(1L, indexer.getIndexInfo("test_index").get("count"));
    }

    @Test
    void testBuildIndexHybridType() {
        StubEmbedding embedding = new StubEmbedding(List.of(List.of(0.1f, 0.1f), List.of(0.2f, 0.2f)));
        ChromaIndexer indexer = new ChromaIndexer(newStore());

        boolean result = indexer.buildIndex(twoChunks(), new IndexConfig("test_index", "hybrid"), embedding, Map.of());

        assertTrue(result);
        assertEquals(1, embedding.embedDocumentsCalls);
    }

    @Test
    void testBuildIndexVectorTypeWithoutEmbedModel() {
        ChromaIndexer indexer = new ChromaIndexer(newStore());

        BaseError error = assertThrows(BaseError.class,
                () -> indexer.buildIndex(List.of(chunk("1", "chunk 1")),
                        new IndexConfig("test_index", "vector"), null, Map.of()));

        assertEquals(StatusCode.RETRIEVAL_INDEXING_EMBED_MODEL_NOT_FOUND, error.getStatus());
    }

    @Test
    void testUpdateIndex() {
        InMemoryVectorStore store = newStore();
        ChromaIndexer indexer = new ChromaIndexer(store);
        StubEmbedding embedding = new StubEmbedding(List.of(List.of(0.1f, 0.1f)));

        indexer.buildIndex(List.of(chunk("1", "old chunk")), new IndexConfig("test_index", "vector"), embedding,
                Map.of());
        boolean result = indexer.updateIndex(List.of(new TextChunk("2", "updated chunk", "doc_1")),
                "doc_1", new IndexConfig("test_index", "vector"), embedding, Map.of());

        VectorStore scoped = store.withCollection("test_index");
        List<?> results = scoped.queryByFilters(Map.of("doc_id", "doc_1"), 10);
        assertTrue(result);
        assertEquals(1, results.size());
    }

    @Test
    void testDeleteIndexSuccess() {
        InMemoryVectorStore store = newStore();
        ChromaIndexer indexer = new ChromaIndexer(store);
        indexer.buildIndex(List.of(chunk("1", "chunk 1")), new IndexConfig("test_index", "bm25"), null, Map.of());

        boolean result = indexer.deleteIndex("doc_1", "test_index", Map.of());

        assertTrue(result);
        assertEquals(0L, indexer.getIndexInfo("test_index").get("count"));
    }

    @Test
    void testDeleteIndexNotFound() {
        ChromaIndexer indexer = new ChromaIndexer(newStore());

        assertFalse(indexer.deleteIndex("doc_1", "missing_index", Map.of()));
    }

    @Test
    void testIndexExistsTrue() {
        ChromaIndexer indexer = new ChromaIndexer(newStore());
        indexer.buildIndex(List.of(chunk("1", "chunk 1")), new IndexConfig("test_index", "bm25"), null, Map.of());

        assertTrue(indexer.indexExists("test_index"));
    }

    @Test
    void testIndexExistsFalse() {
        ChromaIndexer indexer = new ChromaIndexer(newStore());

        assertFalse(indexer.indexExists("missing_index"));
    }

    @Test
    void testGetIndexInfoExists() {
        ChromaIndexer indexer = new ChromaIndexer(newStore());
        indexer.buildIndex(twoChunks(), new IndexConfig("test_index", "bm25"), null, Map.of());

        Map<String, Object> info = indexer.getIndexInfo("test_index");

        assertEquals("test_index", info.get("index_name"));
        assertEquals(2L, info.get("count"));
        assertEquals(true, info.get("exists"));
    }

    @Test
    void testGetIndexInfoNotExists() {
        ChromaIndexer indexer = new ChromaIndexer(newStore());

        Map<String, Object> info = indexer.getIndexInfo("missing_index");

        assertEquals("missing_index", info.get("index_name"));
        assertEquals(0L, info.get("count"));
        assertEquals(false, info.get("exists"));
    }

    @Test
    void testGetDatabaseName() {
        ChromaIndexer indexer = new ChromaIndexer(newStore());

        assertTrue(indexer.getDatabaseName().startsWith("db_"));
    }

    @Test
    void testGetDistanceMetric() {
        ChromaIndexer indexer = new ChromaIndexer(newStore());

        assertEquals("cosine", indexer.getDistanceMetric());
    }

    @Test
    void testGetIndexType() {
        ChromaIndexer indexer = new ChromaIndexer(newStore());

        assertEquals("hybrid", indexer.getIndexType());
    }

    @Test
    void testGetTextField() {
        ChromaIndexer indexer = new ChromaIndexer(newStore());

        assertEquals("text", indexer.getTextField());
    }

    @Test
    void testGetVectorField() {
        ChromaIndexer indexer = new ChromaIndexer(newStore());

        assertEquals("vector", indexer.getVectorField());
    }

    @Test
    void testGetSparseVectorField() {
        ChromaIndexer indexer = new ChromaIndexer(newStore());

        assertEquals("sparse_vector", indexer.getSparseVectorField());
    }

    @Test
    void testGetMetadataField() {
        ChromaIndexer indexer = new ChromaIndexer(newStore());

        assertEquals("metadata", indexer.getMetadataField());
    }

    @Test
    void testGetDocIdField() {
        ChromaIndexer indexer = new ChromaIndexer(newStore());

        assertEquals("doc_id", indexer.getDocIdField());
    }

    @Test
    void testBuildIndexWithEmptyChunksStillCreatesEmptyIndexInfo() {
        ChromaIndexer indexer = new ChromaIndexer(newStore());

        boolean result = indexer.buildIndex(List.of(), new IndexConfig("empty_index", "bm25"), null, Map.of());

        assertTrue(result);
        assertEquals(0L, indexer.getIndexInfo("empty_index").get("count"));
    }

    @Test
    void testClose() {
        ChromaIndexer indexer = new ChromaIndexer(newStore());

        indexer.close();
    }

    private static List<TextChunk> twoChunks() {
        return List.of(chunk("1", "chunk 1"), chunk("2", "chunk 2"));
    }

    private static TextChunk chunk(String id, String text) {
        return new TextChunk(id, text, "doc_1");
    }

    private static InMemoryVectorStore newStore() {
        return new InMemoryVectorStore(
                new com.openjiuwen.core.retrieval.common.VectorStoreConfig(
                        "chroma",
                        "db_" + UUID.randomUUID().toString().replace("-", ""),
                        "test_collection",
                        "cosine"),
                "hybrid");
    }

    private static final class StubEmbedding implements Embedding {
        private final List<List<Float>> embeddings;
        private int embedDocumentsCalls;

        private StubEmbedding(List<List<Float>> embeddings) {
            this.embeddings = embeddings;
        }

        @Override
        public List<Float> embedQuery(String text) {
            return embeddings.getFirst();
        }

        @Override
        public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize) {
            embedDocumentsCalls++;
            return embeddings;
        }

        @Override
        public int getDimension() {
            return embeddings.isEmpty() ? 0 : embeddings.getFirst().size();
        }

        @Override
        public int getMaxBatchSize() {
            return 128;
        }
    }
}
