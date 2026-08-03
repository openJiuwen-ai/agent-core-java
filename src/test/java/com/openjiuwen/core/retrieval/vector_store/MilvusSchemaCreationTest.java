/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.google.gson.JsonObject;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.vector.request.InsertReq;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MilvusSchemaCreationTest {

    @Test
    void createCollectionUsesPythonDeclaredSchemaAndInsertPreservesScalarFields() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(true);
        when(client.createSchema()).thenCallRealMethod();
        VectorStoreConfig config = new VectorStoreConfig("milvus", "memory_base");
        MilvusVectorStore store = new MilvusVectorStore(
                client,
                config,
                "vector");

        store.add(List.of(Map.of(
                "id", "mem-1",
                "vector", List.of(0.1f, 0.2f, 0.3f, 0.4f),
                "text", "data",
                "old_field_name", "value",
                "type_change_field", 100
        )), 128, Map.of()).join();

        ArgumentCaptor<InsertReq> insertCaptor = ArgumentCaptor.forClass(InsertReq.class);
        verify(client).insert(insertCaptor.capture());
        JsonObject row = insertCaptor.getValue().getData().get(0);
        assertTrue(row.has("id"));
        assertTrue(row.has("old_field_name"));
        assertTrue(row.has("type_change_field"));
    }
}
