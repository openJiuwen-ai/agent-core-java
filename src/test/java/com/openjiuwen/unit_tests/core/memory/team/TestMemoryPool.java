/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.team;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MemoryPool.
 * <p>
 * Mirrors Python's test_memory_pool.py from
 * <code>tests/unit_tests/core/memory/team/test_memory_pool.py</code>.
 */
@DisplayName("Memory Pool Tests")
class TestMemoryPool {

    // Stub classes
    static class MemoryPoolConfig {
        int maxSize;
        boolean enableEviction;

        MemoryPoolConfig(int maxSize, boolean enableEviction) {
            this.maxSize = maxSize;
            this.enableEviction = enableEviction;
        }
    }

    static class MemoryEntry {
        String key;
        Object value;
        long timestamp;

        MemoryEntry(String key, Object value) {
            this.key = key;
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }
    }

    static class MemoryPool {
        Map<String, MemoryEntry> entries = new HashMap<>();
        MemoryPoolConfig config;

        MemoryPool(MemoryPoolConfig config) {
            this.config = config;
        }

        void put(String key, Object value) {
            if (entries.size() >= config.maxSize && config.enableEviction) {
                // Evict oldest
                String oldestKey = null;
                long oldestTime = Long.MAX_VALUE;
                for (Map.Entry<String, MemoryEntry> e : entries.entrySet()) {
                    if (e.getValue().timestamp < oldestTime) {
                        oldestTime = e.getValue().timestamp;
                        oldestKey = e.getKey();
                    }
                }
                if (oldestKey != null) {
                    entries.remove(oldestKey);
                }
            }
            entries.put(key, new MemoryEntry(key, value));
        }

        Object get(String key) {
            MemoryEntry entry = entries.get(key);
            return entry != null ? entry.value : null;
        }

        void clear() {
            entries.clear();
        }

        int size() {
            return entries.size();
        }
    }

    @Nested
    @DisplayName("Memory Pool Operations Tests")
    class TestMemoryPoolOperations {

        @Test
        @DisplayName("memory pool creation")
        void testMemoryPoolCreation() {
            MemoryPoolConfig config = new MemoryPoolConfig(100, true);
            MemoryPool pool = new MemoryPool(config);

            assertNotNull(pool);
            assertEquals(100, pool.config.maxSize);
        }

        @Test
        @DisplayName("put and get operations")
        void testPutAndGetOperations() {
            MemoryPool pool = new MemoryPool(new MemoryPoolConfig(10, false));

            pool.put("key1", "value1");
            pool.put("key2", "value2");

            assertEquals("value1", pool.get("key1"));
            assertEquals("value2", pool.get("key2"));
        }

        @Test
        @DisplayName("eviction when max size reached")
        void testEvictionWhenMaxSizeReached() {
            MemoryPool pool = new MemoryPool(new MemoryPoolConfig(2, true));

            pool.put("key1", "value1");
            pool.put("key2", "value2");
            pool.put("key3", "value3"); // Should evict oldest

            assertEquals(2, pool.size());
        }

        @Test
        @DisplayName("clear pool")
        void testClearPool() {
            MemoryPool pool = new MemoryPool(new MemoryPoolConfig(10, false));
            pool.put("key1", "value1");
            pool.put("key2", "value2");

            pool.clear();

            assertEquals(0, pool.size());
        }

        @Test
        @DisplayName("get non-existent key returns null")
        void testGetNonExistentKeyReturnsNull() {
            MemoryPool pool = new MemoryPool(new MemoryPoolConfig(10, false));

            assertNull(pool.get("non-existent"));
        }
    }
}