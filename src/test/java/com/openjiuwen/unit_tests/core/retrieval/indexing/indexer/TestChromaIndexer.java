/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.indexer;

import com.openjiuwen.core.retrieval.indexing.indexer.ChromaIndexer;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChromaDB index manager test cases.
 * Mirrors Python's tests/unit_tests/core/retrieval/indexing/indexer/test_chroma_indexer.py
 *
 * Note: Python tests require chromadb package. Java uses in-memory implementation.
 */
class TestChromaIndexer {

    @Test
    void testInitSuccess() {
        // Test successful initialization
        VectorStore vectorStore = new InMemoryVectorStore();
        ChromaIndexer indexer = new ChromaIndexer(vectorStore);
        assertNotNull(indexer);
    }

    @Test
    void testIndexerIsIndexer() {
        // Test that ChromaIndexer implements Indexer interface
        VectorStore vectorStore = new InMemoryVectorStore();
        ChromaIndexer indexer = new ChromaIndexer(vectorStore);
        assertTrue(indexer instanceof com.openjiuwen.core.retrieval.indexing.indexer.Indexer);
    }

    @Test
    void testClose() {
        // Test close method
        VectorStore vectorStore = new InMemoryVectorStore();
        ChromaIndexer indexer = new ChromaIndexer(vectorStore);
        indexer.close();
        // No exception expected
    }
}