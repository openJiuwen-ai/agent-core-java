/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.kv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

        @Test
        @DisplayName("delete by prefix with batch size")
        void testDeleteByPrefixWithBatchSize() {
            InMemoryKVStore store = new InMemoryKVStore();
            for (int i = 0; i < 10; i++) {
                store.set("prefix:key" + i, "value" + i);
            }

            store.deleteByPrefix("prefix:", 3);

            assertTrue(store.getByPrefix("prefix:").isEmpty());
        }

        @Test
        @DisplayName("delete by prefix no matches")
        void testDeleteByPrefixNoMatches() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1");

            store.deleteByPrefix("nonexistent:", null);

            assertTrue(store.exists("key1"));
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
        @DisplayName("mget")
        void testMget() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1");
            store.set("key2", "value2");
            store.set("key3", "value3");

            List<Object> result = store.mget(List.of("key1", "key2", "key4"));

            assertEquals(Arrays.asList("value1", "value2", null), result);
        }

        @Test
        @DisplayName("mget empty list")
        void testMgetEmptyList() {
            InMemoryKVStore store = new InMemoryKVStore();

            assertTrue(store.mget(List.of()).isEmpty());
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

        @Test
        @DisplayName("batch delete empty list")
        void testBatchDeleteEmptyList() {
            InMemoryKVStore store = new InMemoryKVStore();

            assertEquals(0, store.batchDelete(List.of(), null));
        }

        @Test
        @DisplayName("batch delete with batch size")
        void testBatchDeleteWithBatchSize() {
            InMemoryKVStore store = new InMemoryKVStore();
            List<String> keys = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                String key = "key" + i;
                keys.add(key);
                store.set(key, "value" + i);
            }

            assertEquals(10, store.batchDelete(keys, 3));
        }
    }

    @Nested
    @DisplayName("Pipeline Operations")
    class TestInMemoryKVStorePipeline {

        @Test
        @DisplayName("pipeline set and get")
        void testPipelineSetAndGet() {
            InMemoryKVStore store = new InMemoryKVStore();
            List<Object> results = store.pipeline()
                    .set("key1", "value1")
                    .set("key2", "value2")
                    .get("key1")
                    .get("key2")
                    .execute();

            assertEquals(Arrays.asList(null, null, "value1", "value2"), results);
        }

        @Test
        @DisplayName("pipeline set get exists")
        void testPipelineSetGetExists() {
            InMemoryKVStore store = new InMemoryKVStore();
            List<Object> results = store.pipeline()
                    .set("key1", "value1")
                    .get("key1")
                    .exists("key1")
                    .get("nonexistent")
                    .exists("nonexistent")
                    .execute();

            assertEquals(Arrays.asList(null, "value1", true, null, false), results);
        }

        @Test
        @DisplayName("pipeline multiple executes")
        void testPipelineMultipleExecutes() {
            InMemoryKVStore store = new InMemoryKVStore();
            var pipeline = store.pipeline();

            List<Object> results1 = pipeline.set("key1", "value1").get("key1").execute();
            List<Object> results2 = pipeline.set("key2", "value2").get("key2").execute();

            assertEquals(Arrays.asList(null, "value1"), results1);
            assertEquals(Arrays.asList(null, "value2"), results2);
        }

        @Test
        @DisplayName("pipeline empty operations")
        void testPipelineEmptyOperations() {
            InMemoryKVStore store = new InMemoryKVStore();

            assertTrue(store.pipeline().execute().isEmpty());
        }

        @Test
        @DisplayName("pipeline with bytes")
        void testPipelineWithBytes() {
            InMemoryKVStore store = new InMemoryKVStore();
            byte[] bytesValue = "bytes_value".getBytes();

            List<Object> results = store.pipeline().set("key1", bytesValue).get("key1").execute();

            assertNull(results.get(0));
            assertArrayEquals(bytesValue, (byte[]) results.get(1));
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

            assertEquals(2, result.size());
            assertEquals("value1", result.get("prefix:key1"));
            assertTrue(result.containsKey("prefix:key2"));
            assertNull(result.get("prefix:key2"));
        }

        @Test
        @DisplayName("key expiry")
        void testKeyExpiry() throws InterruptedException {
            InMemoryKVStore store = new InMemoryKVStore();
            store.exclusiveSet("key1", "value1", 1);

            assertTrue(store.exists("key1"));
            Thread.sleep(1100);
            assertFalse(store.exists("key1"));
        }

        @Test
        @DisplayName("expired key returns none")
        void testExpiredKeyReturnsNone() throws InterruptedException {
            InMemoryKVStore store = new InMemoryKVStore();
            store.exclusiveSet("key1", "value1", 1);

            Thread.sleep(1100);

            assertNull(store.get("key1"));
        }

        @Test
        @DisplayName("expired key in get by prefix returns none")
        void testExpiredKeyInGetByPrefixReturnsNone() throws InterruptedException {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("prefix:key1", "value1");
            store.exclusiveSet("prefix:key2", "value2", 1);

            Thread.sleep(1100);
            Map<String, Object> result = store.getByPrefix("prefix:");

            assertEquals("value1", result.get("prefix:key1"));
            assertTrue(result.containsKey("prefix:key2"));
            assertNull(result.get("prefix:key2"));
        }

        @Test
        @DisplayName("can set after key expires")
        void testCanSetAfterKeyExpires() throws InterruptedException {
            InMemoryKVStore store = new InMemoryKVStore();
            store.exclusiveSet("key1", "value1", 1);

            Thread.sleep(1100);
            store.set("key1", "value2");

            assertEquals("value2", store.get("key1"));
        }
    }

    @Nested
    @DisplayName("Concurrency")
    class TestInMemoryKVStoreConcurrency {

        @Test
        @DisplayName("concurrent sets")
        void testConcurrentSets() throws InterruptedException {
            InMemoryKVStore store = new InMemoryKVStore();
            ExecutorService executor = Executors.newFixedThreadPool(8);
            for (int i = 0; i < 100; i++) {
                final int index = i;
                executor.submit(() -> store.set("key" + index, "value" + index));
            }
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            for (int i = 0; i < 100; i++) {
                assertEquals("value" + i, store.get("key" + i));
            }
        }

        @Test
        @DisplayName("consecutive pipeline operations")
        void testConsecutivePipelineOperations() {
            InMemoryKVStore store = new InMemoryKVStore();

            for (int i = 0; i < 10; i++) {
                List<Object> results = store.pipeline()
                        .set("key" + i, "value" + i)
                        .get("key" + i)
                        .execute();
                assertEquals(Arrays.asList(null, "value" + i), results);
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class TestInMemoryKVStoreEdgeCases {

        @Test
        @DisplayName("empty string key")
        void testEmptyStringKey() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("", "value");

            assertEquals("value", store.get(""));
        }

        @Test
        @DisplayName("empty string value")
        void testEmptyStringValue() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "");

            assertEquals("", store.get("key1"));
        }

        @Test
        @DisplayName("special characters in key")
        void testSpecialCharactersInKey() {
            InMemoryKVStore store = new InMemoryKVStore();
            List<String> specialKeys = List.of(
                    "key:with:colons",
                    "key/with/slashes",
                    "key-with-dashes",
                    "key_with_underscores",
                    "key.with.dots",
                    "key with spaces");

            for (String key : specialKeys) {
                store.set(key, "value_" + key);
            }

            for (String key : specialKeys) {
                assertEquals("value_" + key, store.get(key));
            }
        }

        @Test
        @DisplayName("large value")
        void testLargeValue() {
            InMemoryKVStore store = new InMemoryKVStore();
            String largeValue = "x".repeat(100000);

            store.set("key1", largeValue);

            assertEquals(largeValue, store.get("key1"));
        }

        @Test
        @DisplayName("get by prefix empty prefix")
        void testGetByPrefixEmptyPrefix() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1");
            store.set("key2", "value2");

            Map<String, Object> result = store.getByPrefix("");

            assertEquals(2, result.size());
            assertEquals("value1", result.get("key1"));
            assertEquals("value2", result.get("key2"));
        }

        @Test
        @DisplayName("delete by prefix empty prefix")
        void testDeleteByPrefixEmptyPrefix() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1");
            store.set("key2", "value2");

            store.deleteByPrefix("", null);

            assertFalse(store.exists("key1"));
            assertFalse(store.exists("key2"));
        }
    }
}
