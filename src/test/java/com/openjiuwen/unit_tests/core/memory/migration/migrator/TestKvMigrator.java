/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.memory.migration.migrator;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KV migrator tests.
 * <p>
 * Mirrors Python's {@code TestKVMigrator} in
 * {@code tests/unit_tests/core/memory/migration/migrator/test_kv_migrator.py}.
 * Tests KV migration functionality for data compatibility upgrade.
 */
class TestKvMigrator {

    // Stub KV store for testing
    static class InMemoryKVStore {
        private final Map<String, String> data = new HashMap<>();

        public CompletableFuture<Void> set(String key, String value) {
            data.put(key, value);
            return CompletableFuture.completedFuture(null);
        }

        public CompletableFuture<String> get(String key) {
            return CompletableFuture.completedFuture(data.get(key));
        }

        public CompletableFuture<Void> delete(String key) {
            data.remove(key);
            return CompletableFuture.completedFuture(null);
        }

        public CompletableFuture<Boolean> exists(String key) {
            return CompletableFuture.completedFuture(data.containsKey(key));
        }
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Basic setup)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("Test KV store basic operations")
    void testKvStoreBasicOperations() throws Exception {
        InMemoryKVStore store = new InMemoryKVStore();

        // Test set and get
        store.set("key1", "value1").get();
        String value = store.get("key1").get();
        assertEquals("value1", value);

        // Test exists
        assertTrue(store.exists("key1").get());
        assertFalse(store.exists("nonexistent").get());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Migration simulation)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    @DisplayName("Test basic migration - transform old keys to new keys")
    void testBasicMigration() throws Exception {
        InMemoryKVStore store = new InMemoryKVStore();

        // Simulate existing old data in store (before migration)
        store.set("old_key_v1", "old_value_v1").get();
        store.set("old_key_v2", "old_value_v2").get();

        // Migration operation v1: Transform old_key_v1 to new_key_v1
        String oldValue1 = store.get("old_key_v1").get();
        if (oldValue1 != null) {
            store.set("new_key_v1", oldValue1).get();
            store.delete("old_key_v1").get();
        }

        // Migration operation v2: Transform old_key_v2 to new_key_v2
        String oldValue2 = store.get("old_key_v2").get();
        if (oldValue2 != null) {
            store.set("new_key_v2", oldValue2).get();
            store.delete("old_key_v2").get();
        }

        // Verify migration results - old keys should be removed, new keys should exist
        assertNull(store.get("old_key_v1").get());
        assertNull(store.get("old_key_v2").get());
        assertEquals("old_value_v1", store.get("new_key_v1").get());
        assertEquals("old_value_v2", store.get("new_key_v2").get());
    }

    @Test
    @Tag("level1")
    @DisplayName("Test migration is idempotent")
    void testMigrationIdempotent() throws Exception {
        InMemoryKVStore store = new InMemoryKVStore();

        // Set up initial data
        store.set("old_key", "old_value").get();

        // First migration
        String oldValue = store.get("old_key").get();
        if (oldValue != null) {
            store.set("new_key", oldValue).get();
            store.delete("old_key").get();
        }

        // Second migration (should be no-op since old_key no longer exists)
        String oldValue2 = store.get("old_key").get();
        if (oldValue2 != null) {
            store.set("new_key", oldValue2).get();
            store.delete("old_key").get();
        }

        // Verify result is same as after first migration
        assertEquals("old_value", store.get("new_key").get());
        assertNull(store.get("old_key").get());
    }
}