/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryVectorStoreTest {

    @Test
    void sparseSearchUsesBm25TermFrequency() {
        String collection = "bm25_" + UUID.randomUUID().toString().replace("-", "");
        InMemoryVectorStore store = new InMemoryVectorStore(new VectorStoreConfig("chroma", collection), "bm25");
        store.add(List.of(
                Map.of("id", "doc1", "text", "apple apple banana", "metadata", Map.of()),
                Map.of("id", "doc2", "text", "apple banana cherry", "metadata", Map.of())),
                128,
                Map.of());

        List<SearchResult> results = store.sparseSearch("apple", 2, Map.of(), Map.of());

        assertEquals(2, results.size());
        assertEquals("doc1", results.get(0).getId());
        assertTrue(results.get(0).getScore() > results.get(1).getScore());
    }

    @Test
    void withCollectionKeepsCollectionsIsolated() {
        String collection = "chunks_" + UUID.randomUUID().toString().replace("-", "");
        InMemoryVectorStore store = new InMemoryVectorStore(new VectorStoreConfig("chroma", collection), "hybrid");
        store.add(List.of(Map.of("id", "base", "text", "base doc", "vector", List.of(1.0f), "metadata", Map.of())), 128, Map.of());

        VectorStore scoped = store.withCollection(collection + "_other");
        scoped.add(List.of(Map.of("id", "other", "text", "other doc", "vector", List.of(1.0f), "metadata", Map.of())), 128, Map.of());

        assertEquals(1L, store.count(collection));
        assertEquals(1L, store.count(collection + "_other"));
    }

    @Test
    void vectorSearchReturnsSortedResults() {
        String collection = "vec_" + UUID.randomUUID().toString().replace("-", "");
        InMemoryVectorStore store = new InMemoryVectorStore(new VectorStoreConfig("chroma", collection), "vector");
        store.add(List.of(
                Map.of("id", "d1", "text", "doc 1", "vector", List.of(1.0f, 0.0f, 0.0f), "metadata", Map.of()),
                Map.of("id", "d2", "text", "doc 2", "vector", List.of(0.0f, 1.0f, 0.0f), "metadata", Map.of()),
                Map.of("id", "d3", "text", "doc 3", "vector", List.of(0.9f, 0.1f, 0.0f), "metadata", Map.of())),
                128, Map.of());

        List<SearchResult> results = store.search(List.of(1.0f, 0.0f, 0.0f), 3, Map.of(), Map.of());

        assertEquals(3, results.size());
        assertEquals("d1", results.get(0).getId());
        assertTrue(results.get(0).getScore() >= results.get(1).getScore());
    }

    @Test
    void hybridSearchCombinesVectorAndSparseScores() {
        String collection = "hybrid_" + UUID.randomUUID().toString().replace("-", "");
        InMemoryVectorStore store = new InMemoryVectorStore(new VectorStoreConfig("chroma", collection), "hybrid");
        store.add(List.of(
                Map.of("id", "d1", "text", "apple banana", "vector", List.of(1.0f, 0.0f), "metadata", Map.of()),
                Map.of("id", "d2", "text", "cherry date", "vector", List.of(0.0f, 1.0f), "metadata", Map.of())),
                128, Map.of());

        List<SearchResult> results = store.hybridSearch("apple", List.of(1.0f, 0.0f), 2, 0.5, Map.of(), Map.of());

        assertEquals(2, results.size());
        assertEquals("d1", results.get(0).getId());
    }

    @Test
    void deleteRemovesDocumentsById() {
        String collection = "del_" + UUID.randomUUID().toString().replace("-", "");
        InMemoryVectorStore store = new InMemoryVectorStore(new VectorStoreConfig("chroma", collection), "hybrid");
        store.add(List.of(
                Map.of("id", "d1", "text", "text1", "metadata", Map.of()),
                Map.of("id", "d2", "text", "text2", "metadata", Map.of())),
                128, Map.of());

        assertTrue(store.delete(List.of("d1"), null, Map.of()));
        assertEquals(1L, store.count(collection));

        List<SearchResult> results = store.sparseSearch("text1", 5, Map.of(), Map.of());
        assertTrue(results.stream().noneMatch(r -> "d1".equals(r.getId())));
    }

    @Test
    void queryByFiltersReturnsMatchingRecords() {
        String collection = "qf_" + UUID.randomUUID().toString().replace("-", "");
        InMemoryVectorStore store = new InMemoryVectorStore(new VectorStoreConfig("chroma", collection), "hybrid");
        store.add(List.of(
                Map.of("id", "d1", "text", "hello", "metadata", Map.of("source", "web")),
                Map.of("id", "d2", "text", "world", "metadata", Map.of("source", "file"))),
                128, Map.of());

        List<SearchResult> results = store.queryByFilters(Map.of("source", "web"), 10);
        assertEquals(1, results.size());
        assertEquals("d1", results.get(0).getId());
    }
}
