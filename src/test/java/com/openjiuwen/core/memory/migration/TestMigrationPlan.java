/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.memory.migration;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.UpdateKVOperation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for migration_plan.py registry objects.
 * Mirrors Python's tests/unit_tests/core/memory/migration/test_migration_plan.py.
 */
@DisplayName("MigrationPlan registry tests")
class TestMigrationPlan {

    private Map<String, List<BaseOperation>> sqlBackup;
    private Map<String, List<BaseOperation>> vectorBackup;
    private Map<String, List<BaseOperation>> kvBackup;

    @BeforeEach
    void backupAndClearRegistries() {
        sqlBackup = copyOperations(MigrationPlan.getSqlRegistry().getAllOperations());
        vectorBackup = copyOperations(MigrationPlan.getVectorRegistry().getAllOperations());
        kvBackup = copyOperations(MigrationPlan.getKvRegistry().getAllOperations());

        MigrationPlan.getSqlRegistry().clear();
        MigrationPlan.getVectorRegistry().clear();
        MigrationPlan.getKvRegistry().clear();
    }

    @AfterEach
    void restoreRegistries() {
        MigrationPlan.getSqlRegistry().clear();
        MigrationPlan.getSqlRegistry().setOperations(sqlBackup);
        MigrationPlan.getVectorRegistry().clear();
        MigrationPlan.getVectorRegistry().setOperations(vectorBackup);
        MigrationPlan.getKvRegistry().clear();
        MigrationPlan.getKvRegistry().setOperations(kvBackup);
    }

    @Test
    void testKvRegistryInitialization() {
        assertNotNull(MigrationPlan.getKvRegistry());
        assertNotNull(MigrationPlan.getKvRegistry().getAllOperations());
    }

    @Test
    void testSqlRegistryInitialization() {
        assertNotNull(MigrationPlan.getSqlRegistry());
        assertNotNull(MigrationPlan.getSqlRegistry().getAllOperations());
    }

    @Test
    void testVectorRegistryInitialization() {
        assertNotNull(MigrationPlan.getVectorRegistry());
        assertNotNull(MigrationPlan.getVectorRegistry().getAllOperations());
    }

    @Test
    void testKvRegistryRegisterOperation() {
        UpdateKVOperation operation = operation(1, "Test operation",
                store -> store.set("test_key", "test_value"));

        MigrationPlan.getKvRegistry().register("test_entity", operation);

        List<BaseOperation> operations = MigrationPlan.getKvRegistry().getOperations("test_entity", 1, 1);
        assertEquals(1, operations.size());
        assertEquals(1, operations.get(0).getSchemaVersion());
    }

    @Test
    void testKvRegistryGetOperationsOperationsEmpty() {
        assertTrue(MigrationPlan.getKvRegistry().getOperations("missing", 1, 10).isEmpty());
    }

    @Test
    void testKvRegistryGetCurrentVersionEmpty() {
        assertEquals(0, MigrationPlan.getKvRegistry().getCurrentVersion("missing"));
    }

    @Test
    void testKvRegistryGetAllEntitiesEmpty() {
        assertTrue(MigrationPlan.getKvRegistry().getAllEntities().isEmpty());
    }

    @Test
    void testKvRegistryGetAllOperationsEmpty() {
        assertTrue(MigrationPlan.getKvRegistry().getAllOperations().isEmpty());
    }

    @Test
    void testKvRegistryMultipleOperationsSameEntity() {
        MigrationPlan.getKvRegistry().register("test_entity", operation(1, "Test v1",
                store -> store.set("key_v1", "value_v1")));
        MigrationPlan.getKvRegistry().register("test_entity", operation(2, "Test v2",
                store -> store.set("key_v2", "value_v2")));

        List<BaseOperation> operations = MigrationPlan.getKvRegistry().getOperations("test_entity", 1, 2);
        assertEquals(2, operations.size());
        assertEquals(1, operations.get(0).getSchemaVersion());
        assertEquals(2, operations.get(1).getSchemaVersion());
    }

    @Test
    void testKvRegistryGetOperationsVersionRange() {
        for (int version = 1; version <= 5; version++) {
            int captured = version;
            MigrationPlan.getKvRegistry().register("test_entity", operation(version, "Test v" + version,
                    store -> store.set("test_key", "value_" + captured)));
        }

        List<BaseOperation> operations = MigrationPlan.getKvRegistry().getOperations("test_entity", 2, 4);
        assertEquals(3, operations.size());
        assertEquals(2, operations.get(0).getSchemaVersion());
        assertEquals(3, operations.get(1).getSchemaVersion());
        assertEquals(4, operations.get(2).getSchemaVersion());
    }

    @Test
    void testKvRegistryGetOperationsInvalidRange() {
        MigrationPlan.getKvRegistry().register("test_entity", operation(1, "Test",
                store -> store.set("test_key", "test_value")));

        assertTrue(MigrationPlan.getKvRegistry().getOperations("test_entity", 5, 1).isEmpty());
    }

    @Test
    void testKvRegistryGetCurrentVersion() {
        MigrationPlan.getKvRegistry().register("test_entity", operation(3, "Test",
                store -> store.set("test_key", "test_value")));

        assertEquals(3, MigrationPlan.getKvRegistry().getCurrentVersion("test_entity"));
    }

    @Test
    void testKvRegistryGetAllEntities() {
        MigrationPlan.getKvRegistry().register("entity1", operation(1, "Test",
                store -> store.set("test_key", "test_value")));
        MigrationPlan.getKvRegistry().register("entity2", operation(1, "Test",
                store -> store.set("test_key", "test_value")));

        List<String> entities = MigrationPlan.getKvRegistry().getAllEntities();
        assertEquals(2, entities.size());
        assertTrue(entities.contains("entity1"));
        assertTrue(entities.contains("entity2"));
    }

    @Test
    void testKvRegistryGetAllOperations() {
        MigrationPlan.getKvRegistry().register("entity1", operation(1, "Test",
                store -> store.set("test_key", "test_value")));
        MigrationPlan.getKvRegistry().register("entity2", operation(2, "Test",
                store -> store.set("test_key", "test_value")));

        Map<String, List<BaseOperation>> allOps = MigrationPlan.getKvRegistry().getAllOperations();
        assertEquals(2, allOps.size());
        assertTrue(allOps.containsKey("entity1"));
        assertTrue(allOps.containsKey("entity2"));
        assertEquals(1, allOps.get("entity1").size());
        assertEquals(1, allOps.get("entity2").size());
    }

    @Test
    void testKvRegistryRegisterSameVersionRaisesError() {
        MigrationPlan.getKvRegistry().register("test_entity", operation(1, "Test 1",
                store -> store.set("key1", "value1")));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> MigrationPlan.getKvRegistry().register("test_entity", operation(1, "Test 2",
                        store -> store.set("key2", "value2"))));

        assertTrue(error.getMessage().contains("schema number"));
    }

    @Test
    void testKvRegistryRegisterLowerVersionRaisesError() {
        MigrationPlan.getKvRegistry().register("test_entity", operation(3, "Test v3",
                store -> store.set("test_key", "test_value")));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> MigrationPlan.getKvRegistry().register("test_entity", operation(1, "Test v1",
                        store -> store.set("test_key", "test_value"))));

        assertTrue(error.getMessage().contains("schema number"));
    }

    @Test
    void testKvRegistryOperationExecution() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        MigrationPlan.getKvRegistry().register("test_entity", operation(1, "Test", store -> {
            store.set("test_key", "test_value");
            store.set("another_key", "another_value");
        }));

        List<BaseOperation> operations = MigrationPlan.getKvRegistry().getOperations("test_entity", 1, 1);
        assertEquals(1, operations.size());

        ((UpdateKVOperation) operations.get(0)).getUpdateFunc().accept(kvStore);

        assertEquals("test_value", kvStore.get("test_key"));
        assertEquals("another_value", kvStore.get("another_key"));
    }

    @Test
    void testKvRegistryMultipleOperationsExecutionOrder() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        List<Integer> executionOrder = new ArrayList<>();

        MigrationPlan.getKvRegistry().register("test_entity", operation(1, "Test v1", store -> {
            executionOrder.add(1);
            store.set("order", "v1");
        }));
        MigrationPlan.getKvRegistry().register("test_entity", operation(2, "Test v2", store -> {
            executionOrder.add(2);
            store.set("order", "v2");
        }));
        MigrationPlan.getKvRegistry().register("test_entity", operation(3, "Test v3", store -> {
            executionOrder.add(3);
            store.set("order", "v3");
        }));

        List<BaseOperation> operations = MigrationPlan.getKvRegistry().getOperations("test_entity", 1, 3);
        assertEquals(3, operations.size());
        for (BaseOperation op : operations) {
            ((UpdateKVOperation) op).getUpdateFunc().accept(kvStore);
        }

        assertEquals(List.of(1, 2, 3), executionOrder);
        assertEquals("v3", kvStore.get("order"));
    }

    @Test
    void testSqlRegistryOperations() {
        assertNotNull(MigrationPlan.getSqlRegistry());
        assertNotNull(MigrationPlan.getSqlRegistry().getAllEntities());
    }

    @Test
    void testVectorRegistryOperations() {
        assertNotNull(MigrationPlan.getVectorRegistry());
        assertNotNull(MigrationPlan.getVectorRegistry().getAllEntities());
    }

    @Test
    void testRegistryCleanupAndRestore() {
        MigrationPlan.getKvRegistry().register("test_entity1", operation(1, "Test 1",
                store -> store.set("test_key", "test_value")));
        MigrationPlan.getKvRegistry().register("test_entity2", operation(2, "Test 2",
                store -> store.set("test_key", "test_value")));

        Map<String, List<BaseOperation>> currentState =
                copyOperations(MigrationPlan.getKvRegistry().getAllOperations());

        MigrationPlan.getKvRegistry().clear();
        assertTrue(MigrationPlan.getKvRegistry().getAllEntities().isEmpty());

        MigrationPlan.getKvRegistry().setOperations(currentState);
        List<String> entities = MigrationPlan.getKvRegistry().getAllEntities();
        assertEquals(2, entities.size());
        assertTrue(entities.contains("test_entity1"));
        assertTrue(entities.contains("test_entity2"));
    }

    private static UpdateKVOperation operation(String description) {
        return operation(1, description, store -> {
        });
    }

    private static UpdateKVOperation operation(int version, String description,
                                               java.util.function.Consumer<com.openjiuwen.spi.store.BaseKVStore> update) {
        return new UpdateKVOperation(new OperationMetadata(version, description), update);
    }

    private static Map<String, List<BaseOperation>> copyOperations(Map<String, List<BaseOperation>> source) {
        Map<String, List<BaseOperation>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, new ArrayList<>(value)));
        return copy;
    }
}
