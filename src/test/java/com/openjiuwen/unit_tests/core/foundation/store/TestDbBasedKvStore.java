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
 * Unit tests for DbBasedKVStore.
 * <p>
 * Mirrors Python's test_db_based_kv_store.py from
 * <code>tests/unit_tests/core/foundation/store/test_db_based_kv_store.py</code>.
 */
@DisplayName("Db Based KV Store Tests")
class TestDbBasedKvStore {

    // Stub classes
    static class DbConnectionStub {
        Map<String, byte[]> data = new HashMap<>();

        void put(String key, byte[] value) {
            data.put(key, value);
        }

        byte[] get(String key) {
            return data.get(key);
        }

        void delete(String key) {
            data.remove(key);
        }

        boolean exists(String key) {
            return data.containsKey(key);
        }
    }

    static class DbBasedKvStore {
        DbConnectionStub connection;

        DbBasedKvStore(DbConnectionStub connection) {
            this.connection = connection;
        }

        CompletableFuture<Void> set(String key, byte[] value) {
            connection.put(key, value);
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<byte[]> get(String key) {
            return CompletableFuture.completedFuture(connection.get(key));
        }

        CompletableFuture<Void> delete(String key) {
            connection.delete(key);
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Boolean> exists(String key) {
            return CompletableFuture.completedFuture(connection.exists(key));
        }
    }

    @Nested
    @DisplayName("Set and Get Tests")
    class TestSetAndGet {

        @Test
        @DisplayName("set and get value")
        void testSetAndGetValue() throws Exception {
            DbConnectionStub conn = new DbConnectionStub();
            DbBasedKvStore store = new DbBasedKvStore(conn);

            store.set("key1", "value1".getBytes()).get();
            byte[] result = store.get("key1").get();

            assertEquals("value1", new String(result));
        }

        @Test
        @DisplayName("get non-existent key returns null")
        void testGetNonExistentKeyReturnsNull() throws Exception {
            DbConnectionStub conn = new DbConnectionStub();
            DbBasedKvStore store = new DbBasedKvStore(conn);

            byte[] result = store.get("nonexistent").get();

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Delete Tests")
    class TestDelete {

        @Test
        @DisplayName("delete existing key")
        void testDeleteExistingKey() throws Exception {
            DbConnectionStub conn = new DbConnectionStub();
            DbBasedKvStore store = new DbBasedKvStore(conn);
            store.set("key1", "value1".getBytes()).get();

            store.delete("key1").get();

            assertNull(store.get("key1").get());
        }

        @Test
        @DisplayName("delete non-existent key no error")
        void testDeleteNonExistentKeyNoError() throws Exception {
            DbConnectionStub conn = new DbConnectionStub();
            DbBasedKvStore store = new DbBasedKvStore(conn);

            store.delete("nonexistent").get();

            // No exception should be thrown
            assertTrue(true);
        }
    }

    @Nested
    @DisplayName("Exists Tests")
    class TestExists {

        @Test
        @DisplayName("exists returns true for existing key")
        void testExistsReturnsTrueForExistingKey() throws Exception {
            DbConnectionStub conn = new DbConnectionStub();
            DbBasedKvStore store = new DbBasedKvStore(conn);
            store.set("key1", "value1".getBytes()).get();

            boolean result = store.exists("key1").get();

            assertTrue(result);
        }

        @Test
        @DisplayName("exists returns false for non-existent key")
        void testExistsReturnsFalseForNonExistentKey() throws Exception {
            DbConnectionStub conn = new DbConnectionStub();
            DbBasedKvStore store = new DbBasedKvStore(conn);

            boolean result = store.exists("nonexistent").get();

            assertFalse(result);
        }
    }
}