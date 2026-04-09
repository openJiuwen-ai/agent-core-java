/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
package com.openjiuwen.core.retrieval;

import com.google.gson.JsonObject;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.indexing.indexer.MilvusIndexer;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.CharChunker;
import com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.response.QueryResp;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MilvusKnowledgeBaseTest {

    @Test
    void simpleKnowledgeBaseAutoResolvesMilvusIndexer() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(false);
        when(client.createSchema()).thenCallRealMethod();
        when(client.query(any(QueryReq.class))).thenReturn(QueryResp.builder().queryResults(List.of()).build());

        SimpleKnowledgeBase knowledgeBase = new SimpleKnowledgeBase(
                new KnowledgeBaseConfig("milvus_kb", "vector", false, 64, 8),
                new MilvusVectorStore(client, new VectorStoreConfig("milvus", "kb_base"), "vector"),
                new FixedEmbedding(),
                null,
                new CharChunker(64, 8),
                null,
                null,
                null);

        List<String> docIds = knowledgeBase.addDocuments(List.of(
                new Document("doc-1", "hello world from milvus", Map.of("source", "test"))));

        assertEquals(List.of("doc-1"), docIds);
        assertInstanceOf(MilvusIndexer.class, knowledgeBase.getIndexManager());

        ArgumentCaptor<CreateCollectionReq> collectionCaptor = ArgumentCaptor.forClass(CreateCollectionReq.class);
        verify(client).createCollection(collectionCaptor.capture());
        assertEquals("kb_milvus_kb_chunks", collectionCaptor.getValue().getCollectionName());

        ArgumentCaptor<InsertReq> insertCaptor = ArgumentCaptor.forClass(InsertReq.class);
        verify(client).insert(insertCaptor.capture());
        JsonObject row = insertCaptor.getValue().getData().get(0);
        assertEquals("doc-1", row.get("doc_id").getAsString());
        assertEquals("hello world from milvus", row.get("text").getAsString());

        ArgumentCaptor<FlushReq> flushCaptor = ArgumentCaptor.forClass(FlushReq.class);
        verify(client).flush(flushCaptor.capture());
        assertEquals(List.of("kb_milvus_kb_chunks"), flushCaptor.getValue().getCollectionNames());
    }

    private static final class FixedEmbedding implements Embedding {
        @Override
        public List<Float> embedQuery(String text) {
            return List.of(1.0f, 0.0f);
        }

        @Override
        public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize) {
            return texts.stream().map(this::embedQuery).toList();
        }

        @Override
        public int getDimension() {
            return 2;
        }
    }
}
