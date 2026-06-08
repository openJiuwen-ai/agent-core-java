/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.store.graph.milvus;

import com.openjiuwen.core.foundation.store.graph.GraphStoreIndexConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStoreStorageConfig;
import com.openjiuwen.core.foundation.store.graph.milvus.GenerateMilvusSchema;
import com.openjiuwen.core.foundation.store.vector_fields.MilvusAUTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerateMilvusSchemaTest {

    @Test
    void entityCollectionSchemaHasExpectedFields() {
        GenerateMilvusSchema.SchemaResult result = GenerateMilvusSchema.generateSchemaAndIndex(
                GenerateMilvusSchema.ENTITY_COLLECTION,
                new GraphStoreStorageConfig(),
                new GraphStoreIndexConfig(new MilvusAUTO(), "cosine", null, null, null),
                64,
                true
        );

        assertTrue(result.getFields().containsKey("name"));
        assertTrue(result.getFields().containsKey("name_embedding"));
        assertTrue(result.getFields().containsKey("relations"));
        assertTrue(result.getFields().containsKey("episodes"));
        assertEquals("semantic_embedding_name", result.getIndexes().getFirst().get("index_name"));
    }

    @Test
    void relationCollectionSchemaHasExpectedFields() {
        GenerateMilvusSchema.SchemaResult result = GenerateMilvusSchema.generateSchemaAndIndex(
                GenerateMilvusSchema.RELATION_COLLECTION,
                new GraphStoreStorageConfig(),
                new GraphStoreIndexConfig(new MilvusAUTO(), "cosine", null, null, null),
                64,
                true
        );

        assertTrue(result.getFields().containsKey("valid_since"));
        assertTrue(result.getFields().containsKey("valid_until"));
        assertTrue(result.getFields().containsKey("lhs"));
        assertTrue(result.getFields().containsKey("rhs"));
    }

    @Test
    void episodeCollectionSchemaHasExpectedFields() {
        GenerateMilvusSchema.SchemaResult result = GenerateMilvusSchema.generateSchemaAndIndex(
                GenerateMilvusSchema.EPISODE_COLLECTION,
                new GraphStoreStorageConfig(),
                new GraphStoreIndexConfig(new MilvusAUTO(), "cosine", null, null, null),
                64,
                true
        );

        assertTrue(result.getFields().containsKey("valid_since"));
        assertTrue(result.getFields().containsKey("entities"));
    }

    @Test
    void unknownCollectionRaisesError() {
        assertThrows(IllegalArgumentException.class, () -> GenerateMilvusSchema.generateSchemaAndIndex(
                "UNKNOWN_COLLECTION",
                new GraphStoreStorageConfig(),
                new GraphStoreIndexConfig(new MilvusAUTO(), "cosine", null, null, null),
                64,
                true
        ));
    }

    @Test
    void metricTypeDotMapsToIp() {
        GenerateMilvusSchema.SchemaResult result = GenerateMilvusSchema.generateSchemaAndIndex(
                GenerateMilvusSchema.ENTITY_COLLECTION,
                new GraphStoreStorageConfig(),
                new GraphStoreIndexConfig(new MilvusAUTO(), "dot", null, null, null),
                64,
                true
        );

        assertEquals("IP", result.getMetricType());
    }
}
