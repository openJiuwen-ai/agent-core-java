/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.indexer;

import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's document-deletion semantics in
 * {@code openjiuwen/core/retrieval/indexing/indexer/milvus_indexer.py} and
 * {@code openjiuwen/core/retrieval/indexing/indexer/chroma_indexer.py}.
 */
class InMemoryIndexerTest {

    @Test
    void deleteIndexRemovesEveryChunkForDocumentAndKeepsOtherDocuments() {
        InMemoryVectorStore store = new InMemoryVectorStore("delete_by_doc_" + UUID.randomUUID());
        InMemoryIndexer indexer = new InMemoryIndexer(store);
        IndexConfig config = new IndexConfig(store.getCollectionName(), "bm25", false);
        List<TextChunk> chunks = List.of(
                new TextChunk(UUID.randomUUID().toString(), "alpha first", "doc_1"),
                new TextChunk(UUID.randomUUID().toString(), "alpha second", "doc_1"),
                new TextChunk(UUID.randomUUID().toString(), "beta only", "doc_2")
        );

        assertThat(indexer.buildIndex(chunks, config, null, Map.of()).join()).isTrue();
        assertThat(store.queryByFilters(Map.of("doc_id", "doc_1"), 10)).hasSize(2);
        assertThat(store.queryByFilters(Map.of("doc_id", "doc_2"), 10)).hasSize(1);

        assertThat(indexer.deleteIndex("doc_1", config.getIndexName(), Map.of()).join()).isTrue();

        assertThat(store.queryByFilters(Map.of("doc_id", "doc_1"), 10)).isEmpty();
        assertThat(store.sparseSearch("alpha", 10, Map.of("doc_id", "doc_1"), Map.of())).isEmpty();
        assertThat(store.queryByFilters(Map.of("doc_id", "doc_2"), 10)).hasSize(1);
        assertThat(store.sparseSearch("beta", 10, Map.of("doc_id", "doc_2"), Map.of())).hasSize(1);
    }

    @Test
    void deleteIndexReturnsFalseWhenDocumentDoesNotExist() {
        InMemoryVectorStore store = new InMemoryVectorStore("delete_missing_" + UUID.randomUUID());
        InMemoryIndexer indexer = new InMemoryIndexer(store);

        assertThat(indexer.deleteIndex("missing", store.getCollectionName(), Map.of()).join()).isFalse();
    }
}
