/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration;

import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.memory.migration.migrator.VectorMigrator;
import com.openjiuwen.core.memory.migration.operation.AddScalarFieldOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VectorMigratorTest {

    @Test
    void tryMigrateAppliesOperationsAndUpdatesMetadata() {
        InMemoryVectorStore vectorStore = new InMemoryVectorStore("vector_user_profile");
        String collectionName = "alice_scope_migration_test_user_profile";
        vectorStore.createCollection(collectionName, null, Map.of()).join();
        vectorStore.updateCollectionMetadata(collectionName, Map.of("schema_version", 1)).join();
        vectorStore.withCollection(collectionName).add(List.of(Map.of(
                "id", "1",
                "text", "hello",
                "vector", List.of(1.0f, 2.0f, 3.0f)
        )), null, Map.of()).join();

        VectorMigrator migrator = new VectorMigrator(vectorStore);
        AddScalarFieldOperation op = new AddScalarFieldOperation(
                new OperationMetadata(2, "add field"),
                "user_profile",
                "nickname",
                "string",
                "unknown");

        assertTrue(migrator.tryMigrate("vector_user_profile", List.of(op)).join());
        assertEquals(2, vectorStore.getCollectionMetadata(collectionName).join().get("schema_version"));
        assertEquals("unknown", vectorStore.withCollection(collectionName)
                .queryByFilters(Map.of("nickname", "unknown"), 10)
                .get(0)
                .getMetadata()
                .get("nickname"));
    }

    @Test
    void tryMigrateFailsWhenExistingBackendDoesNotSupportSchemaMutation() {
        String collectionName = "alice_scope_migration_test_user_profile";
        BaseVectorStore vectorStore = mock(BaseVectorStore.class);
        when(vectorStore.listCollectionNames()).thenReturn(CompletableFuture.completedFuture(List.of(collectionName)));
        when(vectorStore.getCollectionMetadata(anyString())).thenReturn(
                CompletableFuture.completedFuture(Map.of("schema_version", 1)));
        when(vectorStore.updateSchema(anyString(), any())).thenReturn(
                CompletableFuture.failedFuture(new UnsupportedOperationException("mock")));
        when(vectorStore.updateCollectionMetadata(anyString(), any())).thenReturn(
                CompletableFuture.completedFuture(null));

        VectorMigrator migrator = new VectorMigrator(vectorStore);
        AddScalarFieldOperation op = new AddScalarFieldOperation(
                new OperationMetadata(2, "add field"),
                "user_profile",
                "nickname",
                "string",
                "unknown");

        assertFalse(migrator.tryMigrate("vector_user_profile", List.of(op)).join());
        assertEquals(1, vectorStore.getCollectionMetadata(collectionName).join().get("schema_version"));
    }
}
