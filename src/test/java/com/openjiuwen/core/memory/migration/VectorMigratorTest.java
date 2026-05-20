/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration;

import com.openjiuwen.core.memory.manage.mem_model.SemanticStore;
import com.openjiuwen.core.memory.migration.migrator.VectorMigrator;
import com.openjiuwen.core.memory.migration.operation.AddScalarFieldOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VectorMigratorTest {

    @Test
    void tryMigrateAppliesOperationsAndUpdatesMetadata() {
        InMemoryVectorStore vectorStore = new InMemoryVectorStore("vector_user_profile");
        SemanticStore semanticStore = new SemanticStore(vectorStore);
        String collectionName = "alice_scope_migration_test_user_profile";
        semanticStore.createCollection(collectionName, 3, Map.of());
        semanticStore.updateCollectionMetadata(collectionName, Map.of("schema_version", 1));
        vectorStore.withCollection(collectionName).add(List.of(Map.of(
                "id", "1",
                "text", "hello",
                "vector", List.of(1.0f, 2.0f, 3.0f)
        )), null, Map.of());

        VectorMigrator migrator = new VectorMigrator(semanticStore);
        AddScalarFieldOperation op = new AddScalarFieldOperation(
                new OperationMetadata(2, "add field"),
                "user_profile",
                "nickname",
                "string",
                "unknown");

        assertTrue(migrator.tryMigrate("vector_user_profile", List.of(op)));
        assertEquals(2, semanticStore.getCollectionMetadata(collectionName).get("schema_version"));
        assertEquals("unknown", vectorStore.withCollection(collectionName)
                .queryByFilters(Map.of("nickname", "unknown"), 10)
                .get(0)
                .getMetadata()
                .get("nickname"));
    }

    @Test
    void tryMigrateFailsWhenExistingBackendDoesNotSupportSchemaMutation() {
        String collectionName = "alice_scope_migration_test_user_profile";
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.tableExists(collectionName)).thenReturn(true);

        SemanticStore semanticStore = new SemanticStore(vectorStore);
        semanticStore.createCollection(collectionName, 3, Map.of());
        semanticStore.updateCollectionMetadata(collectionName, Map.of("schema_version", 1));

        VectorMigrator migrator = new VectorMigrator(semanticStore);
        AddScalarFieldOperation op = new AddScalarFieldOperation(
                new OperationMetadata(2, "add field"),
                "user_profile",
                "nickname",
                "string",
                "unknown");

        assertFalse(migrator.tryMigrate("vector_user_profile", List.of(op)));
        assertEquals(1, semanticStore.getCollectionMetadata(collectionName).get("schema_version"));
    }
}
