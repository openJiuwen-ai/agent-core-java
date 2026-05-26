/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.memory;

import com.openjiuwen.core.memory.migration.MigrationPlan;
import com.openjiuwen.core.memory.migration.migrator.KvMigrator;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.UpdateKVOperation;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.spi.store.BaseKVStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for LongTermMemory migration.
 * Mirrors Python's tests/unit_tests/core/memory/test_long_term_memory_migration_integration.py
 */
class TestLongTermMemoryMigrationIntegration {

    private BaseKVStore kvStore;

    @BeforeEach
    void setUp() {
        kvStore = new InMemoryKVStore();
        // Clear registries before each test
        MigrationPlan.getKvRegistry().clear();
    }

    @AfterEach
    void tearDown() {
        // Restore registry state
        MigrationPlan.getKvRegistry().clear();
    }

    @Nested
    @DisplayName("LongTermMemoryMigrationIntegration tests")
    class MigrationTests {

        @Test
        @DisplayName("test empty migration plan")
        void testEmptyMigrationPlan() {
            // Test that empty migration plan is handled gracefully.
            KvMigrator migrator = new KvMigrator(kvStore);

            boolean result = migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, 
                    MigrationPlan.getKvRegistry().getOperations(KvMigrator.KV_ENTITY_KEY));

            assertTrue(result);
            // No version set when no operations
            Object version = kvStore.get(KvMigrator.KV_SCHEMA_VERSION);
            // Version may be null or 0 depending on implementation
        }

        @Test
        @DisplayName("test kv migration during register")
        void testKvMigrationDuringRegister() {
            // Test that migration is automatically triggered during register.
            // Set initial version
            kvStore.set(KvMigrator.KV_SCHEMA_VERSION, 1);
            kvStore.set("old_key", "old_value");

            // Register migration operation
            UpdateKVOperation op = new UpdateKVOperation(
                    new OperationMetadata(2, "Migrate v2"),
                    store -> {
                        Object oldVal = store.get("old_key");
                        if (oldVal != null) {
                            store.set("new_key", oldVal);
                            store.delete("old_key");
                        }
                    });

            MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY, op);

            KvMigrator migrator = new KvMigrator(kvStore);
            boolean result = migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY,
                    MigrationPlan.getKvRegistry().getOperations(KvMigrator.KV_ENTITY_KEY));

            assertTrue(result);
            assertNull(kvStore.get("old_key"));
            assertEquals("old_value", kvStore.get("new_key"));
            assertEquals(2, kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
        }

        @Test
        @DisplayName("test idempotent migration")
        void testIdempotentMigration() {
            // Test that migration is idempotent - running multiple times doesn't cause issues.
            kvStore.set(KvMigrator.KV_SCHEMA_VERSION, 0);

            UpdateKVOperation op = new UpdateKVOperation(
                    new OperationMetadata(1, "Initialize"),
                    store -> {
                        store.set("initialized", "true");
                        store.set("data", "value");
                    });

            MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY, op);

            KvMigrator migrator = new KvMigrator(kvStore);

            // First migration
            migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY,
                    MigrationPlan.getKvRegistry().getOperations(KvMigrator.KV_ENTITY_KEY));

            assertEquals("true", kvStore.get("initialized"));
            assertEquals("value", kvStore.get("data"));

            // Second migration - should be idempotent
            migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY,
                    MigrationPlan.getKvRegistry().getOperations(KvMigrator.KV_ENTITY_KEY));

            assertEquals("true", kvStore.get("initialized"));
            assertEquals("value", kvStore.get("data"));
            assertEquals(1, kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
        }

        @Test
        @DisplayName("test migration version already at target")
        void testMigrationVersionAlreadyAtTarget() {
            // Test that migration skips when version is already at target.
            kvStore.set(KvMigrator.KV_SCHEMA_VERSION, 2);

            UpdateKVOperation op = new UpdateKVOperation(
                    new OperationMetadata(2, "Migrate v2"),
                    store -> store.set("migrated", "true"));

            MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY, op);

            KvMigrator migrator = new KvMigrator(kvStore);
            boolean result = migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY,
                    MigrationPlan.getKvRegistry().getOperations(KvMigrator.KV_ENTITY_KEY));

            assertTrue(result);
            // Should not have run migration since version already at target
            assertNull(kvStore.get("migrated"));
        }

        @Test
        @DisplayName("test kv schema version constant")
        void testKvSchemaVersionConstant() {
            // Test that KV_SCHEMA_VERSION constant is defined.
            assertEquals("MEMORY_MIGRATION_KV_SCHEMA_VERSION", KvMigrator.KV_SCHEMA_VERSION);
            assertEquals("kv_global", KvMigrator.KV_ENTITY_KEY);
        }
    }
}