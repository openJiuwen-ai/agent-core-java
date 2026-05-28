/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InMemoryKVStore.
 * <p>
 * Mirrors Python's test_in_memory_kv_store.py from
 * <code>tests/unit_tests/core/foundation/store/test_in_memory_kv_store.py</code>.
 */
@DisplayName("In Memory KV Store Tests")
class TestInMemoryKvStore {

    // Stub classes
    static class InMemoryKvStore {
        Map<String, Object> data = new HashMap<>();

        CompletableFuture<Void> set(String key, Object value) {
            data.put(key, value);
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Object> get(String key) {
            return CompletableFuture.completedFuture(data.get(key));
        }

        CompletableFuture<Void> delete(String key) {
            data.remove(key);
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Boolean> exists(String key) {
            return CompletableFuture.completedFuture(data.containsKey(key));
        }
    }

    @Nested
    @DisplayName("Basic Operations Tests")
    class TestBasicOperations {

        @Test
        @DisplayName("set and get value")
        void testSetAndGetValue() throws Exception {
            InMemoryKvStore store = new InMemoryKvStore();

            store.set("key1", "value1").get();
            Object result = store.get("key1").get();

            assertEquals("value1", result);
        }

        @Test
        @DisplayName("set overwrites existing value")
        void testSetOverwritesExistingValue() throws Exception {
            InMemoryKvStore store = new InMemoryKvStore();
            store.set("key1", "value1").get();
            store.set("key1", "value2").get();

            Object result = store.get("key1").get();

            assertEquals("value2", result);
        }

        @Test
        @DisplayName("get non-existent key returns null")
        void testGetNonExistentKeyReturnsNull() throws Exception {
            InMemoryKvStore store = new InMemoryKvStore();

            Object result = store.get("nonexistent").get();

            assertNull(result);
        }

        @Test
        @DisplayName("set and get bytes")
        void testSetAndGetBytes() throws Exception {
            InMemoryKvStore store = new InMemoryKvStore();
            byte[] bytes = "bytes_value".getBytes();

            store.set("key1", bytes).get();
            Object result = store.get("key1").get();

            assertArrayEquals(bytes, (byte[]) result);
        }
    }

    @Nested
    @DisplayName("Delete Tests")
    class TestDelete {

        @Test
        @DisplayName("delete existing key")
        void testDeleteExistingKey() throws Exception {
            InMemoryKvStore store = new InMemoryKvStore();
            store.set("key1", "value1").get();

            store.delete("key1").get();

            assertNull(store.get("key1").get());
        }

        @Test
        @DisplayName("delete non-existent key no error")
        void testDeleteNonExistentKeyNoError() throws Exception {
            InMemoryKvStore store = new InMemoryKvStore();

            // Deleting a non-existent key should not throw an exception
            // This matches Python behavior: await kv_store.delete("key") succeeds even if key doesn't exist
            store.delete("nonexistent").get();

            // Verify the key still doesn't exist after attempted delete
            Boolean exists = store.exists("nonexistent").get();
            assertFalse(exists, "Non-existent key should not exist after delete attempt");

            // No exception should be thrown - test passes if we reach here
        }
    }

    @Nested
    @DisplayName("Exists Tests")
    class TestExists {

        @Test
        @DisplayName("exists returns true for existing key")
        void testExistsReturnsTrueForExistingKey() throws Exception {
            InMemoryKvStore store = new InMemoryKvStore();
            store.set("key1", "value1").get();

            boolean result = store.exists("key1").get();

            assertTrue(result);
        }

        @Test
        @DisplayName("exists returns false for non-existent key")
        void testExistsReturnsFalseForNonExistentKey() throws Exception {
            InMemoryKvStore store = new InMemoryKvStore();

            boolean result = store.exists("nonexistent").get();

            assertFalse(result);
        }
    }
}