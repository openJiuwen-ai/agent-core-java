/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
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
        BaseCallback callback = new BaseCallback();

        boolean built = indexer.buildIndex(
                List.of(TextChunk.fromDocument(new Document("doc-1", "hello world", Map.of()), "hello world")),
                new IndexConfig(collection, "hybrid"),
                new FixedEmbedding(),
                Map.of("callback", callback));

        assertEquals(true, built);
        assertEquals(1, callback.getCallCounter());
    }

    private static final class FixedEmbedding implements Embedding {
        @Override
        public List<Float> embedQuery(String text) {
            return List.of(1.0f, 0.0f);
        }

        @Override
        public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize) {
            return texts.stream().map(text -> embedQuery(text)).toList();
        }

        @Override
        public int getDimension() {
            return 2;
        }
    }
}
