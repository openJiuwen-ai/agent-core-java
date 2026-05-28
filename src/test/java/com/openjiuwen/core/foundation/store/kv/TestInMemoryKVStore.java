/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.kv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InMemoryKVStore.
 * <p>
 * Mirrors Python's test_in_memory_kv_store.py from
 * <code>tests/unit_tests/core/foundation/store/test_in_memory_kv_store.py</code>.
 *
 * <p>Note: Python tests use async operations; Java implementation is synchronous.
 */
@DisplayName("InMemoryKVStore Tests")
class TestInMemoryKVStore {

    @Nested
    @DisplayName("Basic Operations")
    class TestInMemoryKVStoreBasicOperations {

        @Test
        @DisplayName("test set and get")
        void testSetAndGet() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1");

            Object result = store.get("key1");
            assertEquals("value1", result);
        }

        @Test
        @DisplayName("set overwrites existing value")
        void testSetOverwritesExistingValue() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1");
            store.set("key1", "value2");

            Object result = store.get("key1");
            assertEquals("value2", result);
        }

        @Test
        @DisplayName("get nonexistent key returns null")
        void testGetNonexistentKeyReturnsNull() {
            InMemoryKVStore store = new InMemoryKVStore();

            Object result = store.get("nonexistent");
            assertNull(result);
        }

        @Test
        @DisplayName("set and get bytes")
        void testSetAndGetBytes() {
            InMemoryKVStore store = new InMemoryKVStore();
            byte[] bytesValue = "bytes_value".getBytes();
            store.set("key1", bytesValue);

            Object result = store.get("key1");
            assertArrayEquals(bytesValue, (byte[]) result);
        }

        @Test
        @DisplayName("delete existing key")
        void testDeleteExistingKey() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1");
            store.delete("key1");

            Object result = store.get("key1");
            assertNull(result);
        }

        @Test
        @DisplayName("delete nonexistent key no error")
        void testDeleteNonexistentKeyNoError() {
            InMemoryKVStore store = new InMemoryKVStore();
            // Should not throw
            store.delete("nonexistent");
        }

        @Test
        @DisplayName("exists returns true")
        void testExistsTrue() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1");

            boolean result = store.exists("key1");
            assertTrue(result);
        }

        @Test
        @DisplayName("exists returns false")
        void testExistsFalse() {
            InMemoryKVStore store = new InMemoryKVStore();

            boolean result = store.exists("nonexistent");
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Exclusive Set Operations")
    class TestInMemoryKVStoreExclusiveSet {

        @Test
        @DisplayName("exclusive set new key")
        void testExclusiveSetNewKey() {
            InMemoryKVStore store = new InMemoryKVStore();
            boolean result = store.exclusiveSet("key1", "value1", null);

            assertTrue(result);
            assertEquals("value1", store.get("key1"));
        }

        @Test
        @DisplayName("exclusive set existing key fails")
        void testExclusiveSetExistingKeyFails() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1");

            boolean result = store.exclusiveSet("key1", "value2", null);

            assertFalse(result);
            assertEquals("value1", store.get("key1"));
        }

        @Test
        @DisplayName("exclusive set with expiry")
        void testExclusiveSetWithExpiry() throws InterruptedException {
            InMemoryKVStore store = new InMemoryKVStore();
            store.exclusiveSet("key1", "value1", 1);

            assertTrue(store.exists("key1"));
            Thread.sleep(1100);
            assertFalse(store.exists("key1"));
        }

        @Test
        @DisplayName("exclusive set allows setting after expiry")
        void testExclusiveSetAllowsSettingAfterExpiry() throws InterruptedException {
            InMemoryKVStore store = new InMemoryKVStore();
            boolean result1 = store.exclusiveSet("key1", "value1", 1);
            assertTrue(result1);

            // Wait for expiry
            Thread.sleep(1100);

            // Should be able to set again
            boolean result2 = store.exclusiveSet("key1", "value2", 1);
            assertTrue(result2);
        }
    }

    @Nested
    @DisplayName("Prefix Operations")
    class TestInMemoryKVStorePrefixOperations {

        @Test
        @DisplayName("get by prefix")
        void testGetByPrefix() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("user:1:name", "Alice");
            store.set("user:1:email", "alice@example.com");
            store.set("user:2:name", "Bob");
            store.set("admin:settings", "value");

            Map<String, Object> result = store.getByPrefix("user:1");

            assertEquals(2, result.size());
            assertEquals("Alice", result.get("user:1:name"));
            assertEquals("alice@example.com", result.get("user:1:email"));
        }

        @Test
        @DisplayName("get by prefix empty result")
        void testGetByPrefixEmptyResult() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1");

            Map<String, Object> result = store.getByPrefix("nonexistent:");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("delete by prefix")
        void testDeleteByPrefix() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("user:1:name", "Alice");
            store.set("user:1:email", "alice@example.com");
            store.set("user:2:name", "Bob");

            store.deleteByPrefix("user:1", null);

            assertFalse(store.exists("user:1:name"));
            assertFalse(store.exists("user:1:email"));
            assertTrue(store.exists("user:2:name"));
        }
    }

    @Nested
    @DisplayName("Batch Operations")
    class TestInMemoryKVStoreBatchOperations {

        @Test
        @DisplayName("mget multiple keys")
        void testMgetMultipleKeys() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1");
            store.set("key2", "value2");
            store.set("key3", "value3");

            List<Object> result = store.mget(List.of("key1", "key2", "key3"));

            assertEquals(3, result.size());
            assertEquals("value1", result.get(0));
            assertEquals("value2", result.get(1));
            assertEquals("value3", result.get(2));
        }

        @Test
        @DisplayName("mget with nonexistent keys")
        void testMgetWithNonexistentKeys() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1");

            List<Object> result = store.mget(List.of("key1", "nonexistent"));

            assertEquals(2, result.size());
            assertEquals("value1", result.get(0));
            assertNull(result.get(1));
        }

        @Test
        @DisplayName("batch delete")
        void testBatchDelete() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1");
            store.set("key2", "value2");
            store.set("key3", "value3");

            int deleted = store.batchDelete(List.of("key1", "key2"), null);

            assertEquals(2, deleted);
            assertFalse(store.exists("key1"));
            assertFalse(store.exists("key2"));
            assertTrue(store.exists("key3"));
        }
    }

    @Nested
    @DisplayName("Expiry Operations")
    class TestInMemoryKVStoreExpiryOperations {

        @Test
        @DisplayName("set with expiry")
        void testSetWithExpiry() throws InterruptedException {
            InMemoryKVStore store = new InMemoryKVStore();
            // Note: Java BaseKVStore may not have set with expiry directly
            // Use exclusiveSet for expiry testing
            store.exclusiveSet("key1", "value1", 1);

            assertTrue(store.exists("key1"));
            Thread.sleep(1100);
            assertFalse(store.exists("key1"));
        }

        @Test
        @DisplayName("expired key not returned by get")
        void testExpiredKeyNotReturnedByGet() throws InterruptedException {
            InMemoryKVStore store = new InMemoryKVStore();
            store.exclusiveSet("key1", "value1", 1);

            Thread.sleep(1100);
            Object result = store.get("key1");
            assertNull(result);
        }

        @Test
        @DisplayName("expired key not included in prefix results")
        void testExpiredKeyNotIncludedInPrefixResults() throws InterruptedException {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("prefix:key1", "value1");
            store.exclusiveSet("prefix:key2", "value2", 1);

            Thread.sleep(1100);
            Map<String, Object> result = store.getByPrefix("prefix:");

            assertEquals(1, result.size());
            assertTrue(result.containsKey("prefix:key1"));
        }
    }
}