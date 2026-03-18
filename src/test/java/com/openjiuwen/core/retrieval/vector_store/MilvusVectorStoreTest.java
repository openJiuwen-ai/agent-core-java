/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MilvusVectorStoreTest {

    @Test
    void searchNormalizesCosineScoresAndMapsMetadata() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(true);
        when(client.search(any())).thenReturn(SearchResp.builder()
                .searchResults(List.of(List.of(SearchResp.SearchResult.builder()
                        .entity(new LinkedHashMap<>(Map.of(
                                "chunk_id", "chunk-1",
                                "text", "hello world",
                                "doc_id", "doc-1",
                                "metadata", new LinkedHashMap<>(Map.of("source", "web")))))
                        .score(0.8f)
                        .primaryKey("1")
                        .build())))
                .build());

        MilvusVectorStore store = new MilvusVectorStore(client, new VectorStoreConfig("milvus", "kb_chunks"), "vector");
        List<SearchResult> results = store.search(List.of(1.0f, 0.0f), 3, Map.of("doc_id", "doc-1"), Map.of());

        assertEquals(1, results.size());
        assertEquals("chunk-1", results.get(0).getId());
        assertEquals("doc-1", results.get(0).getMetadata().get("doc_id"));
        assertEquals(0.8d, ((Number) results.get(0).getMetadata().get("raw_score")).doubleValue(), 1e-6);
        assertEquals(0.9d, ((Number) results.get(0).getMetadata().get("raw_score_scaled")).doubleValue(), 1e-6);
        assertEquals(0.9d, results.get(0).getScore(), 1e-6);

        ArgumentCaptor<SearchReq> captor = ArgumentCaptor.forClass(SearchReq.class);
        verify(client).search(captor.capture());
        assertEquals("kb_chunks", captor.getValue().getCollectionName());
        assertEquals("doc_id == \"doc-1\"", captor.getValue().getFilter());
    }

    @Test
    void hybridSearchFallsBackToWeightedFusionWhenNativeHybridFails() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(true);
        when(client.hybridSearch(any())).thenThrow(new RuntimeException("native hybrid unavailable"));
        when(client.search(any())).thenReturn(
                SearchResp.builder()
                        .searchResults(List.of(List.of(
                                SearchResp.SearchResult.builder()
                                        .entity(Map.of(
                                                "chunk_id", "chunk-1",
                                                "text", "dense doc",
                                                "doc_id", "doc-1",
                                                "metadata", Map.of("doc_id", "doc-1")))
                                        .score(0.8f)
                                        .build(),
                                SearchResp.SearchResult.builder()
                                        .entity(Map.of(
                                                "chunk_id", "chunk-2",
                                                "text", "other doc",
                                                "doc_id", "doc-2",
                                                "metadata", Map.of("doc_id", "doc-2")))
                                        .score(-0.2f)
                                        .build())))
                        .build(),
                SearchResp.builder()
                        .searchResults(List.of(List.of(
                                SearchResp.SearchResult.builder()
                                        .entity(Map.of(
                                                "chunk_id", "chunk-1",
                                                "text", "dense doc",
                                                "doc_id", "doc-1",
                                                "metadata", Map.of("doc_id", "doc-1")))
                                        .score(3.0f)
                                        .build(),
                                SearchResp.SearchResult.builder()
                                        .entity(Map.of(
                                                "chunk_id", "chunk-2",
                                                "text", "other doc",
                                                "doc_id", "doc-2",
                                                "metadata", Map.of("doc_id", "doc-2")))
                                        .score(0.1f)
                                        .build())))
                        .build());

        MilvusVectorStore store = new MilvusVectorStore(client, new VectorStoreConfig("milvus", "kb_chunks"), "hybrid");
        List<SearchResult> results = store.hybridSearch("apple", List.of(1.0f, 0.0f), 2, 0.75, Map.of("source", "web"), Map.of());

        assertEquals(2, results.size());
        assertEquals("chunk-1", results.get(0).getId());
        assertTrue(results.get(0).getScore() > results.get(1).getScore());
    }

    @Test
    void queryByFiltersAndDeleteUseInExpressions() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(true);
        when(client.query(any())).thenReturn(QueryResp.builder()
                .queryResults(List.of(QueryResp.QueryResult.builder()
                        .entity(new LinkedHashMap<>(Map.of(
                                "chunk_id", "chunk-1",
                                "text", "hello",
                                "doc_id", "doc-1",
                                "metadata", new LinkedHashMap<>(Map.of("doc_id", "doc-1")))))
                        .build()))
                .build());
        when(client.delete(any())).thenReturn(DeleteResp.builder().deleteCnt(2).build());

        MilvusVectorStore store = new MilvusVectorStore(client, new VectorStoreConfig("milvus", "kb_chunks"), "hybrid");
        List<SearchResult> results = store.queryByFilters(Map.of("chunk_id", List.of("chunk-1", "chunk-2"), "doc_id", "doc-1"), 5);

        assertEquals(1, results.size());
        assertEquals("chunk-1", results.get(0).getId());
        assertEquals("doc-1", results.get(0).getMetadata().get("doc_id"));

        ArgumentCaptor<QueryReq> queryCaptor = ArgumentCaptor.forClass(QueryReq.class);
        verify(client).query(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getFilter().contains("chunk_id in [\"chunk-1\", \"chunk-2\"]"));
        assertTrue(queryCaptor.getValue().getFilter().contains("doc_id == \"doc-1\""));

        assertTrue(store.delete(List.of("chunk-1", "chunk-2"), Map.of("doc_id", "doc-1"), Map.of()));

        ArgumentCaptor<DeleteReq> deleteCaptor = ArgumentCaptor.forClass(DeleteReq.class);
        verify(client).delete(deleteCaptor.capture());
        assertTrue(deleteCaptor.getValue().getFilter().contains("chunk_id in [\"chunk-1\", \"chunk-2\"]"));
        assertTrue(deleteCaptor.getValue().getFilter().contains("doc_id == \"doc-1\""));

        ArgumentCaptor<FlushReq> flushCaptor = ArgumentCaptor.forClass(FlushReq.class);
        verify(client).flush(flushCaptor.capture());
        assertEquals(List.of("kb_chunks"), flushCaptor.getValue().getCollectionNames());
        assertFalse(flushCaptor.getValue().getCollectionNames().isEmpty());
    }
}
