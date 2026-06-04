package com.openjiuwen.core.memory.migration;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.UpdateKVOperation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_migration_plan.py} in
 * {@code tests/unit_tests/core/memory/migration}.
 */
class MigrationPlanTest {

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
        assertInstanceOf(Map.class, MigrationPlan.getKvRegistry().getAllOperations());
    }

    @Test
    void testSqlRegistryInitialization() {
        assertNotNull(MigrationPlan.getSqlRegistry());
        assertInstanceOf(Map.class, MigrationPlan.getSqlRegistry().getAllOperations());
    }

    @Test
    void testVectorRegistryInitialization() {
        assertNotNull(MigrationPlan.getVectorRegistry());
        assertInstanceOf(Map.class, MigrationPlan.getVectorRegistry().getAllOperations());
    }

    @Test
    void testKvRegistryRegisterOperation() {
        UpdateKVOperation operation = updateOperation(1, "Test operation",
                store -> store.set("test_key", "test_value"));

        MigrationPlan.getKvRegistry().register("test_entity", operation);

        List<BaseOperation> operations = MigrationPlan.getKvRegistry().getOperations("test_entity", 1, 1);
        assertEquals(1, operations.size());
        assertEquals(1, operations.getFirst().getSchemaVersion());
    }

    @Test
    void testKvRegistryGetOperationsOperationsEmpty() {
        List<BaseOperation> operations = MigrationPlan.getKvRegistry()
                .getOperations("nonexistent_entity", 1, 10);

        assertEquals(0, operations.size());
    }

    @Test
    void testKvRegistryGetCurrentVersionEmpty() {
        assertEquals(0, MigrationPlan.getKvRegistry().getCurrentVersion("nonexistent_entity"));
    }

    @Test
    void testKvRegistryGetAllEntitiesEmpty() {
        assertEquals(0, MigrationPlan.getKvRegistry().getAllEntities().size());
    }

    @Test
    void testKvRegistryGetAllOperationsEmpty() {
        assertEquals(0, MigrationPlan.getKvRegistry().getAllOperations().size());
    }

    @Test
    void testKvRegistryMultipleOperationsSameEntity() {
        MigrationPlan.getKvRegistry().register("test_entity",
                updateOperation(1, "Test v1", store -> store.set("key_v1", "value_v1")));
        MigrationPlan.getKvRegistry().register("test_entity",
                updateOperation(2, "Test v2", store -> store.set("key_v2", "value_v2")));

        List<BaseOperation> operations = MigrationPlan.getKvRegistry().getOperations("test_entity", 1, 2);

        assertEquals(2, operations.size());
        assertEquals(1, operations.get(0).getSchemaVersion());
        assertEquals(2, operations.get(1).getSchemaVersion());
    }

    @Test
    void testKvRegistryGetOperationsVersionRange() {
        for (int version = 1; version < 6; version++) {
            int capturedVersion = version;
            MigrationPlan.getKvRegistry().register("test_entity",
                    updateOperation(capturedVersion, "Test v" + capturedVersion,
                            store -> store.set("test_key", "test_value")));
        }

        List<BaseOperation> operations = MigrationPlan.getKvRegistry().getOperations("test_entity", 2, 4);

        assertEquals(3, operations.size());
        assertEquals(2, operations.get(0).getSchemaVersion());
        assertEquals(3, operations.get(1).getSchemaVersion());
        assertEquals(4, operations.get(2).getSchemaVersion());
    }

    @Test
    void testKvRegistryGetOperationsInvalidRange() {
        MigrationPlan.getKvRegistry().register("test_entity",
                updateOperation(1, "Test", store -> store.set("test_key", "test_value")));

        List<BaseOperation> operations = MigrationPlan.getKvRegistry().getOperations("test_entity", 5, 1);

        assertEquals(0, operations.size());
    }

    @Test
    void testKvRegistryGetCurrentVersion() {
        MigrationPlan.getKvRegistry().register("test_entity",
                updateOperation(3, "Test", store -> store.set("test_key", "test_value")));

        assertEquals(3, MigrationPlan.getKvRegistry().getCurrentVersion("test_entity"));
    }

    @Test
    void testKvRegistryGetAllEntities() {
        MigrationPlan.getKvRegistry().register("entity1",
                updateOperation(1, "Test", store -> store.set("test_key", "test_value")));
        MigrationPlan.getKvRegistry().register("entity2",
                updateOperation(1, "Test", store -> store.set("test_key", "test_value")));

        List<String> entities = MigrationPlan.getKvRegistry().getAllEntities();

        assertEquals(2, entities.size());
        assertTrue(entities.contains("entity1"));
        assertTrue(entities.contains("entity2"));
    }

    @Test
    void testKvRegistryGetAllOperations() {
        MigrationPlan.getKvRegistry().register("entity1",
                updateOperation(1, "Test", store -> store.set("test_key", "test_value")));
        MigrationPlan.getKvRegistry().register("entity2",
                updateOperation(2, "Test", store -> store.set("test_key", "test_value")));

        Map<String, List<BaseOperation>> allOps = MigrationPlan.getKvRegistry().getAllOperations();

        assertEquals(2, allOps.size());
        assertTrue(allOps.containsKey("entity1"));
        assertTrue(allOps.containsKey("entity2"));
        assertEquals(1, allOps.get("entity1").size());
        assertEquals(1, allOps.get("entity2").size());
    }

    @Test
    void testKvRegistryRegisterSameVersionRaisesError() {
        MigrationPlan.getKvRegistry().register("test_entity",
                updateOperation(1, "Test 1", store -> store.set("key1", "value1")));

        BaseError error = assertThrows(BaseError.class, () -> MigrationPlan.getKvRegistry().register("test_entity",
                updateOperation(1, "Test 2", store -> store.set("key2", "value2"))));

        assertTrue(error.getMessage().contains("failed to register operation"));
        assertTrue(error.getMessage().contains(
                "schema number of the new operation must be greater than the current maximum"));
    }

    @Test
    void testKvRegistryRegisterLowerVersionRaisesError() {
        MigrationPlan.getKvRegistry().register("test_entity",
                updateOperation(3, "Test v3", store -> store.set("test_key", "test_value")));

        BaseError error = assertThrows(BaseError.class, () -> MigrationPlan.getKvRegistry().register("test_entity",
                updateOperation(1, "Test v1", store -> store.set("test_key", "test_value"))));

        assertTrue(error.getMessage().contains("failed to register operation"));
        assertTrue(error.getMessage().contains(
                "schema number of the new operation must be greater than the current maximum"));
    }

    @Test
    void testKvRegistryOperationExecution() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        MigrationPlan.getKvRegistry().register("test_entity", updateOperation(1, "Test", store -> {
            store.set("test_key", "test_value");
            store.set("another_key", "another_value");
        }));

        List<BaseOperation> operations = MigrationPlan.getKvRegistry().getOperations("test_entity", 1, 1);

        assertEquals(1, operations.size());
        ((UpdateKVOperation) operations.getFirst()).getUpdateFunc().accept(kvStore);
        assertEquals("test_value", kvStore.get("test_key"));
        assertEquals("another_value", kvStore.get("another_key"));
    }

    @Test
    void testKvRegistryMultipleOperationsExecutionOrder() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        List<Integer> executionOrder = new ArrayList<>();

        MigrationPlan.getKvRegistry().register("test_entity", updateOperation(1, "Test v1", store -> {
            executionOrder.add(1);
            store.set("order", "v1");
        }));
        MigrationPlan.getKvRegistry().register("test_entity", updateOperation(2, "Test v2", store -> {
            executionOrder.add(2);
            store.set("order", "v2");
        }));
        MigrationPlan.getKvRegistry().register("test_entity", updateOperation(3, "Test v3", store -> {
            executionOrder.add(3);
            store.set("order", "v3");
        }));

        List<BaseOperation> operations = MigrationPlan.getKvRegistry().getOperations("test_entity", 1, 3);

        assertEquals(3, operations.size());
        for (BaseOperation operation : operations) {
            ((UpdateKVOperation) operation).getUpdateFunc().accept(kvStore);
        }
        assertIterableEquals(List.of(1, 2, 3), executionOrder);
        assertEquals("v3", kvStore.get("order"));
    }

    @Test
    void testSqlRegistryOperations() {
        assertNotNull(MigrationPlan.getSqlRegistry());
        assertInstanceOf(List.class, MigrationPlan.getSqlRegistry().getAllEntities());
    }

    @Test
    void testVectorRegistryOperations() {
        assertNotNull(MigrationPlan.getVectorRegistry());
        assertInstanceOf(List.class, MigrationPlan.getVectorRegistry().getAllEntities());
    }

    @Test
    void testRegistryCleanupAndRestore() {
        MigrationPlan.getKvRegistry().register("test_entity1",
                updateOperation(1, "Test 1", store -> store.set("test_key", "test_value")));
        MigrationPlan.getKvRegistry().register("test_entity2",
                updateOperation(2, "Test 2", store -> store.set("test_key", "test_value")));

        Map<String, List<BaseOperation>> currentState = copyOperations(MigrationPlan.getKvRegistry().getAllOperations());

        MigrationPlan.getKvRegistry().clear();
        assertEquals(0, MigrationPlan.getKvRegistry().getAllEntities().size());

        MigrationPlan.getKvRegistry().setOperations(currentState);
        List<String> entities = MigrationPlan.getKvRegistry().getAllEntities();
        assertEquals(2, entities.size());
        assertTrue(entities.contains("test_entity1"));
        assertTrue(entities.contains("test_entity2"));
    }

    private static UpdateKVOperation updateOperation(int schemaVersion, String description,
                                                     java.util.function.Consumer<com.openjiuwen.spi.store.BaseKVStore>
                                                             updateFunc) {
        return new UpdateKVOperation(new OperationMetadata(schemaVersion, description), updateFunc);
    }

    private static Map<String, List<BaseOperation>> copyOperations(Map<String, List<BaseOperation>> source) {
        Map<String, List<BaseOperation>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, new ArrayList<>(value)));
        return copy;
    }
}
