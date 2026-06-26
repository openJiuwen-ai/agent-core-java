/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration;

import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.OperationRegistry;
import com.openjiuwen.core.memory.migration.operation.UpdateKVOperation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Missing Python parity tests for migration-plan registries.
 *
 * <p>Mirrors Python's {@code TestMigrationPlan} in
 * {@code tests/unit_tests/core/memory/migration/test_migration_plan.py}.</p>
 */
class MigrationPlanMissingTest {

    @AfterEach
    void restoreRegistries() {
        MigrationPlan.getSqlRegistry().clear();
        MigrationPlan.getVectorRegistry().clear();
        MigrationPlan.getKvRegistry().clear();
        MigrationPlan.getMessageRegistry().clear();
        MigrationPlan.getIndexRegistry().clear();
    }

    @Test
    void kvRegistryInitialization() {
        assertThat(MigrationPlan.getKvRegistry()).isNotNull();
        assertThat(MigrationPlan.getKvRegistry().getAllOperations()).isInstanceOf(Map.class);
    }

    @Test
    void sqlRegistryInitialization() {
        assertThat(MigrationPlan.getSqlRegistry()).isNotNull();
        assertThat(MigrationPlan.getSqlRegistry().getAllOperations()).isInstanceOf(Map.class);
    }

    @Test
    void vectorRegistryInitialization() {
        assertThat(MigrationPlan.getVectorRegistry()).isNotNull();
        assertThat(MigrationPlan.getVectorRegistry().getAllOperations()).isInstanceOf(Map.class);
    }

    @Test
    void kvRegistryRegisterOperation() {
        OperationRegistry registry = MigrationPlan.getKvRegistry();
        registry.register("test_entity", operation(1, "Test operation",
                store -> store.set("test_key", "test_value")));

        List<BaseOperation> operations = registry.getOperations("test_entity", 1, 1);

        assertThat(operations).hasSize(1);
        assertThat(operations.get(0).getSchemaVersion()).isEqualTo(1);
    }

    @Test
    void kvRegistryGetOperationsOperationsEmpty() {
        List<BaseOperation> operations = MigrationPlan.getKvRegistry().getOperations("nonexistent_entity", 1, 10);

        assertThat(operations).isEmpty();
    }

    @Test
    void kvRegistryGetCurrentVersionEmpty() {
        int version = MigrationPlan.getKvRegistry().getCurrentVersion("nonexistent_entity");

        assertThat(version).isZero();
    }

    @Test
    void kvRegistryGetAllEntitiesEmpty() {
        List<String> entities = MigrationPlan.getKvRegistry().getAllEntities();

        assertThat(entities).isEmpty();
    }

    @Test
    void kvRegistryGetAllOperationsEmpty() {
        Map<String, List<BaseOperation>> allOperations = MigrationPlan.getKvRegistry().getAllOperations();

        assertThat(allOperations).isEmpty();
    }

    @Test
    void kvRegistryMultipleOperationsSameEntity() {
        OperationRegistry registry = MigrationPlan.getKvRegistry();
        registry.register("test_entity", operation(1, "Test v1", store -> store.set("key_v1", "value_v1")));
        registry.register("test_entity", operation(2, "Test v2", store -> store.set("key_v2", "value_v2")));

        List<BaseOperation> operations = registry.getOperations("test_entity", 1, 2);

        assertThat(operations).hasSize(2);
        assertThat(operations.get(0).getSchemaVersion()).isEqualTo(1);
        assertThat(operations.get(1).getSchemaVersion()).isEqualTo(2);
    }

    @Test
    void kvRegistryGetOperationsVersionRange() {
        OperationRegistry registry = MigrationPlan.getKvRegistry();
        for (int version = 1; version < 6; version++) {
            registry.register("test_entity", operation(version, "Test v" + version,
                    store -> store.set("test_key", "test_value")));
        }

        List<BaseOperation> operations = registry.getOperations("test_entity", 2, 4);

        assertThat(operations).hasSize(3);
        assertThat(operations).extracting(BaseOperation::getSchemaVersion).containsExactly(2, 3, 4);
    }

    @Test
    void kvRegistryGetOperationsInvalidRange() {
        OperationRegistry registry = MigrationPlan.getKvRegistry();
        registry.register("test_entity", operation(1, "Test", store -> store.set("test_key", "test_value")));

        List<BaseOperation> operations = registry.getOperations("test_entity", 5, 1);

        assertThat(operations).isEmpty();
    }

    @Test
    void kvRegistryGetCurrentVersion() {
        OperationRegistry registry = MigrationPlan.getKvRegistry();
        registry.register("test_entity", operation(3, "Test", store -> store.set("test_key", "test_value")));

        int version = registry.getCurrentVersion("test_entity");

        assertThat(version).isEqualTo(3);
    }

    @Test
    void kvRegistryGetAllEntities() {
        OperationRegistry registry = MigrationPlan.getKvRegistry();
        registry.register("entity1", operation(1, "Test", store -> store.set("test_key", "test_value")));
        registry.register("entity2", operation(1, "Test", store -> store.set("test_key", "test_value")));

        List<String> entities = registry.getAllEntities();

        assertThat(entities).hasSize(2);
        assertThat(entities).contains("entity1", "entity2");
    }

    @Test
    void kvRegistryGetAllOperations() {
        OperationRegistry registry = MigrationPlan.getKvRegistry();
        registry.register("entity1", operation(1, "Test", store -> store.set("test_key", "test_value")));
        registry.register("entity2", operation(2, "Test", store -> store.set("test_key", "test_value")));

        Map<String, List<BaseOperation>> allOperations = registry.getAllOperations();

        assertThat(allOperations).hasSize(2);
        assertThat(allOperations).containsKeys("entity1", "entity2");
        assertThat(allOperations.get("entity1")).hasSize(1);
        assertThat(allOperations.get("entity2")).hasSize(1);
    }

    @Test
    void kvRegistryRegisterSameVersionRaisesError() {
        OperationRegistry registry = MigrationPlan.getKvRegistry();
        registry.register("test_entity", operation(1, "Test 1", store -> store.set("key1", "value1")));

        assertThatThrownBy(() -> registry.register("test_entity",
                operation(1, "Test 2", store -> store.set("key2", "value2"))))
                .hasMessageContaining("failed to register operation")
                .hasMessageContaining("schema number of the new operation must be greater than the current maximum");
    }

    @Test
    void kvRegistryRegisterLowerVersionRaisesError() {
        OperationRegistry registry = MigrationPlan.getKvRegistry();
        registry.register("test_entity", operation(3, "Test v3", store -> store.set("test_key", "test_value")));

        assertThatThrownBy(() -> registry.register("test_entity",
                operation(1, "Test v1", store -> store.set("test_key", "test_value"))))
                .hasMessageContaining("failed to register operation")
                .hasMessageContaining("schema number of the new operation must be greater than the current maximum");
    }

    @Test
    void kvRegistryOperationExecution() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        OperationRegistry registry = MigrationPlan.getKvRegistry();
        registry.register("test_entity", operation(1, "Test", store -> store.set("test_key", "test_value")
                .thenCompose(ignored -> store.set("another_key", "another_value"))));

        List<BaseOperation> operations = registry.getOperations("test_entity", 1, 1);
        ((UpdateKVOperation) operations.get(0)).getUpdateFunc().apply(kvStore).join();

        assertThat(kvStore.get("test_key").join()).isEqualTo("test_value");
        assertThat(kvStore.get("another_key").join()).isEqualTo("another_value");
    }

    @Test
    void kvRegistryMultipleOperationsExecutionOrder() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        List<Integer> executionOrder = new ArrayList<>();
        OperationRegistry registry = MigrationPlan.getKvRegistry();
        registry.register("test_entity", operation(1, "Test v1", store -> {
            executionOrder.add(1);
            return store.set("order", "v1");
        }));
        registry.register("test_entity", operation(2, "Test v2", store -> {
            executionOrder.add(2);
            return store.set("order", "v2");
        }));
        registry.register("test_entity", operation(3, "Test v3", store -> {
            executionOrder.add(3);
            return store.set("order", "v3");
        }));

        for (BaseOperation operation : registry.getOperations("test_entity", 1, 3)) {
            ((UpdateKVOperation) operation).getUpdateFunc().apply(kvStore).join();
        }

        assertThat(executionOrder).containsExactly(1, 2, 3);
        assertThat(kvStore.get("order").join()).isEqualTo("v3");
    }

    @Test
    void sqlRegistryOperations() {
        assertThat(MigrationPlan.getSqlRegistry()).isNotNull();
        assertThat(MigrationPlan.getSqlRegistry().getAllEntities()).isInstanceOf(List.class);
    }

    @Test
    void vectorRegistryOperations() {
        assertThat(MigrationPlan.getVectorRegistry()).isNotNull();
        assertThat(MigrationPlan.getVectorRegistry().getAllEntities()).isInstanceOf(List.class);
    }

    @Test
    void registryCleanupAndRestore() {
        OperationRegistry registry = MigrationPlan.getKvRegistry();
        registry.register("test_entity1", operation(1, "Test 1", store -> store.set("test_key", "test_value")));
        registry.register("test_entity2", operation(2, "Test 2", store -> store.set("test_key", "test_value")));
        Map<String, List<BaseOperation>> currentState = new LinkedHashMap<>(registry.getAllOperations());

        registry.clear();
        assertThat(registry.getAllEntities()).isEmpty();

        registry.setOperations(currentState);
        List<String> entities = registry.getAllEntities();

        assertThat(entities).hasSize(2);
        assertThat(entities).contains("test_entity1", "test_entity2");
    }

    private static UpdateKVOperation operation(int version,
                                               String description,
                                               Function<BaseKVStore, CompletableFuture<Void>> updateFunc) {
        return new UpdateKVOperation(new OperationMetadata(version, description), updateFunc);
    }
}
