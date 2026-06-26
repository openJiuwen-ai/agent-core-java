/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.indexer;

import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.TextChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code TestIndexer} in
 * {@code tests/unit_tests/core/retrieval/indexing/indexer/test_base.py}.</p>
 */
class IndexerTest {

    @Test
    void buildIndexReturnsConcreteFutureResult() {
        ConcreteIndexer indexer = new ConcreteIndexer();
        boolean result = indexer.buildIndex(
                List.of(
                        new TextChunk("1", "chunk 1", "doc_1"),
                        new TextChunk("2", "chunk 2", "doc_1")
                ),
                new IndexConfig("test_index", "vector", false),
                null,
                Map.of()
        ).join();

        assertThat(result).isTrue();
    }

    @Test
    void updateIndexReturnsConcreteFutureResult() {
        ConcreteIndexer indexer = new ConcreteIndexer();
        boolean result = indexer.updateIndex(
                List.of(new TextChunk("1", "updated chunk", "doc_1")),
                "doc_1",
                new IndexConfig("test_index", "vector", false),
                null,
                Map.of()
        ).join();

        assertThat(result).isTrue();
    }

    @Test
    void deleteIndexReturnsConcreteFutureResult() {
        ConcreteIndexer indexer = new ConcreteIndexer();

        assertThat(indexer.deleteIndex("doc_1", "test_index", Map.of()).join()).isTrue();
    }

    @Test
    void indexExistsReturnsConcreteFutureResult() {
        ConcreteIndexer indexer = new ConcreteIndexer();

        assertThat(indexer.indexExists("test_index").join()).isTrue();
    }

    @Test
    void getIndexInfoReturnsConcreteFutureResult() {
        ConcreteIndexer indexer = new ConcreteIndexer();

        assertThat(indexer.getIndexInfo("test_index").join())
                .containsEntry("count", 10);
    }

    private static final class ConcreteIndexer extends Indexer {

        @Override
        public CompletableFuture<Boolean> buildIndex(
                List<TextChunk> chunks,
                IndexConfig config,
                com.openjiuwen.core.retrieval.embedding.Embedding embedModel,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> updateIndex(
                List<TextChunk> chunks,
                String docId,
                IndexConfig config,
                com.openjiuwen.core.retrieval.embedding.Embedding embedModel,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> deleteIndex(String docId, String indexName, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> indexExists(String indexName) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Map<String, Object>> getIndexInfo(String indexName) {
            return CompletableFuture.completedFuture(Map.of("count", 10));
        }
    }
}
