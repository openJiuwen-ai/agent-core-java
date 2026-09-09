/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.memory.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.foundation.store.vector.InMemoryVectorStore;
import com.openjiuwen.memory.manage.model.SemanticStore;
import com.openjiuwen.memory.migration.migrator.VectorMigrator;
import com.openjiuwen.memory.migration.operation.AddScalarFieldOperation;
import com.openjiuwen.memory.migration.operation.OperationMetadata;
import com.openjiuwen.spi.store.vector.BaseVectorStore;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class VectorMigratorTest {
    @Test
    void tryMigrateAppliesOperationsAndUpdatesMetadata() {
        InMemoryVectorStore vectorStore = new InMemoryVectorStore(Map.of("collection_name", "vector_user_profile"));
        SemanticStore semanticStore = new SemanticStore(vectorStore);
        String collectionName = "alice_scope_migration_test_user_profile";
        semanticStore.createCollection(collectionName, 3, Map.of());
        semanticStore.updateCollectionMetadata(collectionName, Map.of("schema_version", 1));
        vectorStore.addDocs(collectionName,
                List.of(Map.of("id", "1", "text", "hello", "embedding", List.of(1.0f, 2.0f, 3.0f))), Map.of());

        VectorMigrator migrator = new VectorMigrator(semanticStore);
        AddScalarFieldOperation op = new AddScalarFieldOperation(new OperationMetadata(2, "add field"), "user_profile",
                "nickname", "string", "unknown");

        assertTrue(migrator.tryMigrate("vector_user_profile", List.of(op)));
        assertEquals(2, semanticStore.getCollectionMetadata(collectionName).get("schema_version"));
        assertTrue(vectorStore.getSchema(collectionName, Map.of()).hasField("nickname"));
    }

    @Test
    void tryMigrateFailsWhenExistingBackendDoesNotSupportSchemaMutation() throws Exception {
        String collectionName = "alice_scope_migration_test_user_profile";
        BaseVectorStore vectorStore = mock(BaseVectorStore.class);
        when(vectorStore.collectionExists(collectionName, Map.of())).thenReturn(true);
        when(vectorStore.getCollectionMetadata(collectionName)).thenReturn(Map.of("schema_version", 1));
        doThrow(new UnsupportedOperationException("schema mutation unavailable")).when(vectorStore)
                .updateSchema(eq(collectionName), anyList());

        SemanticStore semanticStore = new SemanticStore(vectorStore);
        semanticStore.createCollection(collectionName, 3, Map.of());
        semanticStore.updateCollectionMetadata(collectionName, Map.of("schema_version", 1));

        VectorMigrator migrator = new VectorMigrator(semanticStore);
        AddScalarFieldOperation op = new AddScalarFieldOperation(new OperationMetadata(2, "add field"), "user_profile",
                "nickname", "string", "unknown");

        assertFalse(migrator.tryMigrate("vector_user_profile", List.of(op)));
        assertEquals(1, semanticStore.getCollectionMetadata(collectionName).get("schema_version"));
    }
}
