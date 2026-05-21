/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.indexer;

import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Indexer abstract base class test cases.
 * Mirrors Python's tests/unit_tests/core/retrieval/indexing/indexer/test_base.py
 */
class TestIndexer {

    /**
     * Concrete index manager implementation for testing abstract base class.
     */
    static class ConcreteIndexer implements Indexer {

        @Override
        public boolean buildIndex(List<TextChunk> chunks, IndexConfig config, 
                                  com.openjiuwen.core.retrieval.embedding.Embedding embedModel,
                                  Map<String, Object> options) {
            return true;
        }

        @Override
        public boolean updateIndex(List<TextChunk> chunks, String docId, IndexConfig config,
                                   com.openjiuwen.core.retrieval.embedding.Embedding embedModel,
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
        public void close() {
        }
    }

    @Test
    void testBuildIndex() {
        // Test building index
        Indexer indexer = new ConcreteIndexer();
        List<TextChunk> chunks = List.of(
                new TextChunk("1", "chunk 1", "doc_1"),
                new TextChunk("2", "chunk 2", "doc_1")
        );
        IndexConfig config = new IndexConfig("test_index", "vector", null, null, null, null, null);
        boolean result = indexer.buildIndex(chunks, config, null, null);
        assertTrue(result);
    }

    @Test
    void testUpdateIndex() {
        // Test updating index
        Indexer indexer = new ConcreteIndexer();
        List<TextChunk> chunks = List.of(
                new TextChunk("1", "updated chunk", "doc_1")
        );
        IndexConfig config = new IndexConfig("test_index", "vector", null, null, null, null, null);
        boolean result = indexer.updateIndex(chunks, "doc_1", config, null, null);
        assertTrue(result);
    }

    @Test
    void testDeleteIndex() {
        // Test deleting index
        Indexer indexer = new ConcreteIndexer();
        boolean result = indexer.deleteIndex("doc_1", "test_index", null);
        assertTrue(result);
    }

    @Test
    void testIndexExists() {
        // Test checking if index exists
        Indexer indexer = new ConcreteIndexer();
        boolean result = indexer.indexExists("test_index");
        assertTrue(result);
    }

    @Test
    void testGetIndexInfo() {
        // Test getting index information
        Indexer indexer = new ConcreteIndexer();
        Map<String, Object> info = indexer.getIndexInfo("test_index");
        assertTrue(info.containsKey("count"));
        assertEquals(10, info.get("count"));
    }

    @Test
    void testClose() {
        // Test close method (default implementation)
        Indexer indexer = new ConcreteIndexer();
        indexer.close();
        // No exception expected
    }
}