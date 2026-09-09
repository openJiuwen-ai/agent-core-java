/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.foundation.store.vector.InMemoryVectorStore;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Verifies the vector-store providers available from the Core artifact alone.
 *
 * @since 0.1.15
 */
class VectorStoreFactoryCoreTest {
    @Test
    void coreServiceLoaderProvidesOnlyInMemoryStore() {
        List<String> providerTypes = ServiceLoader.load(VectorStoreProvider.class).stream()
                .map(provider -> provider.get().typeName())
                .toList();

        assertTrue(providerTypes.contains("in_memory"));
        assertFalse(providerTypes.contains("chroma"));
        assertFalse(providerTypes.contains("milvus"));
        assertFalse(providerTypes.contains("pgvector"));
        assertFalse(providerTypes.contains("elasticsearch"));
    }

    @Test
    void coreFactoryCreatesInMemoryStoreAndAlias() {
        assertInstanceOf(InMemoryVectorStore.class, VectorStoreFactory.create("in_memory"));
        assertInstanceOf(InMemoryVectorStore.class, VectorStoreFactory.create("memory"));
    }

    @Test
    void inMemoryStorePreservesPublicVectorFieldAndUpdatedSchema() {
        String collectionName = "foundation_vector_adapter_test";
        InMemoryVectorStore store = new InMemoryVectorStore(Map.of("collection_name", collectionName));
        CollectionSchema schema = CollectionSchema.fromFields(
                List.of(FieldSchema.builder().name("id").dtype(VectorDataType.VARCHAR).isPrimary(true).build(),
                        FieldSchema.builder().name("embedding").dtype(VectorDataType.FLOAT_VECTOR).dim(2).build()),
                "adapter test", false);
        store.createCollection(collectionName, schema, Map.of());
        store.addDocs(collectionName,
                List.of(Map.of("id", "first", "text", "first document", "embedding", List.of(1.0F, 0.0F))),
                Map.of());

        List<VectorSearchResult> results = store.search(collectionName, List.of(1.0F, 0.0F), "embedding", 1, null,
                Map.of());

        assertEquals(1, results.size());
        assertEquals("first", results.get(0).getFields().get("id"));
        assertTrue(store.getSchema(collectionName, Map.of()).hasField("embedding"));
    }
}
