/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.spi.store.vector.CollectionSchema;
import com.openjiuwen.spi.store.vector.FieldSchema;
import com.openjiuwen.spi.store.vector.VectorDataType;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.vector.request.InsertReq;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

class MilvusSchemaCreationTest {
    @Test
    void createCollectionUsesPythonDeclaredSchemaAndInsertPreservesScalarFields() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.hasCollection(any())).thenReturn(true);
        when(client.createSchema()).thenCallRealMethod();
        MilvusVectorStore store = new MilvusVectorStore(client, new VectorStoreConfig("milvus", "memory_base"),
                "vector", Map.of("vector_field", "vector"));

        CollectionSchema schema = CollectionSchema.fromFields(List.of(
                FieldSchema.builder().name("id").dtype(VectorDataType.VARCHAR).isPrimary(true).maxLength(36).build(),
                FieldSchema.builder().name("vector").dtype(VectorDataType.FLOAT_VECTOR).dim(4).build(),
                FieldSchema.builder().name("text").dtype(VectorDataType.VARCHAR).maxLength(256).build(),
                FieldSchema.builder().name("old_field_name").dtype(VectorDataType.VARCHAR).maxLength(64).build(),
                FieldSchema.builder().name("type_change_field").dtype(VectorDataType.INT32).build()),
                "Initial collection for schema update tests", false);

        store.createCollection("memory_migration", schema, Map.of("schema_version", 0));
        store.withCollection("memory_migration").add(List.of(Map.of("id", "mem-1", "vector",
                List.of(0.1f, 0.2f, 0.3f, 0.4f), "text", "data", "old_field_name", "value", "type_change_field", 100)),
                null, Map.of());

        ArgumentCaptor<CreateCollectionReq> collectionCaptor = ArgumentCaptor.forClass(CreateCollectionReq.class);
        verify(client).createCollection(collectionCaptor.capture());
        CreateCollectionReq.CollectionSchema milvusSchema = collectionCaptor.getValue().getCollectionSchema();
        assertNotNull(milvusSchema.getField("id"));
        assertNotNull(milvusSchema.getField("vector"));
        assertNotNull(milvusSchema.getField("old_field_name"));
        assertNotNull(milvusSchema.getField("type_change_field"));

        ArgumentCaptor<InsertReq> insertCaptor = ArgumentCaptor.forClass(InsertReq.class);
        verify(client).insert(insertCaptor.capture());
        JsonObject row = insertCaptor.getValue().getData().get(0);
        assertTrue(row.has("id"));
        assertTrue(row.has("old_field_name"));
        assertTrue(row.has("type_change_field"));
    }
}
