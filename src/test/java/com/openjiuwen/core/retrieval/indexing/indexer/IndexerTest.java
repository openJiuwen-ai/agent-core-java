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

class IndexerTest {

    @Test
    void buildIndexReturnsConcreteFutureResult() {
        ConcreteIndexer indexer = new ConcreteIndexer();
        boolean result = indexer.buildIndex(
                List.of(new TextChunk("chunk-1", "body", "doc-1")),
                new IndexConfig("test-index", "hybrid", false),
                null,
                Map.of()
        ).join();

        assertThat(result).isTrue();
    }

    @Test
    void updateIndexReturnsConcreteFutureResult() {
        ConcreteIndexer indexer = new ConcreteIndexer();
        boolean result = indexer.updateIndex(
                List.of(new TextChunk("chunk-1", "body", "doc-1")),
                "doc-1",
                new IndexConfig("test-index", "hybrid", false),
                null,
                Map.of()
        ).join();

        assertThat(result).isTrue();
    }

    @Test
    void deleteIndexReturnsConcreteFutureResult() {
        ConcreteIndexer indexer = new ConcreteIndexer();

        assertThat(indexer.deleteIndex("doc-1", "test-index", Map.of()).join()).isTrue();
    }

    @Test
    void indexExistsReturnsConcreteFutureResult() {
        ConcreteIndexer indexer = new ConcreteIndexer();

        assertThat(indexer.indexExists("test-index").join()).isTrue();
    }

    @Test
    void getIndexInfoReturnsConcreteFutureResult() {
        ConcreteIndexer indexer = new ConcreteIndexer();

        assertThat(indexer.getIndexInfo("test-index").join())
                .containsEntry("index_name", "test-index");
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
            return CompletableFuture.completedFuture(Map.of("index_name", indexName));
        }
    }
}
