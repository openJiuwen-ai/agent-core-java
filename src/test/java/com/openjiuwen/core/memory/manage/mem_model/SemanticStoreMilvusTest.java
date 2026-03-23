/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.memory.manage.mem_model;

import com.google.gson.JsonObject;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.InsertReq;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SemanticStoreMilvusTest {

    @Test
    void createCollectionUsesMilvusBootstrapWithoutEmptyInsert() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(false);
        when(client.createSchema()).thenCallRealMethod();

        SemanticStore semanticStore = new SemanticStore(
                new MilvusVectorStore(client, new VectorStoreConfig("milvus", "memory_base"), "hybrid"));

        semanticStore.createCollection("memory_fragments", 3, Map.of());

        ArgumentCaptor<CreateCollectionReq> collectionCaptor = ArgumentCaptor.forClass(CreateCollectionReq.class);
        verify(client).createCollection(collectionCaptor.capture());
        assertEquals("memory_fragments", collectionCaptor.getValue().getCollectionName());
        assertNotNull(collectionCaptor.getValue().getCollectionSchema().getField("vector"));
        assertNull(collectionCaptor.getValue().getCollectionSchema().getField("sparse_vector"));
        verify(client, never()).insert(any());
        verify(client, never()).flush(any(FlushReq.class));
    }

    @Test
    void addDocsBootstrapsVectorOnlyMilvusCollection() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(false);
        when(client.createSchema()).thenCallRealMethod();

        SemanticStore semanticStore = new SemanticStore(
                new MilvusVectorStore(client, new VectorStoreConfig("milvus", "memory_base"), "hybrid"),
                new FixedEmbedding());

        boolean stored = semanticStore.addDocs(List.of(Map.entry("mem-1", "remember this")), "memory_fragments");

        assertTrue(stored);

        ArgumentCaptor<CreateCollectionReq> collectionCaptor = ArgumentCaptor.forClass(CreateCollectionReq.class);
        verify(client).createCollection(collectionCaptor.capture());
        assertEquals("memory_fragments", collectionCaptor.getValue().getCollectionName());
        assertNotNull(collectionCaptor.getValue().getCollectionSchema().getField("vector"));
        assertNull(collectionCaptor.getValue().getCollectionSchema().getField("sparse_vector"));

        ArgumentCaptor<InsertReq> insertCaptor = ArgumentCaptor.forClass(InsertReq.class);
        verify(client).insert(insertCaptor.capture());
        assertEquals("memory_fragments", insertCaptor.getValue().getCollectionName());
        JsonObject row = insertCaptor.getValue().getData().get(0);
        assertTrue(row.has("chunk_id"));
        assertTrue(row.has("doc_id"));
        assertTrue(row.has("text"));
        assertTrue(row.has("vector"));
    }

    private static final class FixedEmbedding implements Embedding {
        @Override
        public List<Float> embedQuery(String text) {
            return List.of(1.0f, 0.0f, 0.5f);
        }

        @Override
        public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize) {
            return texts.stream().map(this::embedQuery).toList();
        }

        @Override
        public int getDimension() {
            return 3;
        }
    }
}
