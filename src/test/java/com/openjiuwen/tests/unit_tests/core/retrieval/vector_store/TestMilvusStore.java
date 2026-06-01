/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.retrieval.vector_store;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.index.response.DescribeIndexResp;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.SearchResp;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Milvus vector store test cases.
 *
 * <p>Mirrors Python's {@code test_milvus_store.py} in
 * {@code tests/unit_tests/core/retrieval/vector_store/test_milvus_store}.</p>
 */
class TestMilvusStore {

    @Test
    void testInitSuccess() {
        try (MockedConstruction<MilvusClientV2> mocked = Mockito.mockConstruction(MilvusClientV2.class)) {
            MilvusVectorStore store = new MilvusVectorStore(config(), "http://localhost:19530", "hybrid");

            assertEquals("test_collection", store.getCollectionName());
            assertEquals("http://localhost:19530", store.getMilvusUri());
            assertNotNull(store.getClient());
            assertEquals(1, mocked.constructed().size());
        }
    }

    @Test
    void testInitWithToken() {
        try (MockedConstruction<MilvusClientV2> ignored = Mockito.mockConstruction(MilvusClientV2.class)) {
            MilvusVectorStore store = new MilvusVectorStore(config(), "http://localhost:19530", "test_token", "hybrid");

            assertEquals("test_token", store.getMilvusToken());
        }
    }

    @Test
    void testInitWithMilvusAlias() {
        MilvusVectorStore store = store(existingClient());

        MilvusVectorStore scoped = (MilvusVectorStore) store.withCollection("my_alias_collection");

        assertEquals("test_collection", store.getCollectionName());
        assertEquals("my_alias_collection", scoped.getCollectionName());
        assertEquals(store.getClient(), scoped.getClient());
    }

    @Test
    void testInitWithCustomFields() {
        MilvusVectorStore store = store(existingClient());

        assertEquals("text", store.getTextField());
        assertEquals("vector", store.getVectorField());
        assertEquals("doc_id", store.getDocIdField());
    }

    @Test
    void testInitWithInvalidVectorField() {
        assertThrows(BaseError.class, () -> new MilvusVectorStore(
                existingClient(), config(), "invalid-index-type"));
    }

    @Test
    void testAddSingleDict() {
        MilvusClientV2 client = existingClient();
        MilvusVectorStore store = store(client);

        store.add(List.of(document("1", "Test content", List.of(0.1f, 0.2f))), null, Map.of());

        ArgumentCaptor<InsertReq> insert = ArgumentCaptor.forClass(InsertReq.class);
        verify(client).insert(insert.capture());
        verify(client).flush(any());
        assertEquals("test_collection", insert.getValue().getCollectionName());
        assertEquals(1, insert.getValue().getData().size());
    }

    @Test
    void testAddListOfDicts() {
        MilvusClientV2 client = existingClient();
        MilvusVectorStore store = store(client);

        store.add(List.of(
                document("1", "Content 1", List.of(0.1f)),
                document("2", "Content 2", List.of(0.2f))), null, Map.of());

        ArgumentCaptor<InsertReq> insert = ArgumentCaptor.forClass(InsertReq.class);
        verify(client).insert(insert.capture());
        assertEquals(2, insert.getValue().getData().size());
    }

    @Test
    void testAddWithBatching() {
        MilvusClientV2 client = existingClient();
        MilvusVectorStore store = store(client);
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            data.add(document(String.valueOf(i), "Content " + i, List.of(0.1f)));
        }

        store.add(data, 50, Map.of());

        verify(client, times(4)).insert(any());
        verify(client).flush(any());
    }

    @Test
    void testSearchSuccess() {
        MilvusClientV2 client = existingClient();
        when(client.search(any())).thenReturn(searchResp(hit("1", "Test content", "doc_1", 0.9f)));

        List<SearchResult> results = store(client).search(List.of(0.1f), 5, Map.of(), Map.of());

        assertEquals(1, results.size());
        assertEquals("Test content", results.get(0).getText());
        assertTrue(results.get(0).getScore() > 0.0);
    }

    @Test
    void testSearchWithFilters() {
        MilvusClientV2 client = existingClient();
        when(client.search(any())).thenReturn(searchResp());

        store(client).search(List.of(0.1f), 5, Map.of("source", "test"), Map.of());

        ArgumentCaptor<SearchReq> captor = ArgumentCaptor.forClass(SearchReq.class);
        verify(client).search(captor.capture());
        assertEquals("source == \"test\"", captor.getValue().getFilter());
    }

    @Test
    void testSearchEmptyResults() {
        MilvusClientV2 client = existingClient();
        when(client.search(any())).thenReturn(searchResp());

        List<SearchResult> results = store(client).search(List.of(0.1f), 5, Map.of(), Map.of());

        assertTrue(results.isEmpty());
    }

    @Test
    void testSparseSearchSuccess() {
        MilvusClientV2 client = existingClient();
        when(client.search(any())).thenReturn(searchResp(hit("1", "Test content", "doc_1", 0.8f)));

        List<SearchResult> results = store(client).sparseSearch("test query", 5, Map.of(), Map.of());

        assertEquals(1, results.size());
        assertEquals("Test content", results.get(0).getText());
        assertEquals(0.8d, results.get(0).getScore(), 1e-6);
    }

    @Test
    void testSparseSearchWithFilters() {
        MilvusClientV2 client = existingClient();
        when(client.search(any())).thenReturn(searchResp());

        store(client).sparseSearch("test query", 5, Map.of("source", "test"), Map.of());

        ArgumentCaptor<SearchReq> captor = ArgumentCaptor.forClass(SearchReq.class);
        verify(client).search(captor.capture());
        assertEquals(IndexParam.MetricType.BM25, captor.getValue().getMetricType());
        assertEquals("source == \"test\"", captor.getValue().getFilter());
    }

    @Test
    void testSparseSearchFailure() {
        MilvusClientV2 client = existingClient();
        when(client.search(any())).thenThrow(new RuntimeException("Search error"));

        List<SearchResult> results = store(client).sparseSearch("test query", 5, Map.of(), Map.of());

        assertTrue(results.isEmpty());
    }

    @Test
    void testHybridSearchSuccess() {
        MilvusClientV2 client = existingClient();
        when(client.hybridSearch(any())).thenReturn(searchResp(hit("1", "Test content", "doc_1", 0.9f)));

        List<SearchResult> results = store(client).hybridSearch(
                "test", List.of(0.1f), 5, 0.5, Map.of(), Map.of());

        assertEquals(1, results.size());
        verify(client).hybridSearch(any());
    }

    @Test
    void testHybridSearchWithoutVector() {
        MilvusClientV2 client = existingClient();
        when(client.search(any())).thenReturn(searchResp());

        List<SearchResult> results = store(client).hybridSearch(
                "test", null, 5, 0.5, Map.of(), Map.of());

        assertNotNull(results);
        verify(client).search(any());
        verify(client, never()).hybridSearch(any());
    }

    @Test
    void testHybridSearchFallback() {
        MilvusClientV2 client = existingClient();
        when(client.hybridSearch(any())).thenThrow(new RuntimeException("Hybrid search error"));
        when(client.search(any())).thenReturn(
                searchResp(hit("1", "Vector result", "doc_1", 0.9f)),
                searchResp(hit("2", "Sparse result", "doc_2", 0.8f)));

        List<SearchResult> results = store(client).hybridSearch(
                "test", List.of(0.1f), 5, 0.5, Map.of(), Map.of());

        assertFalse(results.isEmpty());
        verify(client).hybridSearch(any());
        verify(client, times(2)).search(any());
    }

    @Test
    void testDeleteByIds() {
        MilvusClientV2 client = existingClient();
        when(client.delete(any())).thenReturn(DeleteResp.builder().deleteCnt(2).build());

        boolean result = store(client).delete(List.of("1", "2"), Map.of(), Map.of());

        assertTrue(result);
        ArgumentCaptor<DeleteReq> delete = ArgumentCaptor.forClass(DeleteReq.class);
        verify(client).delete(delete.capture());
        verify(client).flush(any());
        assertTrue(delete.getValue().getFilter().contains("chunk_id in [\"1\", \"2\"]"));
    }

    @Test
    void testDeleteByFilterExpr() {
        MilvusClientV2 client = existingClient();
        when(client.delete(any())).thenReturn(DeleteResp.builder().deleteCnt(1).build());

        boolean result = store(client).delete(null, Map.of("source", "test"), Map.of());

        assertTrue(result);
        ArgumentCaptor<DeleteReq> delete = ArgumentCaptor.forClass(DeleteReq.class);
        verify(client).delete(delete.capture());
        assertEquals("source == \"test\"", delete.getValue().getFilter());
    }

    @Test
    void testDeleteNoResults() {
        MilvusClientV2 client = existingClient();
        when(client.delete(any())).thenReturn(DeleteResp.builder().deleteCnt(0).build());

        assertFalse(store(client).delete(List.of("1"), Map.of(), Map.of()));
    }

    @Test
    void testDeleteWithException() {
        MilvusClientV2 client = existingClient();
        when(client.delete(any())).thenThrow(new RuntimeException("Delete error"));

        assertFalse(store(client).delete(List.of("1"), Map.of(), Map.of()));
    }

    @Test
    void testClose() {
        try (MockedConstruction<MilvusClientV2> mocked = Mockito.mockConstruction(MilvusClientV2.class)) {
            MilvusVectorStore store = new MilvusVectorStore(config(), "http://localhost:19530", "hybrid");

            store.close();

            verify(mocked.constructed().get(0)).close();
        }
    }

    @Test
    void testCloseWithException() {
        try (MockedConstruction<MilvusClientV2> ignored = Mockito.mockConstruction(
                MilvusClientV2.class,
                (mock, context) -> doThrow(new RuntimeException("Close error")).when(mock).close())) {
            MilvusVectorStore store = new MilvusVectorStore(config(), "http://localhost:19530", "hybrid");

            assertDoesNotThrow(store::close);
        }
    }

    @Test
    void testCheckVectorFieldCollectionNotExists() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(false);

        store(client).checkVectorField();

        verify(client).hasCollection(any());
    }

    @Test
    void testCheckVectorFieldVectorFieldNotFound() {
        MilvusClientV2 client = existingClient();
        when(client.describeIndex(any())).thenReturn(DescribeIndexResp.builder()
                .indexDescriptions(List.of())
                .build());
        when(client.describeCollection(any())).thenReturn(DescribeCollectionResp.builder()
                .vectorFieldNames(List.of("other_vector"))
                .build());

        assertThrows(BaseError.class, () -> store(client).checkVectorField());
    }

    @Test
    void testCheckVectorFieldIndexTypeMismatch() {
        MilvusClientV2 client = existingClient();
        when(client.describeIndex(any())).thenReturn(indexResp(IndexParam.IndexType.IVF_FLAT, IndexParam.MetricType.COSINE));

        assertThrows(BaseError.class, () -> store(client).checkVectorField());
    }

    @Test
    void testCheckVectorFieldConfigMismatch() {
        MilvusClientV2 client = existingClient();
        when(client.describeIndex(any())).thenReturn(indexResp(IndexParam.IndexType.AUTOINDEX, IndexParam.MetricType.L2));

        assertThrows(BaseError.class, () -> store(client).checkVectorField());
    }

    @Test
    void testCheckVectorFieldSuccessMatchingConfig() {
        MilvusClientV2 client = existingClient();
        when(client.describeIndex(any())).thenReturn(indexResp(IndexParam.IndexType.AUTOINDEX, IndexParam.MetricType.COSINE));

        assertDoesNotThrow(() -> store(client).checkVectorField());
    }

    @Test
    void testCheckVectorFieldAutoIndexType() {
        MilvusClientV2 client = existingClient();
        when(client.describeIndex(any())).thenReturn(indexResp(IndexParam.IndexType.AUTOINDEX, IndexParam.MetricType.COSINE));

        assertDoesNotThrow(() -> store(client).checkVectorField());
    }

    @Test
    void testCheckVectorFieldIgnoresEfSearchFactor() {
        assertDoesNotThrow(() -> VectorStore.checkConfigsMatching(
                Map.of("M", 16, "efSearchFactor", 2.0),
                Map.of("M", "16")));
    }

    private static VectorStoreConfig config() {
        return new VectorStoreConfig("milvus", "test_collection");
    }

    private static MilvusVectorStore store(MilvusClientV2 client) {
        return new MilvusVectorStore(client, config(), "hybrid");
    }

    private static MilvusClientV2 existingClient() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(true);
        return client;
    }

    private static Map<String, Object> document(String id, String text, List<Float> vector) {
        return new LinkedHashMap<>(Map.of(
                "id", id,
                "text", text,
                "vector", vector,
                "metadata", new LinkedHashMap<>(Map.of("source", "test"))));
    }

    private static SearchResp searchResp(SearchResp.SearchResult... hits) {
        return SearchResp.builder()
                .searchResults(hits.length == 0 ? List.of() : List.of(List.of(hits)))
                .build();
    }

    private static SearchResp.SearchResult hit(String id, String text, String docId, float score) {
        return SearchResp.SearchResult.builder()
                .entity(new LinkedHashMap<>(Map.of(
                        "chunk_id", id,
                        "text", text,
                        "doc_id", docId,
                        "metadata", new LinkedHashMap<>(Map.of("doc_id", docId)))))
                .score(score)
                .primaryKey(id)
                .build();
    }

    private static DescribeIndexResp indexResp(IndexParam.IndexType indexType, IndexParam.MetricType metricType) {
        return DescribeIndexResp.builder()
                .indexDescriptions(List.of(DescribeIndexResp.IndexDesc.builder()
                        .fieldName("vector")
                        .indexType(indexType)
                        .metricType(metricType)
                        .extraParams(Map.of())
                        .build()))
                .build();
    }
}
