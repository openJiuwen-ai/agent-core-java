/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.memory.migration.migrator;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.common.KvPrefixRegistry;
import com.openjiuwen.core.memory.migration.migrator.KvMigrator;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.UpdateKVOperation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KV migrator tests.
 *
 * <p>Mirrors Python's {@code TestKVMigrator} in
 * {@code tests/unit_tests/core/memory/migration/migrator/test_kv_migrator.py}.
 */
class TestKvMigrator {

    @Test
    void testBasicMigration() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        KvMigrator migrator = new KvMigrator(kvStore);

        kvStore.set("old_key_v1", "old_value_v1");
        kvStore.set("old_key_v2", "old_value_v2");

        List<BaseOperation> operations = List.of(
                updateOperation(1, "migrate v1", store -> {
                    Object oldValue = store.get("old_key_v1");
                    if (oldValue != null) {
                        store.set("new_key_v1", oldValue);
                        store.delete("old_key_v1");
                    }
                }),
                updateOperation(2, "migrate v2", store -> {
                    Object oldValue = store.get("old_key_v2");
                    if (oldValue != null) {
                        store.set("new_key_v2", oldValue);
                        store.delete("old_key_v2");
                    }
                })
        );

        assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, operations));
        assertNull(kvStore.get("old_key_v1"));
        assertNull(kvStore.get("old_key_v2"));
        assertEquals("old_value_v1", kvStore.get("new_key_v1"));
        assertEquals("old_value_v2", kvStore.get("new_key_v2"));
        assertEquals("2", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
        assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, operations));
    }

    @Test
    void testKeyRenameFunctionality() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        KvMigrator migrator = new KvMigrator(kvStore);

        kvStore.set("KEY_PREFIX1/user1/scope1", "value1");
        kvStore.set("KEY_PREFIX1/user2/scope2", "value2");
        kvStore.set("KEY_PREFIX1/user3/scope3", "value3");
        kvStore.set("KEY_PREFIX2/user1/scope1", "protected_value1");
        kvStore.set("KEY_PREFIX2/user2/scope2", "protected_value2");
        kvStore.set("other_key1", "other_value1");
        kvStore.set("other_key2", "other_value2");

        assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(
                updateOperation(1, "rename keys", store -> {
                    Map<String, Object> keys = store.getByPrefix("KEY_PREFIX1");
                    for (Map.Entry<String, Object> entry : keys.entrySet()) {
                        String[] parts = entry.getKey().split("/");
                        if (parts.length == 3) {
                            String newKey = parts[0] + "/" + parts[2] + "/" + parts[1];
                            store.set(newKey, entry.getValue());
                            store.delete(entry.getKey());
                        }
                    }
                })
        )));

        assertNull(kvStore.get("KEY_PREFIX1/user1/scope1"));
        assertNull(kvStore.get("KEY_PREFIX1/user2/scope2"));
        assertNull(kvStore.get("KEY_PREFIX1/user3/scope3"));
        assertEquals("value1", kvStore.get("KEY_PREFIX1/scope1/user1"));
        assertEquals("value2", kvStore.get("KEY_PREFIX1/scope2/user2"));
        assertEquals("value3", kvStore.get("KEY_PREFIX1/scope3/user3"));
        assertEquals("protected_value1", kvStore.get("KEY_PREFIX2/user1/scope1"));
        assertEquals("protected_value2", kvStore.get("KEY_PREFIX2/user2/scope2"));
        assertEquals("other_value1", kvStore.get("other_key1"));
        assertEquals("other_value2", kvStore.get("other_key2"));
        assertEquals("1", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void testKeyValueMergeMigration() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        KvMigrator migrator = new KvMigrator(kvStore);

        kvStore.set("KEY_PREFIX1/user1/scope1", "{\"key1\":\"value1\",\"key2\":\"value2\"}");
        kvStore.set("KEY_PREFIX1/user2/scope2", "{\"key1\":\"value5\",\"key2\":\"value6\"}");
        kvStore.set("KEY_PREFIX2/user1/scope1", "{\"key3\":\"value3\",\"key4\":\"value4\"}");
        kvStore.set("KEY_PREFIX2/user2/scope2", "{\"key3\":\"value7\",\"key4\":\"value8\"}");
        kvStore.set("KEY_PREFIX2/user1/scope1/extra", "{\"key5\":\"value5\"}");

        List<BaseOperation> operations = List.of(
                updateOperation(1, "merge values", store -> {
                    Map<String, Object> prefix1Entries = store.getByPrefix("KEY_PREFIX1");
                    for (Map.Entry<String, Object> entry : prefix1Entries.entrySet()) {
                        String[] parts = entry.getKey().split("/");
                        if (parts.length != 3) {
                            continue;
                        }
                        String userId = parts[1];
                        String scopeId = parts[2];
                        Map<String, Object> merged = new LinkedHashMap<>();
                        merged.putAll(jsonMap(entry.getValue()));
                        merged.putAll(jsonMap(store.get("KEY_PREFIX2/" + userId + "/" + scopeId)));
                        store.set("KEY_PREFIX3/" + userId + "/" + scopeId, jsonString(merged));
                    }
                })
        );

        assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, operations));
        assertEquals("{\"key1\":\"value1\",\"key2\":\"value2\",\"key3\":\"value3\",\"key4\":\"value4\"}",
                kvStore.get("KEY_PREFIX3/user1/scope1"));
        assertEquals("{\"key1\":\"value5\",\"key2\":\"value6\",\"key3\":\"value7\",\"key4\":\"value8\"}",
                kvStore.get("KEY_PREFIX3/user2/scope2"));
        assertEquals("{\"key1\":\"value1\",\"key2\":\"value2\"}", kvStore.get("KEY_PREFIX1/user1/scope1"));
        assertEquals("{\"key3\":\"value3\",\"key4\":\"value4\"}", kvStore.get("KEY_PREFIX2/user1/scope1"));
        assertEquals("1", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void testVersionControl() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        KvMigrator migrator = new KvMigrator(kvStore);

        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "1");
        kvStore.set("key2", "value1");
        kvStore.set("key4", "value4");

        List<BaseOperation> operations = List.of(
                updateOperation(1, "rename key1->key2", store -> {
                    Object oldValue = store.get("key1");
                    if (oldValue != null) {
                        store.set("key2", oldValue);
                        store.delete("key1");
                    }
                }),
                updateOperation(2, "rename key2->key3", store -> {
                    Object oldValue = store.get("key2");
                    if (oldValue != null) {
                        store.set("key3", oldValue);
                        store.delete("key2");
                    }
                }),
                updateOperation(3, "merge key3 and key4 into key5", store -> {
                    Object value3 = store.get("key3");
                    Object value4 = store.get("key4");
                    if (value3 != null || value4 != null) {
                        store.set("key5", "{\"key3\":\"" + value3 + "\",\"key4\":\"" + value4 + "\"}");
                        store.delete("key3");
                        store.delete("key4");
                    }
                })
        );

        assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, operations));
        assertNull(kvStore.get("key1"));
        assertNull(kvStore.get("key2"));
        assertNull(kvStore.get("key3"));
        assertNull(kvStore.get("key4"));
        assertEquals("{\"key3\":\"value1\",\"key4\":\"value4\"}", kvStore.get("key5"));
        assertEquals("3", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void testInvalidVersionFormatRaisesException() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "invalid_version");
        KvMigrator migrator = new KvMigrator(kvStore);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(
                        updateOperation(1, "migrate", store -> store.set("migrated", "true"))
                ))
        );

        assertTrue(error.getMessage().contains("Invalid KV_SCHEMA_VERSION format"));
        assertTrue(error.getMessage().contains("invalid_version"));
        assertNull(kvStore.get("migrated"));
    }

    @Test
    void testValidNumericStringVersion() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "1");
        kvStore.set("old_key", "old_value");
        KvMigrator migrator = new KvMigrator(kvStore);

        assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(
                updateOperation(2, "migrate", store -> {
                    Object oldValue = store.get("old_key");
                    if (oldValue != null) {
                        store.set("new_key", oldValue);
                        store.delete("old_key");
                    }
                })
        )));

        assertNull(kvStore.get("old_key"));
        assertEquals("old_value", kvStore.get("new_key"));
        assertEquals("2", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void testIntegerVersionType() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, 1);
        kvStore.set("old_key", "old_value");
        KvMigrator migrator = new KvMigrator(kvStore);

        assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(
                updateOperation(2, "migrate", store -> {
                    Object oldValue = store.get("old_key");
                    if (oldValue != null) {
                        store.set("new_key", oldValue);
                        store.delete("old_key");
                    }
                })
        )));

        assertNull(kvStore.get("old_key"));
        assertEquals("old_value", kvStore.get("new_key"));
        assertEquals("2", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void testVersionFieldNotExists() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        KvMigrator migrator = new KvMigrator(kvStore);

        assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(
                updateOperation(1, "initialize", store -> store.set("initialized", "true"))
        )));

        assertEquals("true", kvStore.get("initialized"));
        assertEquals("1", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void testSchemaVersionZero() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, 0);
        KvMigrator migrator = new KvMigrator(kvStore);

        assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(
                updateOperation(1, "migrate", store -> store.set("migrated", "true"))
        )));

        assertEquals("true", kvStore.get("migrated"));
        assertEquals("1", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void testSchemaVersionLargeNumber() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, 999999);
        KvMigrator migrator = new KvMigrator(kvStore);

        assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(
                updateOperation(1000000, "migrate", store -> store.set("migrated", "true"))
        )));

        assertEquals("true", kvStore.get("migrated"));
        assertEquals("1000000", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void testSchemaVersionNegativeNumberAllowed() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, -1);
        KvMigrator migrator = new KvMigrator(kvStore);

        assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(
                updateOperation(1, "migrate", store -> store.set("migrated", "true"))
        )));

        assertEquals("true", kvStore.get("migrated"));
        assertEquals("1", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void testSchemaVersionFloatRaisesException() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, 1.5);
        KvMigrator migrator = new KvMigrator(kvStore);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(
                        updateOperation(2, "migrate", store -> store.set("migrated", "true"))
                ))
        );

        assertTrue(error.getMessage().contains("Invalid KV_SCHEMA_VERSION type"));
    }

    @Test
    void testSpecialCharacterKeyNames() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        KvMigrator migrator = new KvMigrator(kvStore);
        List<String> keys = List.of(
                "key/with/slashes",
                "key:with:colons",
                "key-with-dashes",
                "key_with_underscores",
                "key.with.dots",
                "key@with@at",
                "key#with#hash"
        );
        for (String key : keys) {
            kvStore.set(key, "value_" + key);
        }

        assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(
                updateOperation(1, "rename special keys", store -> {
                    for (String key : keys) {
                        Object oldValue = store.get(key);
                        if (oldValue != null) {
                            store.set("renamed_" + key, oldValue);
                            store.delete(key);
                        }
                    }
                })
        )));

        for (String key : keys) {
            assertNull(kvStore.get(key));
            assertEquals("value_" + key, kvStore.get("renamed_" + key));
        }
        assertEquals("1", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void testMigrationInterruptAndResume() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "1");
        kvStore.set("key1", "value1");
        KvMigrator migrator = new KvMigrator(kvStore);

        List<BaseOperation> operations = List.of(
                updateOperation(2, "migrate v2", store -> store.set("key2", "value2")),
                updateOperation(3, "migrate v3", store -> store.set("key3", "value3"))
        );

        assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, operations));
        assertEquals("value2", kvStore.get("key2"));
        assertEquals("value3", kvStore.get("key3"));
        assertEquals("3", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void testPartialMigrationContinueUpgrade() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "2");
        kvStore.set("key2", "value2");
        KvMigrator migrator = new KvMigrator(kvStore);

        List<BaseOperation> operations = List.of(
                updateOperation(1, "migrate v1", store -> store.set("key1", "value1")),
                updateOperation(2, "migrate v2", store -> store.set("key2", "value2")),
                updateOperation(3, "migrate v3", store -> store.set("key3", "value3"))
        );

        assertTrue(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, operations));
        assertNull(kvStore.get("key1"));
        assertEquals("value2", kvStore.get("key2"));
        assertEquals("value3", kvStore.get("key3"));
        assertEquals("3", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
    }

    @Test
    void testMigrationInvalidEntityKey() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        KvMigrator migrator = new KvMigrator(kvStore);

        assertFalse(migrator.tryMigrate("invalid_entity_key", List.of(
                updateOperation(1, "migrate", store -> store.set("migrated", "true"))
        )));
    }

    @Test
    void testMigrationFailureRollsBackToInitial() {
        String prefix = "TEST_PREFIX_ROLLBACK";
        KvPrefixRegistry.getInstance().registerCurrent(prefix);
        try {
            InMemoryKVStore kvStore = new InMemoryKVStore();
            KvMigrator migrator = new KvMigrator(kvStore);

            kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "1");
            kvStore.set(prefix + "/initial_key1", "initial_value1");
            kvStore.set(prefix + "/initial_key2", "initial_value2");

            List<BaseOperation> operations = List.of(
                    updateOperation(2, "migrate v2", store -> {
                        store.set(prefix + "/v2_key", "v2_value");
                        store.delete(prefix + "/initial_key1");
                    }),
                    updateOperation(3, "migrate v3", store -> {
                        store.set(prefix + "/v3_key", "v3_value");
                        store.delete(prefix + "/initial_key2");
                        throw new IllegalStateException("Migration v3 failed");
                    })
            );

            assertFalse(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, operations));
            assertEquals("initial_value1", kvStore.get(prefix + "/initial_key1"));
            assertEquals("initial_value2", kvStore.get(prefix + "/initial_key2"));
            assertEquals("1", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
            assertNull(kvStore.get(prefix + "/v2_key"));
            assertNull(kvStore.get(prefix + "/v3_key"));
        } finally {
            KvPrefixRegistry.getInstance().unregister(prefix);
        }
    }

    @Test
    void testMigrationFailureRollsBackWithPrefixData() {
        String prefix = "TEST_PREFIX";
        KvPrefixRegistry.getInstance().registerCurrent(prefix);
        try {
            InMemoryKVStore kvStore = new InMemoryKVStore();
            KvMigrator migrator = new KvMigrator(kvStore);

            kvStore.set(KvMigrator.KV_SCHEMA_VERSION, "1");
            kvStore.set(prefix + "/key1", "value1");
            kvStore.set(prefix + "/key2", "value2");
            kvStore.set("other_key", "other_value");

            List<BaseOperation> operations = List.of(
                    updateOperation(2, "failing migration", store -> {
                        store.set(prefix + "/new_key", "new_value");
                        store.delete(prefix + "/key1");
                        throw new IllegalStateException("Migration v2 failed");
                    })
            );

            assertFalse(migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, operations));
            assertEquals("value1", kvStore.get(prefix + "/key1"));
            assertEquals("value2", kvStore.get(prefix + "/key2"));
            assertEquals("other_value", kvStore.get("other_key"));
            assertEquals("1", kvStore.get(KvMigrator.KV_SCHEMA_VERSION));
            assertNull(kvStore.get(prefix + "/new_key"));
        } finally {
            KvPrefixRegistry.getInstance().unregister(prefix);
        }
    }

    private static UpdateKVOperation updateOperation(int version, String description,
                                                     java.util.function.Consumer<com.openjiuwen.spi.store.BaseKVStore> updateFunc) {
        return new UpdateKVOperation(new OperationMetadata(version, description), updateFunc);
    }

    private static Map<String, Object> jsonMap(Object json) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (json == null) {
            return result;
        }
        String value = String.valueOf(json).trim();
        if (value.length() < 2 || value.charAt(0) != '{' || value.charAt(value.length() - 1) != '}') {
            return result;
        }
        String body = value.substring(1, value.length() - 1).trim();
        if (body.isEmpty()) {
            return result;
        }
        for (String entry : body.split(",")) {
            String[] pair = entry.split(":", 2);
            if (pair.length == 2) {
                result.put(unquote(pair[0].trim()), unquote(pair[1].trim()));
            }
        }
        return result;
    }

    private static String jsonString(Map<String, Object> values) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            builder.append('"').append(entry.getKey()).append('"')
                    .append(':')
                    .append('"').append(entry.getValue()).append('"');
            first = false;
        }
        builder.append('}');
        return builder.toString();
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
