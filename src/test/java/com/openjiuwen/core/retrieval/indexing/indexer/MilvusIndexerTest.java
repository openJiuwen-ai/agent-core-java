/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.indexer;

import com.google.gson.JsonObject;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.BaseCallback;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.collection.response.GetCollectionStatsResp;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.QueryResp;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MilvusIndexer.
 *
 * <p>Mirrors Python's {@code TestMilvusIndexer} in
 * {@code tests.unit_tests.core.retrieval.indexing.indexer.test_milvus_indexer}.</p>
 */
class MilvusIndexerTest {

    @Test
    void testInitSuccess() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        VectorStoreConfig config = new VectorStoreConfig("milvus", "name", "test_collection", "cosine");
        MilvusVectorStore store = new MilvusVectorStore(client, config, "hybrid");
        MilvusIndexer indexer = new MilvusIndexer(store);

        assertEquals("name", indexer.getDatabaseName());
        assertEquals("hybrid", indexer.getIndexType());
        assertEquals("text", indexer.getTextField());
        assertEquals("vector", indexer.getVectorField());
        assertEquals("doc_id", indexer.getDocIdField());
    }

    @Test
    void testInitWithToken() throws Exception {
        try (MockedConstruction<MilvusClientV2> ignored = Mockito.mockConstruction(MilvusClientV2.class)) {
            VectorStoreConfig config = new VectorStoreConfig("milvus", "test_collection");
            MilvusIndexer indexer = new MilvusIndexer(config, "http://localhost:19530", "test_token", "hybrid");

            assertEquals("test_token", vectorStore(indexer).getMilvusToken());
            assertEquals("http://localhost:19530", vectorStore(indexer).getMilvusUri());
        }
    }

    @Test
    void testInitWithMilvusAlias() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        MilvusVectorStore store = new MilvusVectorStore(client, new VectorStoreConfig("milvus", "base"), "hybrid");

        MilvusVectorStore scoped = (MilvusVectorStore) store.withCollection("idx_1_2");

        assertEquals("base", store.getCollectionName());
        assertEquals("idx_1_2", scoped.getCollectionName());
    }

    @Test
    void testInitWithCustomFields() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        MilvusIndexer indexer = new MilvusIndexer(new MilvusVectorStore(
                client, new VectorStoreConfig("milvus", "test_collection"), "hybrid"));

        assertEquals("text", indexer.getTextField());
        assertEquals("vector", indexer.getVectorField());
        assertEquals("doc_id", indexer.getDocIdField());
    }

    @Test
    void testInitWithInvalidVectorField() {
        MilvusClientV2 client = mock(MilvusClientV2.class);

        assertThrows(BaseError.class, () -> new MilvusVectorStore(
                client, new VectorStoreConfig("milvus", "test_collection"), "invalid-index-type"));
    }

    @Test
    void testBuildIndexVectorType() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(false);
        when(client.createSchema()).thenCallRealMethod();
        MilvusIndexer indexer = new MilvusIndexer(new MilvusVectorStore(
                client, new VectorStoreConfig("milvus", "base"), "hybrid"));
        BaseCallback callback = new BaseCallback();

        boolean built = indexer.buildIndex(
                List.of(TextChunk.fromDocument(new Document("doc-1", "hello world", Map.of("source", "web")),
                        "hello world")),
                new IndexConfig("kb_chunks", "vector"),
                new FixedEmbedding(),
                Map.of("callback", callback));

        assertTrue(built);
        assertEquals(1, callback.getCallCounter());

        ArgumentCaptor<CreateCollectionReq> collectionCaptor = ArgumentCaptor.forClass(CreateCollectionReq.class);
        verify(client).createCollection(collectionCaptor.capture());
        assertEquals("kb_chunks", collectionCaptor.getValue().getCollectionName());
        assertNotNull(collectionCaptor.getValue().getCollectionSchema().getField("vector"));

        ArgumentCaptor<InsertReq> insertCaptor = ArgumentCaptor.forClass(InsertReq.class);
        verify(client).insert(insertCaptor.capture());
        JsonObject first = insertCaptor.getValue().getData().getFirst();
        assertTrue(first.has("chunk_id"));
        assertTrue(first.has("doc_id"));
        assertTrue(first.has("text"));
        assertTrue(first.has("vector"));
        assertFalse(first.has("id"));
    }

    @Test
    void testBuildIndexBm25Type() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(false);
        when(client.createSchema()).thenCallRealMethod();
        MilvusIndexer indexer = new MilvusIndexer(new MilvusVectorStore(
                client, new VectorStoreConfig("milvus", "base"), "bm25"));

        boolean built = indexer.buildIndex(
                List.of(new TextChunk("chunk-1", "chunk 1", "doc-1")),
                new IndexConfig("kb_bm25", "bm25"),
                null,
                Map.of());

        assertTrue(built);
        ArgumentCaptor<InsertReq> insertCaptor = ArgumentCaptor.forClass(InsertReq.class);
        verify(client).insert(insertCaptor.capture());
        JsonObject first = insertCaptor.getValue().getData().getFirst();
        assertFalse(first.has("vector"));
    }

    @Test
    void testBuildIndexVectorTypeWithoutEmbedModel() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        MilvusIndexer indexer = new MilvusIndexer(new MilvusVectorStore(
                client, new VectorStoreConfig("milvus", "base"), "vector"));

        BaseError error = assertThrows(BaseError.class, () -> indexer.buildIndex(
                List.of(new TextChunk("chunk-1", "chunk 1", "doc-1")),
                new IndexConfig("kb_vector", "vector"),
                null,
                Map.of()));

        assertTrue(error.getMessage().contains("embed_model"));
    }

    @Test
    void testBuildIndexWithDuplicateDocIds() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(true);
        when(client.query(any())).thenReturn(QueryResp.builder()
                .queryResults(List.of(QueryResp.QueryResult.builder()
                        .entity(Map.of(
                                "chunk_id", "chunk-1",
                                "text", "existing",
                                "doc_id", "doc-1",
                                "metadata", Map.of("doc_id", "doc-1")))
                        .build()))
                .build());
        MilvusIndexer indexer = new MilvusIndexer(new MilvusVectorStore(
                client, new VectorStoreConfig("milvus", "base"), "hybrid"));

        BaseError error = assertThrows(BaseError.class, () -> indexer.buildIndex(
                List.of(TextChunk.fromDocument(new Document("doc-1", "duplicate", Map.of()), "duplicate")),
                new IndexConfig("kb_chunks", "vector"),
                new FixedEmbedding(),
                Map.of()));

        assertTrue(error.getMessage().contains("some documents with same doc_id already exist"));
        verify(client, never()).insert(any());
    }

    @Test
    void testUpdateIndex() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        MilvusIndexer indexer = Mockito.spy(new MilvusIndexer(new MilvusVectorStore(
                client, new VectorStoreConfig("milvus", "base"), "vector")));
        List<TextChunk> chunks = List.of(new TextChunk("chunk-1", "updated chunk", "doc-1"));
        IndexConfig config = new IndexConfig("kb_vector", "vector");
        FixedEmbedding embedding = new FixedEmbedding();

        doReturn(true).when(indexer).deleteIndex("doc-1", "kb_vector", Map.of());
        doReturn(true).when(indexer).buildIndex(chunks, config, embedding, Map.of());

        assertTrue(indexer.updateIndex(chunks, "doc-1", config, embedding, Map.of()));
        verify(indexer).deleteIndex("doc-1", "kb_vector", Map.of());
        verify(indexer).buildIndex(chunks, config, embedding, Map.of());
    }

    @Test
    void testDeleteIndexSuccess() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(true);
        when(client.delete(any())).thenReturn(DeleteResp.builder().deleteCnt(2).build());
        MilvusIndexer indexer = new MilvusIndexer(new MilvusVectorStore(
                client, new VectorStoreConfig("milvus", "base"), "hybrid"));

        assertTrue(indexer.deleteIndex("doc-1", "kb_chunks", Map.of()));

        ArgumentCaptor<DeleteReq> deleteCaptor = ArgumentCaptor.forClass(DeleteReq.class);
        verify(client).delete(deleteCaptor.capture());
        assertEquals("kb_chunks", deleteCaptor.getValue().getCollectionName());
        assertEquals("doc_id == \"doc-1\"", deleteCaptor.getValue().getFilter());

        ArgumentCaptor<FlushReq> flushCaptor = ArgumentCaptor.forClass(FlushReq.class);
        verify(client).flush(flushCaptor.capture());
        assertEquals(List.of("kb_chunks"), flushCaptor.getValue().getCollectionNames());
    }

    @Test
    void testDeleteIndexNotFound() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(false);
        MilvusIndexer indexer = new MilvusIndexer(new MilvusVectorStore(
                client, new VectorStoreConfig("milvus", "base"), "hybrid"));

        assertFalse(indexer.deleteIndex("doc-1", "missing_index", Map.of()));
        verify(client, never()).delete(any());
    }

    @Test
    void testIndexExistsTrue() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(true);
        MilvusIndexer indexer = new MilvusIndexer(new MilvusVectorStore(
                client, new VectorStoreConfig("milvus", "base"), "hybrid"));

        assertTrue(indexer.indexExists("kb_chunks"));
    }

    @Test
    void testIndexExistsFalse() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(false);
        MilvusIndexer indexer = new MilvusIndexer(new MilvusVectorStore(
                client, new VectorStoreConfig("milvus", "base"), "hybrid"));

        assertFalse(indexer.indexExists("missing_index"));
    }

    @Test
    void testGetIndexInfoExists() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        DescribeCollectionResp describe = mock(DescribeCollectionResp.class);
        GetCollectionStatsResp stats = mock(GetCollectionStatsResp.class);
        when(client.hasCollection(any())).thenReturn(true);
        when(client.describeCollection(any())).thenReturn(describe);
        when(client.getCollectionStats(any())).thenReturn(stats);
        when(describe.getFieldNames()).thenReturn(List.of("chunk_id", "doc_id", "text", "vector"));
        when(describe.getVectorFieldNames()).thenReturn(List.of("vector"));
        when(stats.getNumOfEntities()).thenReturn(100L);
        MilvusIndexer indexer = new MilvusIndexer(new MilvusVectorStore(
                client, new VectorStoreConfig("milvus", "base"), "hybrid"));

        Map<String, Object> info = indexer.getIndexInfo("test_index");

        assertEquals(true, info.get("exists"));
        assertEquals("test_index", info.get("index_name"));
        assertEquals(100L, info.get("count"));
        assertEquals(List.of("vector"), info.get("vector_fields"));
    }

    @Test
    void testGetIndexInfoNotExists() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(false);
        MilvusIndexer indexer = new MilvusIndexer(new MilvusVectorStore(
                client, new VectorStoreConfig("milvus", "base"), "hybrid"));

        Map<String, Object> info = indexer.getIndexInfo("missing_index");

        assertEquals(false, info.get("exists"));
        assertEquals("missing_index", info.get("index_name"));
        assertEquals(0L, info.get("count"));
    }

    @Test
    void testClose() {
        try (MockedConstruction<MilvusClientV2> mocked = Mockito.mockConstruction(MilvusClientV2.class)) {
            MilvusIndexer indexer = new MilvusIndexer(
                    new VectorStoreConfig("milvus", "test_collection"), "http://localhost:19530", "hybrid");

            indexer.close();

            verify(mocked.constructed().getFirst()).close();
        }
    }

    @Test
    void deleteIndexUsesDocIdFilterInsteadOfPrimaryIds() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(true);
        when(client.delete(any())).thenReturn(DeleteResp.builder().deleteCnt(1).build());
        MilvusIndexer indexer = new MilvusIndexer(new MilvusVectorStore(
                client, new VectorStoreConfig("milvus", "base"), "hybrid"));

        assertTrue(indexer.deleteIndex("doc-1", "kb_chunks", Map.of()));

        ArgumentCaptor<DeleteReq> deleteCaptor = ArgumentCaptor.forClass(DeleteReq.class);
        verify(client).delete(deleteCaptor.capture());
        assertEquals("doc_id == \"doc-1\"", deleteCaptor.getValue().getFilter());
        verify(client, never()).query(any(QueryReq.class));
    }

    private static MilvusVectorStore vectorStore(MilvusIndexer indexer) throws Exception {
        Field field = MilvusIndexer.class.getDeclaredField("vectorStore");
        field.setAccessible(true);
        return (MilvusVectorStore) field.get(indexer);
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
