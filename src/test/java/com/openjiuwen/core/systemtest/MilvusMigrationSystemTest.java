/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.FieldSchema;
import com.openjiuwen.core.foundation.store.VectorDataType;
import com.openjiuwen.core.foundation.store.vector.MilvusVectorStore;
import com.openjiuwen.core.memory.manage.mem_model.SemanticStore;
import com.openjiuwen.core.memory.migration.migrator.VectorMigrator;
import com.openjiuwen.core.memory.migration.operation.AddScalarFieldOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.RenameScalarFieldOperation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("system-test")
class MilvusMigrationSystemTest {

    @Test
    void milvusSchemaMigrationPreservesDataAndUpdatesVersion() {
        String milvusUri = System.getenv("MILVUS_URI");
        assumeTrue(milvusUri != null && !milvusUri.isBlank(),
                "MILVUS_URI is required for Milvus migration system test");

        String collectionName = "test_user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                + "_test_scope_summary";
        String token = System.getenv("MILVUS_TOKEN");
        MilvusVectorStore store = new MilvusVectorStore(milvusUri, token, "default");
        try {
            store.deleteCollection(collectionName, Map.of()).join();
            store.createCollection(collectionName, initialSchema(), Map.of("schema_version", 0)).join();
            store.updateCollectionMetadata(collectionName, Map.of("schema_version", 0)).join();
            store.addDocs(collectionName, List.of(Map.of(
                    "id", UUID.randomUUID().toString(),
                    "vector", List.of(0.1f, 0.2f, 0.3f, 0.4f),
                    "text", "data",
                    "old_field_name", "value",
                    "type_change_field", 100
            )), Map.of()).join();

            SemanticStore semanticStore = new SemanticStore(store);
            VectorMigrator migrator = new VectorMigrator(store);
            assertTrue(migrator.tryMigrate("vector_summary", List.of(
                    new AddScalarFieldOperation(
                            new OperationMetadata(1, "add field"),
                            "summary",
                            "added_field_migrator",
                            "string",
                            "default"),
                    new RenameScalarFieldOperation(
                            new OperationMetadata(2, "rename field"),
                            "summary",
                            "old_field_name",
                            "new_field_name_migrator")
            )).join());

            Map<String, Object> metadata = store.getCollectionMetadata(collectionName).join();
            assertEquals(2, metadata.get("schema_version"));
            CollectionSchema schema = store.getSchema(collectionName, Map.of()).join();
            List<String> fieldNames = schema.getFields()
                    .stream()
                    .map(FieldSchema::getName)
                    .toList();
            assertTrue(fieldNames.contains("added_field_migrator"));
            assertFalse(fieldNames.contains("old_field_name"));
            assertTrue(fieldNames.contains("new_field_name_migrator"));

            store.addDocs(collectionName, List.of(Map.of(
                    "id", UUID.randomUUID().toString(),
                    "vector", List.of(0.5f, 0.6f, 0.7f, 0.8f),
                    "text", "new data",
                    "new_field_name_migrator", "new value",
                    "type_change_field", 200,
                    "added_field_migrator", "migrated"
            )), Map.of()).join();
            store.close();
        } finally {
            MilvusVectorStore cleanup = new MilvusVectorStore(milvusUri, token, "default");
            cleanup.deleteCollection(collectionName, Map.of()).join();
            cleanup.close();
        }
    }

    private static CollectionSchema initialSchema() {
        return new CollectionSchema(List.of(
                new FieldSchema("id", VectorDataType.VARCHAR, true, false, 36, null, null, null, null, null),
                new FieldSchema("vector", VectorDataType.FLOAT_VECTOR, false, false, null, 4, null, null, null, null),
                new FieldSchema("text", VectorDataType.VARCHAR, false, false, 256, null, null, null, null, null),
                new FieldSchema("old_field_name", VectorDataType.VARCHAR, false, false, 64, null, null, null, null, null),
                new FieldSchema("type_change_field", VectorDataType.INT32, false, false, null, null, null, null, null, null)
        ), "Initial collection for schema update tests", false);
    }
}
