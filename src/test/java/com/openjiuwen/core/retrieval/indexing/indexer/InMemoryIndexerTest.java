/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.indexer;

import com.openjiuwen.core.retrieval.common.BaseCallback;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryIndexerTest {

    @Test
    void buildIndexInvokesProgressCallback() {
        String collection = "idx_" + UUID.randomUUID().toString().replace("-", "");
        InMemoryIndexer indexer = new InMemoryIndexer(new InMemoryVectorStore(new VectorStoreConfig("chroma", collection), "hybrid"));
        BaseCallback callback = new BaseCallback(List.of());

        boolean built = indexer.buildIndex(
                List.of(TextChunk.fromDocument(new Document("doc-1", "hello world", Map.of()), "hello world")),
                new IndexConfig(collection, "hybrid", false),
                new FixedEmbedding(),
                Map.of("callback", callback)).join();

        assertEquals(true, built);
        assertEquals(1, callback.getCallCounter());
    }

    private static final class FixedEmbedding extends Embedding {
        @Override
        public java.util.concurrent.CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            return java.util.concurrent.CompletableFuture.completedFuture(List.of(1.0, 0.0));
        }

        @Override
        public java.util.concurrent.CompletableFuture<List<List<Double>>> embedDocuments(
                List<String> texts, Integer batchSize, Map<String, Object> kwargs) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    texts.stream().map(text -> List.of(1.0, 0.0)).toList());
        }

        @Override
        public int getDimension() {
            return 2;
        }
    }
}
