/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import com.openjiuwen.core.memory.migration.operation.AddScalarFieldOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;

import io.milvus.orm.iterator.QueryIterator;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.AlterCollectionPropertiesReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.RenameCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryIteratorReq;
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

class MilvusVectorStoreTest {
    @Test
    void searchNormalizesCosineScoresAndMapsMetadata() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(true);
        when(client.search(any())).thenReturn(SearchResp.builder()
                .searchResults(List.of(List.of(SearchResp.SearchResult.builder()
                        .entity(new LinkedHashMap<>(Map.of("chunk_id", "chunk-1", "text", "hello world", "doc_id",
                                "doc-1", "metadata", new LinkedHashMap<>(Map.of("source", "web")))))
                        .score(0.8f).primaryKey("1").build())))
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
                                        .entity(Map.of("chunk_id", "chunk-1", "text", "dense doc", "doc_id", "doc-1",
                                                "metadata", Map.of("doc_id", "doc-1")))
                                        .score(0.8f).build(),
                                SearchResp.SearchResult.builder()
                                        .entity(Map.of("chunk_id", "chunk-2", "text", "other doc", "doc_id", "doc-2",
                                                "metadata", Map.of("doc_id", "doc-2")))
                                        .score(-0.2f).build())))
                        .build(),
                SearchResp.builder()
                        .searchResults(
                                List.of(List.of(
                                        SearchResp.SearchResult.builder()
                                                .entity(Map.of("chunk_id", "chunk-1", "text", "dense doc", "doc_id",
                                                        "doc-1", "metadata", Map.of("doc_id", "doc-1")))
                                                .score(3.0f).build(),
                                        SearchResp.SearchResult.builder()
                                                .entity(Map.of("chunk_id", "chunk-2", "text", "other doc", "doc_id",
                                                        "doc-2", "metadata", Map.of("doc_id", "doc-2")))
                                                .score(0.1f).build())))
                        .build());

        MilvusVectorStore store = new MilvusVectorStore(client, new VectorStoreConfig("milvus", "kb_chunks"), "hybrid");
        List<SearchResult> results =
            store.hybridSearch("apple", List.of(1.0f, 0.0f), 2, 0.75, Map.of("source", "web"), Map.of());

        assertEquals(2, results.size());
        assertEquals("chunk-1", results.get(0).getId());
        assertTrue(results.get(0).getScore() > results.get(1).getScore());
    }

    @Test
    void queryByFiltersAndDeleteUseInExpressions() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(true);
        when(client.query(any()))
                .thenReturn(QueryResp.builder()
                        .queryResults(List.of(QueryResp.QueryResult.builder()
                                .entity(new LinkedHashMap<>(Map.of("chunk_id", "chunk-1", "text", "hello", "doc_id",
                                        "doc-1", "metadata", new LinkedHashMap<>(Map.of("doc_id", "doc-1")))))
                                .build()))
                        .build());
        when(client.delete(any())).thenReturn(DeleteResp.builder().deleteCnt(2).build());

        MilvusVectorStore store = new MilvusVectorStore(client, new VectorStoreConfig("milvus", "kb_chunks"), "hybrid");
        List<SearchResult> results =
            store.queryByFilters(Map.of("chunk_id", List.of("chunk-1", "chunk-2"), "doc_id", "doc-1"), 5);

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

    @Test
    void metadataConvertsSchemaVersionAndUpdatesCollectionProperties() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.describeCollection(any())).thenReturn(DescribeCollectionResp.builder().collectionName("kb_chunks")
                .properties(Map.of("schema_version", "7", "owner", "memory")).build());

        MilvusVectorStore store = new MilvusVectorStore(client, new VectorStoreConfig("milvus", "kb_chunks"), "vector");
        Map<String, Object> metadata = store.getCollectionMetadata("kb_chunks");
        assertEquals(7, metadata.get("schema_version"));
        assertEquals("memory", metadata.get("owner"));

        store.updateCollectionMetadata("kb_chunks", Map.of("schema_version", 8));

        ArgumentCaptor<AlterCollectionPropertiesReq> captor =
            ArgumentCaptor.forClass(AlterCollectionPropertiesReq.class);
        verify(client).alterCollectionProperties(captor.capture());
        assertEquals("8", captor.getValue().getProperties().get("schema_version"));
        assertEquals(8, store.getCollectionMetadata("kb_chunks").get("schema_version"));
    }

    @Test
    void updateSchemaRebuildsCollectionAndCopiesTransformedRows() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.createSchema()).thenCallRealMethod();
        when(client.hasCollection(any())).thenReturn(true);

        CreateCollectionReq.CollectionSchema currentSchema = MilvusClientV2.CreateSchema();
        currentSchema.addField(io.milvus.v2.service.collection.request.AddFieldReq.builder().fieldName("pk")
                .dataType(DataType.Int64).isPrimaryKey(true).autoID(true).build());
        currentSchema.addField(io.milvus.v2.service.collection.request.AddFieldReq.builder().fieldName("text")
                .dataType(DataType.VarChar).maxLength(65535).build());
        currentSchema.addField(io.milvus.v2.service.collection.request.AddFieldReq.builder().fieldName("vector")
                .dataType(DataType.FloatVector).dimension(3).build());
        currentSchema.addField(io.milvus.v2.service.collection.request.AddFieldReq.builder().fieldName("metadata")
                .dataType(DataType.JSON).build());
        when(client.describeCollection(any()))
                .thenReturn(DescribeCollectionResp.builder().collectionName("kb_chunks").collectionSchema(currentSchema)
                        .properties(Map.of("schema_version", "1", "distance_metric", "COSINE")).build());

        QueryIterator iterator = mock(QueryIterator.class);
        QueryResultsWrapper.RowRecord row = new QueryResultsWrapper.RowRecord();
        row.put("pk", 1L);
        row.put("text", "hello");
        row.put("vector", List.of(1.0f, 0.0f, 0.5f));
        row.put("metadata", new LinkedHashMap<>(Map.of("source", "unit")));
        when(client.queryIterator(any())).thenReturn(iterator);
        when(iterator.next()).thenReturn(List.of(row), List.of());

        MilvusVectorStore store = new MilvusVectorStore(client, new VectorStoreConfig("milvus", "kb_chunks"), "vector");
        store.updateSchema("kb_chunks", List.of(new AddScalarFieldOperation(new OperationMetadata(2, "add field"),
                "user_profile", "nickname", "string", "unknown")));

        ArgumentCaptor<CreateCollectionReq> createCaptor = ArgumentCaptor.forClass(CreateCollectionReq.class);
        verify(client).createCollection(createCaptor.capture());
        assertTrue(createCaptor.getValue().getCollectionName().startsWith("kb_chunks_migration_"));
        assertEquals(DataType.VarChar,
                createCaptor.getValue().getCollectionSchema().getField("nickname").getDataType());

        ArgumentCaptor<QueryIteratorReq> iteratorCaptor = ArgumentCaptor.forClass(QueryIteratorReq.class);
        verify(client).queryIterator(iteratorCaptor.capture());
        assertEquals("kb_chunks", iteratorCaptor.getValue().getCollectionName());

        ArgumentCaptor<InsertReq> insertCaptor = ArgumentCaptor.forClass(InsertReq.class);
        verify(client).insert(insertCaptor.capture());
        JsonObject migrated = insertCaptor.getValue().getData().get(0);
        assertFalse(migrated.has("pk"));
        assertEquals("unknown", migrated.get("nickname").getAsString());

        verify(client).dropCollection(any(DropCollectionReq.class));
        ArgumentCaptor<RenameCollectionReq> renameCaptor = ArgumentCaptor.forClass(RenameCollectionReq.class);
        verify(client).renameCollection(renameCaptor.capture());
        assertEquals("kb_chunks", renameCaptor.getValue().getNewCollectionName());
    }
}
