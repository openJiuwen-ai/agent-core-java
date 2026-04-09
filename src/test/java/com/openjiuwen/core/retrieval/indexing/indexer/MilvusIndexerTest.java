/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
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
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.QueryResp;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MilvusIndexerTest {

    @Test
    void buildIndexCreatesCollectionAndWritesLogicalChunkFields() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(false);
        when(client.createSchema()).thenCallRealMethod();

        MilvusVectorStore store = new MilvusVectorStore(client, new VectorStoreConfig("milvus", "base"), "hybrid");
        MilvusIndexer indexer = new MilvusIndexer(store);
        BaseCallback callback = new BaseCallback();

        boolean built = indexer.buildIndex(
                List.of(TextChunk.fromDocument(new Document("doc-1", "hello world", Map.of("source", "web")), "hello world")),
                new IndexConfig("kb_chunks", "hybrid"),
                new FixedEmbedding(),
                Map.of("callback", callback));

        assertTrue(built);
        assertEquals(1, callback.getCallCounter());

        ArgumentCaptor<CreateCollectionReq> collectionCaptor = ArgumentCaptor.forClass(CreateCollectionReq.class);
        verify(client).createCollection(collectionCaptor.capture());
        assertEquals("kb_chunks", collectionCaptor.getValue().getCollectionName());
        assertTrue(collectionCaptor.getValue().getCollectionSchema().getField("vector") != null);
        assertTrue(collectionCaptor.getValue().getCollectionSchema().getField("sparse_vector") != null);

        ArgumentCaptor<InsertReq> insertCaptor = ArgumentCaptor.forClass(InsertReq.class);
        verify(client).insert(insertCaptor.capture());
        JsonObject first = insertCaptor.getValue().getData().get(0);
        assertTrue(first.has("chunk_id"));
        assertTrue(first.has("doc_id"));
        assertTrue(first.has("text"));
        assertFalse(first.has("id"));

        ArgumentCaptor<FlushReq> flushCaptor = ArgumentCaptor.forClass(FlushReq.class);
        verify(client).flush(flushCaptor.capture());
        assertEquals(List.of("kb_chunks"), flushCaptor.getValue().getCollectionNames());
    }

    @Test
    void buildIndexRejectsDuplicateDocIds() {
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

        MilvusVectorStore store = new MilvusVectorStore(client, new VectorStoreConfig("milvus", "base"), "hybrid");
        MilvusIndexer indexer = new MilvusIndexer(store);

        assertThrows(BaseError.class, () -> indexer.buildIndex(
                List.of(TextChunk.fromDocument(new Document("doc-1", "duplicate", Map.of()), "duplicate")),
                new IndexConfig("kb_chunks", "hybrid"),
                new FixedEmbedding(),
                Map.of()));

        verify(client, never()).insert(any());
    }

    @Test
    void deleteIndexUsesDocIdFilterInsteadOfPrimaryIds() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(true);
        when(client.delete(any())).thenReturn(DeleteResp.builder().deleteCnt(1).build());

        MilvusVectorStore store = new MilvusVectorStore(client, new VectorStoreConfig("milvus", "base"), "hybrid");
        MilvusIndexer indexer = new MilvusIndexer(store);

        assertTrue(indexer.deleteIndex("doc-1", "kb_chunks", Map.of()));

        ArgumentCaptor<DeleteReq> deleteCaptor = ArgumentCaptor.forClass(DeleteReq.class);
        verify(client).delete(deleteCaptor.capture());
        assertEquals("kb_chunks", deleteCaptor.getValue().getCollectionName());
        assertEquals("doc_id == \"doc-1\"", deleteCaptor.getValue().getFilter());
        verify(client, never()).query(any(QueryReq.class));
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
