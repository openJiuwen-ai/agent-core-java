/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.systemtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.openjiuwen.core.memory.manage.mem_model.SemanticStore;
import com.openjiuwen.core.memory.migration.migrator.VectorMigrator;
import com.openjiuwen.core.memory.migration.operation.AddScalarFieldOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.RenameScalarFieldOperation;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore;
import com.openjiuwen.spi.store.vector.CollectionSchema;
import com.openjiuwen.spi.store.vector.FieldSchema;
import com.openjiuwen.spi.store.vector.VectorDataType;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag("system-test")
class MilvusMigrationSystemTest {
    @Test
    void milvusSchemaMigrationPreservesDataAndUpdatesVersion() {
        String milvusUri = System.getenv("MILVUS_URI");
        assumeTrue(milvusUri != null && !milvusUri.isBlank(),
                "MILVUS_URI is required for Milvus migration system test");

        String collectionName =
            "test_user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "_test_scope_summary";
        String token = System.getenv("MILVUS_TOKEN");
        try (MilvusVectorStore store = new MilvusVectorStore(
                new VectorStoreConfig("milvus", "default", collectionName, "cosine"), milvusUri, token, "vector")) {
            store.deleteTable(collectionName);
            store.createCollection(collectionName, initialSchema(), Map.of("schema_version", 0));
            store.updateCollectionMetadata(collectionName, Map.of("schema_version", 0));
            store.add(List.of(Map.of("id", UUID.randomUUID().toString(), "vector", List.of(0.1f, 0.2f, 0.3f, 0.4f),
                    "text", "data", "old_field_name", "value", "type_change_field", 100)), null, Map.of());

            SemanticStore semanticStore = new SemanticStore(store);
            VectorMigrator migrator = new VectorMigrator(semanticStore);
            assertTrue(migrator.tryMigrate("vector_summary",
                    List.of(new AddScalarFieldOperation(new OperationMetadata(1, "add field"), "summary",
                            "added_field_migrator", "string", "default"),
                            new RenameScalarFieldOperation(new OperationMetadata(2, "rename field"), "summary",
                                    "old_field_name", "new_field_name_migrator"))));

            Map<String, Object> metadata = store.getCollectionMetadata(collectionName);
            assertEquals(2, metadata.get("schema_version"));
            List<String> fieldNames =
                store.getSchema(collectionName).getFields().stream().map(FieldSchema::getName).toList();
            assertTrue(fieldNames.contains("added_field_migrator"));
            assertFalse(fieldNames.contains("old_field_name"));
            assertTrue(fieldNames.contains("new_field_name_migrator"));

            store.withCollection(collectionName)
                    .add(List.of(Map.of("id", UUID.randomUUID().toString(), "vector", List.of(0.5f, 0.6f, 0.7f, 0.8f),
                            "text", "new data", "new_field_name_migrator", "new value", "type_change_field", 200,
                            "added_field_migrator", "migrated")), null, Map.of());

            List<SearchResult> results =
                store.withCollection(collectionName).search(List.of(0.1f, 0.1f, 0.1f, 0.1f), 5, null, Map.of());
            assertTrue(results.size() >= 2);
        } finally {
            try (MilvusVectorStore cleanup = new MilvusVectorStore(
                    new VectorStoreConfig("milvus", "default", collectionName, "cosine"), milvusUri, token, "vector")) {
                cleanup.deleteTable(collectionName);
            }
        }
    }

    private static CollectionSchema initialSchema() {
        return CollectionSchema.fromFields(List.of(
                FieldSchema.builder().name("id").dtype(VectorDataType.VARCHAR).isPrimary(true).maxLength(36).build(),
                FieldSchema.builder().name("vector").dtype(VectorDataType.FLOAT_VECTOR).dim(4).build(),
                FieldSchema.builder().name("text").dtype(VectorDataType.VARCHAR).maxLength(256).build(),
                FieldSchema.builder().name("old_field_name").dtype(VectorDataType.VARCHAR).maxLength(64).build(),
                FieldSchema.builder().name("type_change_field").dtype(VectorDataType.INT32).build()),
                "Initial collection for schema update tests", false);
    }
}
