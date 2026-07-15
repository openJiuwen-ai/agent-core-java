/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.memory.common.KvPrefixRegistry;
import com.openjiuwen.core.memory.migration.migrator.KvMigrator;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.UpdateKVOperation;
import com.openjiuwen.core.memory.support.TestInMemoryKVStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class KvMigratorTest {
    private static final String PREFIX = "user_message";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void registerPrefix() {
        KvPrefixRegistry.getInstance().registerCurrent(PREFIX);
    }

    @AfterEach
    void resetPrefixes() {
        KvPrefixRegistry.getInstance().unregister(PREFIX);
    }

    @Test
    void tryMigrateAppliesPendingOperationsAndUpdatesVersion() {
        TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
        kvStore.set(PREFIX + ":1", "old");
        kvStore.set(PREFIX + ":2", "old2");

        KvMigrator migrator = new KvMigrator(kvStore);
        UpdateKVOperation op = new UpdateKVOperation(new OperationMetadata(2, "append suffix"), store -> {
            store.set(PREFIX + ":1", "migrated");
            store.set(PREFIX + ":2", "migrated");
        });

        assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(op)));
        assertEquals("migrated", kvStore.get(PREFIX + ":1"));
        assertEquals("migrated", kvStore.get(PREFIX + ":2"));
        assertEquals("2", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void tryMigrateRejectsUnsupportedEntityKey() {
        TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
        KvMigrator migrator = new KvMigrator(kvStore);

        assertFalse(migrator.tryMigrate("bad", List.of()));
    }

    @Test
    void tryMigrateExecutesOnlyVersionsNewerThanCurrentVersion() {
        TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "1");
        kvStore.set("key2", "value1");
        kvStore.set("key4", "value4");

        KvMigrator migrator = new KvMigrator(kvStore);
        List<UpdateKVOperation> operations =
            List.of(new UpdateKVOperation(new OperationMetadata(1, "rename key1 to key2"), store -> {
                Object value = store.get("key1");
                if (value != null) {
                    store.set("key2", value);
                    store.delete("key1");
                }
            }), new UpdateKVOperation(new OperationMetadata(2, "rename key2 to key3"), store -> {
                Object value = store.get("key2");
                if (value != null) {
                    store.set("key3", value);
                    store.delete("key2");
                }
            }), new UpdateKVOperation(new OperationMetadata(3, "merge key3 and key4"), store -> {
                Object value3 = store.get("key3");
                Object value4 = store.get("key4");
                if (value3 != null || value4 != null) {
                    store.set("key5", "{\"key3\":\"" + value3 + "\",\"key4\":\"" + value4 + "\"}");
                    store.delete("key3");
                    store.delete("key4");
                }
            }));

        assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.copyOf(operations)));
        assertEquals(null, kvStore.get("key1"));
        assertEquals(null, kvStore.get("key2"));
        assertEquals(null, kvStore.get("key3"));
        assertEquals(null, kvStore.get("key4"));
        assertEquals("{\"key3\":\"value1\",\"key4\":\"value4\"}", kvStore.get("key5"));
        assertEquals("3", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void tryMigrateRejectsInvalidVersionFormat() {
        TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "invalid_version");

        KvMigrator migrator = new KvMigrator(kvStore);
        UpdateKVOperation op =
            new UpdateKVOperation(new OperationMetadata(1, "migrate"), store -> store.set("migrated", "true"));

        IllegalStateException error =
            assertThrows(IllegalStateException.class, () -> migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(op)));
        assertTrue(error.getMessage().contains("Invalid KV_SCHEMA_VERSION format"));
        assertTrue(error.getMessage().contains("invalid_version"));
    }

    @Test
    void tryMigrateAcceptsIntegerAndNumericStringVersions() {
        TestInMemoryKVStore stringStore = new TestInMemoryKVStore();
        stringStore.set(KvMigrator.KV_SCHEMA_VERSION, "1");
        stringStore.set("old_key", "old_value");
        KvMigrator stringMigrator = new KvMigrator(stringStore);

        UpdateKVOperation stringOp =
            new UpdateKVOperation(new OperationMetadata(2, "migrate string version"), store -> {
                Object value = store.get("old_key");
                if (value != null) {
                    store.set("new_key", value);
                    store.delete("old_key");
                }
            });

        assertTrue(stringMigrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(stringOp)));
        assertEquals(null, stringStore.get("old_key"));
        assertEquals("old_value", stringStore.get("new_key"));
        assertEquals("2", stringStore.get(KvMigrator.KV_SCHEMA_VERSION));

        TestInMemoryKVStore integerStore = new TestInMemoryKVStore();
        integerStore.set(KvMigrator.KV_SCHEMA_VERSION, 1);
        integerStore.set("old_key", "old_value");
        KvMigrator integerMigrator = new KvMigrator(integerStore);

        assertTrue(integerMigrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(stringOp)));
        assertEquals(null, integerStore.get("old_key"));
        assertEquals("old_value", integerStore.get("new_key"));
        assertEquals("2", integerStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void tryMigrateRunsAllOperationsWhenVersionFieldDoesNotExist() {
        TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
        KvMigrator migrator = new KvMigrator(kvStore);
        UpdateKVOperation op =
            new UpdateKVOperation(new OperationMetadata(1, "initialize"), store -> store.set("initialized", "true"));

        assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(op)));
        assertEquals("true", kvStore.get("initialized"));
        assertEquals("1", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void tryMigrateTreatsOnlyUnregisteredPrefixDataAsNewStore() {
        Map<String, List<BaseOperation>> originalOps = MigrationPlan.getKvRegistry().getAllOperations();
        try {
            MigrationPlan.getKvRegistry().clear();
            MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY,
                    new UpdateKVOperation(new OperationMetadata(1, "registered latest version"),
                            store -> store.set("registered_op_ran", "true")));

            TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
            kvStore.set("UNREGISTERED_PREFIX/key", "external_data");
            KvMigrator migrator = new KvMigrator(kvStore);
            UpdateKVOperation op = new UpdateKVOperation(new OperationMetadata(1, "would migrate old memory data"),
                    store -> store.set("migrated", "true"));

            assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(op)));
            assertEquals("external_data", kvStore.get("UNREGISTERED_PREFIX/key"));
            assertEquals(null, kvStore.get("migrated"));
            assertEquals("1", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
        } finally {
            MigrationPlan.getKvRegistry().setOperations(originalOps);
        }
    }

    @Test
    void tryMigrateHandlesZeroNegativeAndLargeVersions() {
        TestInMemoryKVStore zeroStore = new TestInMemoryKVStore();
        zeroStore.set(KvMigrator.KV_SCHEMA_VERSION, 0);
        KvMigrator zeroMigrator = new KvMigrator(zeroStore);
        UpdateKVOperation zeroOp = new UpdateKVOperation(new OperationMetadata(1, "migrate from zero"),
                store -> store.set("migrated", "zero"));

        assertTrue(zeroMigrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(zeroOp)));
        assertEquals("zero", zeroStore.get("migrated"));
        assertEquals("1", zeroStore.get(KvMigrator.KV_SCHEMA_VERSION));

        TestInMemoryKVStore negativeStore = new TestInMemoryKVStore();
        negativeStore.set(KvMigrator.KV_SCHEMA_VERSION, -1);
        KvMigrator negativeMigrator = new KvMigrator(negativeStore);
        UpdateKVOperation negativeOp = new UpdateKVOperation(new OperationMetadata(1, "migrate from negative"),
                store -> store.set("migrated", "negative"));

        assertTrue(negativeMigrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(negativeOp)));
        assertEquals("negative", negativeStore.get("migrated"));
        assertEquals("1", negativeStore.get(KvMigrator.KV_SCHEMA_VERSION));

        TestInMemoryKVStore largeStore = new TestInMemoryKVStore();
        largeStore.set(KvMigrator.KV_SCHEMA_VERSION, 999999);
        KvMigrator largeMigrator = new KvMigrator(largeStore);
        UpdateKVOperation largeOp = new UpdateKVOperation(new OperationMetadata(1000000, "migrate from large version"),
                store -> store.set("migrated", "large"));

        assertTrue(largeMigrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(largeOp)));
        assertEquals("large", largeStore.get("migrated"));
        assertEquals("1000000", largeStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void tryMigrateRejectsFloatVersionType() {
        TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, 1.5d);
        KvMigrator migrator = new KvMigrator(kvStore);
        UpdateKVOperation op =
            new UpdateKVOperation(new OperationMetadata(2, "migrate"), store -> store.set("migrated", "true"));

        IllegalStateException error =
            assertThrows(IllegalStateException.class, () -> migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(op)));
        assertTrue(error.getMessage().contains("Invalid KV_SCHEMA_VERSION type"));
        assertTrue(error.getMessage().contains(Double.class.getName()));
    }

    @Test
    void tryMigrateRejectsOperationsThatAreNotAscending() {
        TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
        kvStore.set(PREFIX + ":1", "old");
        KvMigrator migrator = new KvMigrator(kvStore);
        List<UpdateKVOperation> operations =
            List.of(new UpdateKVOperation(new OperationMetadata(2, "v2"), store -> store.set("v2", "true")),
                    new UpdateKVOperation(new OperationMetadata(1, "v1"), store -> store.set("v1", "true")));

        assertFalse(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.copyOf(operations)));
        assertEquals(null, kvStore.get("v1"));
        assertEquals(null, kvStore.get("v2"));
        assertEquals(null, kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void tryMigrateRollsBackPrefixDataAndVersionOnFailure() {
        String testPrefix = "TEST_PREFIX_ROLLBACK";
        KvPrefixRegistry.getInstance().registerCurrent(testPrefix);
        try {
            TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
            kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "1");
            kvStore.set(testPrefix + "/initial_key1", "initial_value1");
            kvStore.set(testPrefix + "/initial_key2", "initial_value2");

            KvMigrator migrator = new KvMigrator(kvStore);
            UpdateKVOperation v2 = new UpdateKVOperation(new OperationMetadata(2, "migrate v2"), store -> {
                store.set(testPrefix + "/v2_key", "v2_value");
                store.delete(testPrefix + "/initial_key1");
            });
            UpdateKVOperation v3 = new UpdateKVOperation(new OperationMetadata(3, "migrate v3"), store -> {
                store.set(testPrefix + "/v3_key", "v3_value");
                store.delete(testPrefix + "/initial_key2");
                throw new IllegalStateException("Migration v3 failed");
            });

            assertFalse(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(v2, v3)));
            assertEquals("initial_value1", kvStore.get(testPrefix + "/initial_key1"));
            assertEquals("initial_value2", kvStore.get(testPrefix + "/initial_key2"));
            assertEquals("1", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
            assertEquals(null, kvStore.get(testPrefix + "/v2_key"));
            assertEquals(null, kvStore.get(testPrefix + "/v3_key"));
        } finally {
            KvPrefixRegistry.getInstance().unregister(testPrefix);
        }
    }

    @Test
    void tryMigrateRollbackKeepsUnregisteredPrefixData() {
        String testPrefix = "TEST_PREFIX";
        KvPrefixRegistry.getInstance().registerCurrent(testPrefix);
        try {
            TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
            kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "1");
            kvStore.set(testPrefix + "/key1", "value1");
            kvStore.set(testPrefix + "/key2", "value2");
            kvStore.set("other_key", "other_value");

            KvMigrator migrator = new KvMigrator(kvStore);
            UpdateKVOperation op = new UpdateKVOperation(new OperationMetadata(2, "migrate v2"), store -> {
                store.set(testPrefix + "/new_key", "new_value");
                store.delete(testPrefix + "/key1");
                throw new IllegalStateException("Migration v2 failed");
            });

            assertFalse(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(op)));
            assertEquals("value1", kvStore.get(testPrefix + "/key1"));
            assertEquals("value2", kvStore.get(testPrefix + "/key2"));
            assertEquals("other_value", kvStore.get("other_key"));
            assertEquals("1", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
            assertEquals(null, kvStore.get(testPrefix + "/new_key"));
        } finally {
            KvPrefixRegistry.getInstance().unregister(testPrefix);
        }
    }

    @Test
    void tryMigrateRenamesKeysWithSpecificPrefixOnly() {
        String keyPrefix1 = "KEY_PREFIX1";
        String keyPrefix2 = "KEY_PREFIX2";
        KvPrefixRegistry.getInstance().registerCurrent(keyPrefix1);
        KvPrefixRegistry.getInstance().registerCurrent(keyPrefix2);
        try {
            TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
            kvStore.set(keyPrefix1 + "/user1/scope1", "value1");
            kvStore.set(keyPrefix1 + "/user2/scope2", "value2");
            kvStore.set(keyPrefix2 + "/user1/scope1", "protected_value1");
            kvStore.set("other_key1", "other_value1");

            KvMigrator migrator = new KvMigrator(kvStore);
            UpdateKVOperation op = new UpdateKVOperation(
                    new OperationMetadata(1, "rename key prefix1 user/scope to scope/user"), store -> {
                        for (Map.Entry<String, Object> entry : store.getByPrefix(keyPrefix1).entrySet()) {
                            String[] parts = entry.getKey().split("/");
                            if (parts.length == 3) {
                                store.set(parts[0] + "/" + parts[2] + "/" + parts[1], entry.getValue());
                                store.delete(entry.getKey());
                            }
                        }
                    });

            assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(op)));
            assertEquals(null, kvStore.get(keyPrefix1 + "/user1/scope1"));
            assertEquals(null, kvStore.get(keyPrefix1 + "/user2/scope2"));
            assertEquals("value1", kvStore.get(keyPrefix1 + "/scope1/user1"));
            assertEquals("value2", kvStore.get(keyPrefix1 + "/scope2/user2"));
            assertEquals("protected_value1", kvStore.get(keyPrefix2 + "/user1/scope1"));
            assertEquals("other_value1", kvStore.get("other_key1"));
            assertEquals("1", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
        } finally {
            KvPrefixRegistry.getInstance().unregister(keyPrefix1);
            KvPrefixRegistry.getInstance().unregister(keyPrefix2);
        }
    }

    @Test
    void tryMigrateMergesValuesFromMultiplePrefixes() {
        String keyPrefix1 = "KEY_PREFIX1";
        String keyPrefix2 = "KEY_PREFIX2";
        String keyPrefix3 = "KEY_PREFIX3";
        KvPrefixRegistry.getInstance().registerCurrent(keyPrefix1);
        KvPrefixRegistry.getInstance().registerCurrent(keyPrefix2);
        KvPrefixRegistry.getInstance().registerCurrent(keyPrefix3);
        try {
            TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
            kvStore.set(keyPrefix1 + "/user1/scope1", "{\"key1\":\"value1\",\"key2\":\"value2\"}");
            kvStore.set(keyPrefix2 + "/user1/scope1", "{\"key3\":\"value3\",\"key4\":\"value4\"}");
            kvStore.set(keyPrefix2 + "/user1/scope1/extra", "{\"key5\":\"value5\"}");

            KvMigrator migrator = new KvMigrator(kvStore);
            UpdateKVOperation op = new UpdateKVOperation(new OperationMetadata(1, "merge key prefix values"), store -> {
                for (Map.Entry<String, Object> entry : store.getByPrefix(keyPrefix1).entrySet()) {
                    String[] parts = entry.getKey().split("/");
                    if (parts.length != 3) {
                        continue;
                    }
                    Object second = store.get(keyPrefix2 + "/" + parts[1] + "/" + parts[2]);
                    if (second != null) {
                        store.set(keyPrefix3 + "/" + parts[1] + "/" + parts[2],
                                "{\"key1\":\"value1\",\"key2\":\"value2\",\"key3\":\"value3\",\"key4\":\"value4\"}");
                    }
                }
            });

            assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(op)));
            assertEquals("{\"key1\":\"value1\",\"key2\":\"value2\",\"key3\":\"value3\",\"key4\":\"value4\"}",
                    kvStore.get(keyPrefix3 + "/user1/scope1"));
            assertEquals("{\"key1\":\"value1\",\"key2\":\"value2\"}", kvStore.get(keyPrefix1 + "/user1/scope1"));
            assertEquals("{\"key3\":\"value3\",\"key4\":\"value4\"}", kvStore.get(keyPrefix2 + "/user1/scope1"));
            assertEquals("1", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
        } finally {
            KvPrefixRegistry.getInstance().unregister(keyPrefix1);
            KvPrefixRegistry.getInstance().unregister(keyPrefix2);
            KvPrefixRegistry.getInstance().unregister(keyPrefix3);
        }
    }

    @Test
    void tryMigrateSkipsJsonMergeDecodeErrorsAndContinuesOtherKeys() {
        String keyPrefix1 = "KEY_PREFIX1";
        String keyPrefix2 = "KEY_PREFIX2";
        String keyPrefix3 = "KEY_PREFIX3";
        KvPrefixRegistry.getInstance().registerCurrent(keyPrefix1);
        KvPrefixRegistry.getInstance().registerCurrent(keyPrefix2);
        KvPrefixRegistry.getInstance().registerCurrent(keyPrefix3);
        try {
            TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
            kvStore.set(keyPrefix1 + "/user1/scope1", "{\"key1\":\"value1\"}");
            kvStore.set(keyPrefix2 + "/user1/scope1", "{\"key2\":\"value2\"}");
            kvStore.set(keyPrefix1 + "/bad/scope", "{not-json");
            kvStore.set(keyPrefix2 + "/bad/scope", "{\"ignored\":\"value\"}");

            KvMigrator migrator = new KvMigrator(kvStore);
            UpdateKVOperation op =
                new UpdateKVOperation(new OperationMetadata(1, "merge valid JSON and skip decode errors"), store -> {
                    for (Map.Entry<String, Object> entry : store.getByPrefix(keyPrefix1).entrySet()) {
                        String[] parts = entry.getKey().split("/");
                        if (parts.length != 3) {
                            continue;
                        }
                        String targetKey = keyPrefix3 + "/" + parts[1] + "/" + parts[2];
                        Object value2 = store.get(keyPrefix2 + "/" + parts[1] + "/" + parts[2]);
                        try {
                            Map<String, Object> merged = new java.util.LinkedHashMap<>();
                            if (entry.getValue() != null) {
                                merged.putAll(MAPPER.readValue(String.valueOf(entry.getValue()), new TypeReference<>() {
                                }));
                            }
                            if (value2 != null) {
                                merged.putAll(MAPPER.readValue(String.valueOf(value2), new TypeReference<>() {
                                }));
                            }
                            store.set(targetKey, MAPPER.writeValueAsString(merged));
                        } catch (JsonProcessingException ignored) {
                            continue;
                        }
                    }
                });

            assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(op)));
            assertEquals("{\"key1\":\"value1\",\"key2\":\"value2\"}", kvStore.get(keyPrefix3 + "/user1/scope1"));
            assertEquals(null, kvStore.get(keyPrefix3 + "/bad/scope"));
            assertEquals("1", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
        } finally {
            KvPrefixRegistry.getInstance().unregister(keyPrefix1);
            KvPrefixRegistry.getInstance().unregister(keyPrefix2);
            KvPrefixRegistry.getInstance().unregister(keyPrefix3);
        }
    }

    @Test
    void tryMigrateHandlesSpecialCharacterKeyNames() {
        TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
        List<String> specialKeys = List.of("key/with/slashes", "key:with:colons", "key-with-dashes",
                "key_with_underscores", "key.with.dots", "key@with@at", "key#with#hash");
        for (String key : specialKeys) {
            kvStore.set(key, "value_" + key);
        }

        KvMigrator migrator = new KvMigrator(kvStore);
        UpdateKVOperation op =
            new UpdateKVOperation(new OperationMetadata(1, "rename special character keys"), store -> {
                for (String oldKey : specialKeys) {
                    Object value = store.get(oldKey);
                    if (value != null) {
                        store.set("renamed_" + oldKey, value);
                        store.delete(oldKey);
                    }
                }
            });

        assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(op)));
        for (String oldKey : specialKeys) {
            assertEquals(null, kvStore.get(oldKey));
            assertEquals("value_" + oldKey, kvStore.get("renamed_" + oldKey));
        }
        assertEquals("1", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void tryMigrateResumesFromCurrentVersion() {
        TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "1");
        kvStore.set("key1", "value1");

        KvMigrator migrator = new KvMigrator(kvStore);
        List<UpdateKVOperation> operations = List.of(
                new UpdateKVOperation(new OperationMetadata(2, "migrate v2"), store -> store.set("key2", "value2")),
                new UpdateKVOperation(new OperationMetadata(3, "migrate v3"), store -> store.set("key3", "value3")));

        assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.copyOf(operations)));
        assertEquals("value2", kvStore.get("key2"));
        assertEquals("value3", kvStore.get("key3"));
        assertEquals("3", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void tryMigrateContinuesOnlyRemainingOperationsFromPartialState() {
        TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "2");
        kvStore.set("key2", "value2");

        KvMigrator migrator = new KvMigrator(kvStore);
        List<UpdateKVOperation> operations = List.of(
                new UpdateKVOperation(new OperationMetadata(1, "migrate v1"), store -> store.set("key1", "value1")),
                new UpdateKVOperation(new OperationMetadata(2, "migrate v2"), store -> store.set("key2", "value2")),
                new UpdateKVOperation(new OperationMetadata(3, "migrate v3"), store -> store.set("key3", "value3")));

        assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.copyOf(operations)));
        assertEquals(null, kvStore.get("key1"));
        assertEquals("value2", kvStore.get("key2"));
        assertEquals("value3", kvStore.get("key3"));
        assertEquals("3", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }
}
