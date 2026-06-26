/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.migrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.common.KvPrefixRegistry;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.UpdateKVOperation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Missing supplemental parity tests for KV migration behavior.
 *
 * <p>Mirrors Python's {@code TestKVMigrator} in
 * {@code tests/unit_tests/core/memory/migration/migrator/test_kv_migrator.py}.</p>
 */
class KvMigratorMissingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    void basicMigrationRenamesTwoKeysAndIsIdempotent() {
        InMemoryKVStore store = new InMemoryKVStore();
        KvMigrator migrator = new KvMigrator(store);
        set(store, "old_key_v1", "old_value_v1");
        set(store, "old_key_v2", "old_value_v2");
        List<BaseOperation> operations = List.of(
                operation(1, "Migrate old_key_v1 to new_key_v1", currentStore -> {
                    Object oldValue = get(currentStore, "old_key_v1");
                    if (oldValue != null) {
                        set(currentStore, "new_key_v1", oldValue);
                        delete(currentStore, "old_key_v1");
                    }
                }),
                operation(2, "Migrate old_key_v2 to new_key_v2", currentStore -> {
                    Object oldValue = get(currentStore, "old_key_v2");
                    if (oldValue != null) {
                        set(currentStore, "new_key_v2", oldValue);
                        delete(currentStore, "old_key_v2");
                    }
                })
        );

        assertThat(migrate(migrator, operations)).isTrue();

        assertThat(get(store, "old_key_v1")).isNull();
        assertThat(get(store, "old_key_v2")).isNull();
        assertThat(get(store, "new_key_v1")).isEqualTo("old_value_v1");
        assertThat(get(store, "new_key_v2")).isEqualTo("old_value_v2");
        assertThat(get(store, KvMigrator.KV_SCHEMA_VERSION)).isEqualTo("2");
        assertThat(migrate(migrator, operations)).isTrue();
    }

    @Test
    void keyRenameOnlyTouchesMatchingPrefix() {
        InMemoryKVStore store = new InMemoryKVStore();
        KvMigrator migrator = new KvMigrator(store);
        String keyPrefix1 = "KEY_PREFIX1";
        String keyPrefix2 = "KEY_PREFIX2";
        Map<String, String> testData = linkedMap(
                keyPrefix1 + "/user1/scope1", "value1",
                keyPrefix1 + "/user2/scope2", "value2",
                keyPrefix1 + "/user3/scope3", "value3",
                keyPrefix2 + "/user1/scope1", "protected_value1",
                keyPrefix2 + "/user2/scope2", "protected_value2",
                keyPrefix2 + "/user3/scope3", "protected_value3",
                "other_key1", "other_value1",
                "other_key2", "other_value2");
        testData.forEach((key, value) -> set(store, key, value));
        List<BaseOperation> operations = List.of(operation(1, "Rename keys", currentStore -> {
            for (Map.Entry<String, Object> entry : currentStore.getByPrefix(keyPrefix1).join().entrySet()) {
                String[] parts = entry.getKey().split("/");
                if (parts.length == 3) {
                    set(currentStore, parts[0] + "/" + parts[2] + "/" + parts[1], entry.getValue());
                    delete(currentStore, entry.getKey());
                }
            }
        }));

        assertThat(migrate(migrator, operations)).isTrue();

        assertThat(get(store, keyPrefix1 + "/user1/scope1")).isNull();
        assertThat(get(store, keyPrefix1 + "/user2/scope2")).isNull();
        assertThat(get(store, keyPrefix1 + "/user3/scope3")).isNull();
        assertThat(get(store, keyPrefix1 + "/scope1/user1")).isEqualTo("value1");
        assertThat(get(store, keyPrefix1 + "/scope2/user2")).isEqualTo("value2");
        assertThat(get(store, keyPrefix1 + "/scope3/user3")).isEqualTo("value3");
        assertThat(get(store, keyPrefix2 + "/user1/scope1")).isEqualTo("protected_value1");
        assertThat(get(store, keyPrefix2 + "/user2/scope2")).isEqualTo("protected_value2");
        assertThat(get(store, keyPrefix2 + "/user3/scope3")).isEqualTo("protected_value3");
        assertThat(get(store, "other_key1")).isEqualTo("other_value1");
        assertThat(get(store, "other_key2")).isEqualTo("other_value2");
        assertThat(get(store, KvMigrator.KV_SCHEMA_VERSION)).isEqualTo("1");
    }

    @Test
    void keyValueMergeCreatesTargetKeysAndKeepsOriginals() {
        InMemoryKVStore store = new InMemoryKVStore();
        KvMigrator migrator = new KvMigrator(store);
        String keyPrefix1 = "KEY_PREFIX1";
        String keyPrefix2 = "KEY_PREFIX2";
        String keyPrefix3 = "KEY_PREFIX3";
        Map<String, String> testData = linkedMap(
                keyPrefix1 + "/user1/scope1", json(linkedMap("key1", "value1", "key2", "value2")),
                keyPrefix1 + "/user2/scope2", json(linkedMap("key1", "value5", "key2", "value6")),
                keyPrefix2 + "/user1/scope1", json(linkedMap("key3", "value3", "key4", "value4")),
                keyPrefix2 + "/user2/scope2", json(linkedMap("key3", "value7", "key4", "value8")),
                keyPrefix2 + "/user1/scope1/extra", json(linkedMap("key5", "value5")));
        testData.forEach((key, value) -> set(store, key, value));
        List<BaseOperation> operations = List.of(operation(1, "Merge values", currentStore -> {
            for (Map.Entry<String, Object> entry : currentStore.getByPrefix(keyPrefix1).join().entrySet()) {
                String[] parts = entry.getKey().split("/");
                if (parts.length != 3) {
                    continue;
                }
                String userId = parts[1];
                String scopeId = parts[2];
                Map<String, String> merged = new LinkedHashMap<>();
                merged.putAll(readStringMap(entry.getValue()));
                Object second = get(currentStore, keyPrefix2 + "/" + userId + "/" + scopeId);
                if (second != null) {
                    merged.putAll(readStringMap(second));
                }
                set(currentStore, keyPrefix3 + "/" + userId + "/" + scopeId, json(merged));
            }
        }));

        assertThat(migrate(migrator, operations)).isTrue();

        assertThat(readStringMap(get(store, keyPrefix3 + "/user1/scope1")))
                .containsExactlyEntriesOf(linkedMap("key1", "value1", "key2", "value2",
                        "key3", "value3", "key4", "value4"));
        assertThat(readStringMap(get(store, keyPrefix3 + "/user2/scope2")))
                .containsExactlyEntriesOf(linkedMap("key1", "value5", "key2", "value6",
                        "key3", "value7", "key4", "value8"));
        assertThat(get(store, keyPrefix1 + "/user1/scope1")).isEqualTo(testData.get(keyPrefix1 + "/user1/scope1"));
        assertThat(get(store, keyPrefix2 + "/user1/scope1")).isEqualTo(testData.get(keyPrefix2 + "/user1/scope1"));
        assertThat(get(store, KvMigrator.KV_SCHEMA_VERSION)).isEqualTo("1");
    }

    @Test
    void versionControlExecutesOnlyPendingOperations() {
        InMemoryKVStore store = new InMemoryKVStore();
        KvMigrator migrator = new KvMigrator(store);
        set(store, KvMigrator.KV_SCHEMA_VERSION, "1");
        set(store, "key2", "value1");
        set(store, "key4", "value4");
        List<BaseOperation> operations = List.of(
                operation(1, "Rename key1 to key2", currentStore -> {
                    Object oldValue = get(currentStore, "key1");
                    if (oldValue != null) {
                        set(currentStore, "key2", oldValue);
                        delete(currentStore, "key1");
                    }
                }),
                operation(2, "Rename key2 to key3", currentStore -> {
                    Object oldValue = get(currentStore, "key2");
                    if (oldValue != null) {
                        set(currentStore, "key3", oldValue);
                        delete(currentStore, "key2");
                    }
                }),
                operation(3, "Merge key3 and key4 into key5", currentStore -> {
                    Map<String, String> merged = new LinkedHashMap<>();
                    Object key3 = get(currentStore, "key3");
                    Object key4 = get(currentStore, "key4");
                    if (key3 != null) {
                        merged.put("key3", String.valueOf(key3));
                    }
                    if (key4 != null) {
                        merged.put("key4", String.valueOf(key4));
                    }
                    if (!merged.isEmpty()) {
                        set(currentStore, "key5", json(merged));
                        delete(currentStore, "key3");
                        delete(currentStore, "key4");
                    }
                })
        );

        assertThat(migrate(migrator, operations)).isTrue();

        assertThat(get(store, "key1")).isNull();
        assertThat(get(store, "key2")).isNull();
        assertThat(get(store, "key3")).isNull();
        assertThat(get(store, "key4")).isNull();
        assertThat(readStringMap(get(store, "key5"))).containsExactlyEntriesOf(
                linkedMap("key3", "value1", "key4", "value4"));
        assertThat(get(store, KvMigrator.KV_SCHEMA_VERSION)).isEqualTo("3");
    }

    @Test
    void invalidVersionFormatRaisesException() {
        InMemoryKVStore store = new InMemoryKVStore();
        set(store, KvMigrator.KV_SCHEMA_VERSION, "invalid_version");
        KvMigrator migrator = new KvMigrator(store);

        assertThatThrownBy(() -> migrate(migrator, List.of(operation(1, "Migrate",
                currentStore -> set(currentStore, "migrated", "true")))))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Invalid SCHEMA_VERSION format: 'invalid_version'. "
                        + "Expected numeric string or integer.");
    }

    @Test
    void validNumericStringVersionMigrates() {
        InMemoryKVStore store = new InMemoryKVStore();
        set(store, KvMigrator.KV_SCHEMA_VERSION, "1");
        set(store, "old_key", "old_value");

        assertThat(migrate(new KvMigrator(store), List.of(operation(2, "Migrate", currentStore -> {
            Object oldValue = get(currentStore, "old_key");
            if (oldValue != null) {
                set(currentStore, "new_key", oldValue);
                delete(currentStore, "old_key");
            }
        })))).isTrue();

        assertThat(get(store, "old_key")).isNull();
        assertThat(get(store, "new_key")).isEqualTo("old_value");
        assertThat(get(store, KvMigrator.KV_SCHEMA_VERSION)).isEqualTo("2");
    }

    @Test
    void integerVersionTypeMigrates() {
        InMemoryKVStore store = new InMemoryKVStore();
        set(store, KvMigrator.KV_SCHEMA_VERSION, 1);
        set(store, "old_key", "old_value");

        assertThat(migrate(new KvMigrator(store), List.of(operation(2, "Migrate", currentStore -> {
            Object oldValue = get(currentStore, "old_key");
            if (oldValue != null) {
                set(currentStore, "new_key", oldValue);
                delete(currentStore, "old_key");
            }
        })))).isTrue();

        assertThat(get(store, "old_key")).isNull();
        assertThat(get(store, "new_key")).isEqualTo("old_value");
        assertThat(get(store, KvMigrator.KV_SCHEMA_VERSION)).isEqualTo("2");
    }

    @Test
    void missingVersionFieldRunsInitialMigration() {
        InMemoryKVStore store = new InMemoryKVStore();

        assertThat(migrate(new KvMigrator(store), List.of(operation(1, "Initialize",
                currentStore -> set(currentStore, "initialized", "true"))))).isTrue();

        assertThat(get(store, "initialized")).isEqualTo("true");
        assertThat(get(store, KvMigrator.KV_SCHEMA_VERSION)).isEqualTo("1");
    }

    @Test
    void schemaVersionZeroMigrates() {
        InMemoryKVStore store = new InMemoryKVStore();
        set(store, KvMigrator.KV_SCHEMA_VERSION, 0);

        assertThat(migrate(new KvMigrator(store), List.of(operation(1, "Migrate",
                currentStore -> set(currentStore, "migrated", "true"))))).isTrue();

        assertThat(get(store, "migrated")).isEqualTo("true");
        assertThat(get(store, KvMigrator.KV_SCHEMA_VERSION)).isEqualTo("1");
    }

    @Test
    void schemaVersionLargeNumberMigrates() {
        InMemoryKVStore store = new InMemoryKVStore();
        set(store, KvMigrator.KV_SCHEMA_VERSION, 999999);

        assertThat(migrate(new KvMigrator(store), List.of(operation(1000000, "Migrate",
                currentStore -> set(currentStore, "migrated", "true"))))).isTrue();

        assertThat(get(store, "migrated")).isEqualTo("true");
        assertThat(get(store, KvMigrator.KV_SCHEMA_VERSION)).isEqualTo("1000000");
    }

    @Test
    void negativeSchemaVersionIsAllowedAndMigrates() {
        InMemoryKVStore store = new InMemoryKVStore();
        set(store, KvMigrator.KV_SCHEMA_VERSION, -1);

        assertThat(migrate(new KvMigrator(store), List.of(operation(1, "Migrate",
                currentStore -> set(currentStore, "migrated", "true"))))).isTrue();

        assertThat(get(store, "migrated")).isEqualTo("true");
        assertThat(get(store, KvMigrator.KV_SCHEMA_VERSION)).isEqualTo("1");
    }

    @Test
    void floatSchemaVersionRaisesException() {
        InMemoryKVStore store = new InMemoryKVStore();
        set(store, KvMigrator.KV_SCHEMA_VERSION, 1.5d);

        assertThatThrownBy(() -> migrate(new KvMigrator(store), List.of(operation(2, "Migrate",
                currentStore -> set(currentStore, "migrated", "true")))))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Invalid SCHEMA_VERSION type: Double. Expected string or integer.");
    }

    @Test
    void specialCharacterKeyNamesAreRenamed() {
        InMemoryKVStore store = new InMemoryKVStore();
        List<String> specialKeys = List.of("key/with/slashes", "key:with:colons", "key-with-dashes",
                "key_with_underscores", "key.with.dots", "key@with@at", "key#with#hash");
        for (String key : specialKeys) {
            set(store, key, "value_" + key);
        }

        assertThat(migrate(new KvMigrator(store), List.of(operation(1, "Rename special keys", currentStore -> {
            for (String oldKey : specialKeys) {
                Object oldValue = get(currentStore, oldKey);
                if (oldValue != null) {
                    set(currentStore, "renamed_" + oldKey, oldValue);
                    delete(currentStore, oldKey);
                }
            }
        })))).isTrue();

        for (String oldKey : specialKeys) {
            assertThat(get(store, oldKey)).isNull();
            assertThat(get(store, "renamed_" + oldKey)).isEqualTo("value_" + oldKey);
        }
        assertThat(get(store, KvMigrator.KV_SCHEMA_VERSION)).isEqualTo("1");
    }

    @Test
    void migrationInterruptAndResumeRunsRemainingVersions() {
        InMemoryKVStore store = new InMemoryKVStore();
        set(store, KvMigrator.KV_SCHEMA_VERSION, "1");
        set(store, "key1", "value1");

        assertThat(migrate(new KvMigrator(store), List.of(
                operation(2, "Migrate v2", currentStore -> set(currentStore, "key2", "value2")),
                operation(3, "Migrate v3", currentStore -> set(currentStore, "key3", "value3"))))).isTrue();

        assertThat(get(store, "key2")).isEqualTo("value2");
        assertThat(get(store, "key3")).isEqualTo("value3");
        assertThat(get(store, KvMigrator.KV_SCHEMA_VERSION)).isEqualTo("3");
    }

    @Test
    void partialMigrationContinuesFromCurrentVersion() {
        InMemoryKVStore store = new InMemoryKVStore();
        set(store, KvMigrator.KV_SCHEMA_VERSION, "2");
        set(store, "key2", "value2");

        assertThat(migrate(new KvMigrator(store), List.of(
                operation(1, "Migrate v1", currentStore -> set(currentStore, "key1", "value1")),
                operation(2, "Migrate v2", currentStore -> set(currentStore, "key2", "value2")),
                operation(3, "Migrate v3", currentStore -> set(currentStore, "key3", "value3"))))).isTrue();

        assertThat(get(store, "key1")).isNull();
        assertThat(get(store, "key2")).isEqualTo("value2");
        assertThat(get(store, "key3")).isEqualTo("value3");
        assertThat(get(store, KvMigrator.KV_SCHEMA_VERSION)).isEqualTo("3");
    }

    @Test
    void invalidEntityKeyReturnsFalse() {
        InMemoryKVStore store = new InMemoryKVStore();

        boolean result = new KvMigrator(store).tryMigrate("invalid_entity_key", List.of(operation(1, "Migrate",
                currentStore -> set(currentStore, "migrated", "true")))).join();

        assertThat(result).isFalse();
    }

    @Test
    void migrationFailureRollsBackToInitialPrefixState() {
        InMemoryKVStore store = new InMemoryKVStore();
        String testPrefix = "TEST_PREFIX_ROLLBACK";
        KvPrefixRegistry.getInstance().registerCurrent(testPrefix);
        try {
            set(store, KvMigrator.KV_SCHEMA_VERSION, "1");
            set(store, testPrefix + "/initial_key1", "initial_value1");
            set(store, testPrefix + "/initial_key2", "initial_value2");
            List<BaseOperation> operations = List.of(
                    operation(2, "Migrate v2", currentStore -> {
                        set(currentStore, testPrefix + "/v2_key", "v2_value");
                        delete(currentStore, testPrefix + "/initial_key1");
                    }),
                    operation(3, "Migrate v3", currentStore -> {
                        set(currentStore, testPrefix + "/v3_key", "v3_value");
                        delete(currentStore, testPrefix + "/initial_key2");
                        throw new IllegalStateException("Migration v3 failed");
                    })
            );

            assertThat(migrate(new KvMigrator(store), operations)).isFalse();

            assertThat(get(store, testPrefix + "/initial_key1")).isEqualTo("initial_value1");
            assertThat(get(store, testPrefix + "/initial_key2")).isEqualTo("initial_value2");
            assertThat(get(store, KvMigrator.KV_SCHEMA_VERSION)).isEqualTo("1");
            assertThat(get(store, testPrefix + "/v2_key")).isNull();
            assertThat(get(store, testPrefix + "/v3_key")).isNull();
        } finally {
            KvPrefixRegistry.getInstance().unregister(testPrefix);
        }
    }

    @Test
    void migrationFailureRollsBackPrefixDataAndKeepsUnregisteredData() {
        InMemoryKVStore store = new InMemoryKVStore();
        String testPrefix = "TEST_PREFIX";
        KvPrefixRegistry.getInstance().registerCurrent(testPrefix);
        try {
            set(store, KvMigrator.KV_SCHEMA_VERSION, "1");
            set(store, testPrefix + "/key1", "value1");
            set(store, testPrefix + "/key2", "value2");
            set(store, "other_key", "other_value");

            assertThat(migrate(new KvMigrator(store), List.of(operation(2, "Migrate v2", currentStore -> {
                set(currentStore, testPrefix + "/new_key", "new_value");
                delete(currentStore, testPrefix + "/key1");
                throw new IllegalStateException("Migration v2 failed");
            })))).isFalse();

            assertThat(get(store, testPrefix + "/key1")).isEqualTo("value1");
            assertThat(get(store, testPrefix + "/key2")).isEqualTo("value2");
            assertThat(get(store, "other_key")).isEqualTo("other_value");
            assertThat(get(store, KvMigrator.KV_SCHEMA_VERSION)).isEqualTo("1");
            assertThat(get(store, testPrefix + "/new_key")).isNull();
        } finally {
            KvPrefixRegistry.getInstance().unregister(testPrefix);
        }
    }

    private static boolean migrate(KvMigrator migrator, List<BaseOperation> operations) {
        return migrator.tryMigrate(KvMigrator.KV_ENTITY_KEY, operations).join();
    }

    private static UpdateKVOperation operation(int schemaVersion, String description, StoreAction action) {
        return new UpdateKVOperation(new OperationMetadata(schemaVersion, description), store -> {
            try {
                action.accept(store);
                return CompletableFuture.completedFuture(null);
            } catch (Exception exception) {
                return CompletableFuture.failedFuture(exception);
            }
        });
    }

    private static void set(BaseKVStore store, String key, Object value) {
        store.set(key, value).join();
    }

    private static Object get(BaseKVStore store, String key) {
        return store.get(key).join();
    }

    private static void delete(BaseKVStore store, String key) {
        store.delete(key).join();
    }

    private static String json(Map<String, String> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(exception);
        }
    }

    private static Map<String, String> readStringMap(Object value) {
        try {
            return OBJECT_MAPPER.readValue(String.valueOf(value), new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(exception);
        }
    }

    private static Map<String, String> linkedMap(String key1, String value1, String key2, String value2) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(key1, value1);
        result.put(key2, value2);
        return result;
    }

    private static Map<String, String> linkedMap(String key1, String value1) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(key1, value1);
        return result;
    }

    private static Map<String, String> linkedMap(String key1, String value1,
                                                 String key2, String value2,
                                                 String key3, String value3,
                                                 String key4, String value4) {
        Map<String, String> result = linkedMap(key1, value1, key2, value2);
        result.put(key3, value3);
        result.put(key4, value4);
        return result;
    }

    private static Map<String, String> linkedMap(String key1, String value1,
                                                 String key2, String value2,
                                                 String key3, String value3,
                                                 String key4, String value4,
                                                 String key5, String value5) {
        Map<String, String> result = linkedMap(key1, value1, key2, value2, key3, value3, key4, value4);
        result.put(key5, value5);
        return result;
    }

    private static Map<String, String> linkedMap(String key1, String value1,
                                                 String key2, String value2,
                                                 String key3, String value3,
                                                 String key4, String value4,
                                                 String key5, String value5,
                                                 String key6, String value6,
                                                 String key7, String value7,
                                                 String key8, String value8) {
        Map<String, String> result = linkedMap(key1, value1, key2, value2, key3, value3, key4, value4);
        result.put(key5, value5);
        result.put(key6, value6);
        result.put(key7, value7);
        result.put(key8, value8);
        return result;
    }

    private interface StoreAction {
        void accept(BaseKVStore store) throws Exception;
    }
}
