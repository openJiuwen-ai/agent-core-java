/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.spi.store;

import com.openjiuwen.spi.store.kv.InMemoryKVStoreProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KVStoreFactory SPI registration and ServiceLoader discovery.
 */
class KVStoreFactorySpiTest {

    // ========== ServiceLoader auto-discovery ==========

    @Test
    @DisplayName("ServiceLoader discovers built-in in_memory provider")
    void discoversInMemoryProvider() {
        assertTrue(KVStoreFactory.hasProvider("in_memory"));
    }

    // ========== create() ==========

    @Test
    @DisplayName("create() with in_memory returns BaseKVStore instance")
    void createInMemoryStore() {
        BaseKVStore store = KVStoreFactory.create("in_memory", Map.of());
        assertNotNull(store);
    }

    @Test
    @DisplayName("create() with null conf defaults to empty map")
    void createWithNullConf() {
        BaseKVStore store = KVStoreFactory.create("in_memory", null);
        assertNotNull(store);
    }

    @Test
    @DisplayName("ServiceLoader discovers redis provider")
    void discoversRedisProvider() {
        assertTrue(KVStoreFactory.hasProvider("redis"));
    }

    @Test
    @DisplayName("create() with unknown type throws IllegalArgumentException")
    void createUnknownTypeThrows() {
        // "redis" is a registered SPI provider after issue #34; use a truly unknown type.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> KVStoreFactory.create("hbase", Map.of()));
        assertTrue(ex.getMessage().contains("hbase"));
    }

    // ========== Manual register() ==========

    @Test
    @DisplayName("register() allows adding a custom KV store provider")
    void registerCustomProvider() {
        KVStoreFactory.register("mock_redis", new KVStoreProvider() {
            @Override
            public String typeName() { return "mock_redis"; }
            @Override
            public BaseKVStore create(Map<String, Object> conf) { return new InMemoryKVStoreProvider().create(Map.of()); }
        });
        assertTrue(KVStoreFactory.hasProvider("mock_redis"));

        BaseKVStore store = KVStoreFactory.create("mock_redis", Map.of());
        assertNotNull(store);
    }

    @Test
    @DisplayName("register() can override an existing provider")
    void registerOverridesExisting() {
        KVStoreFactory.register("in_memory", new KVStoreProvider() {
            @Override
            public String typeName() { return "in_memory"; }
            @Override
            public BaseKVStore create(Map<String, Object> conf) { return new InMemoryKVStoreProvider().create(Map.of()); }
        });
        BaseKVStore store = KVStoreFactory.create("in_memory", Map.of());
        assertNotNull(store);
    }

    // ========== hasProvider() ==========

    @Test
    @DisplayName("hasProvider() returns false for null")
    void hasProviderNull() {
        assertFalse(KVStoreFactory.hasProvider(null));
    }

    @Test
    @DisplayName("hasProvider() returns false for unknown type")
    void hasProviderUnknown() {
        assertFalse(KVStoreFactory.hasProvider("hbase"));
    }

    // ========== Additional test cases ==========

    @Test
    @DisplayName("Multiple create() calls return different instances")
    void createReturnsDifferentInstances() {
        BaseKVStore store1 = KVStoreFactory.create("in_memory", Map.of());
        BaseKVStore store2 = KVStoreFactory.create("in_memory", Map.of());
        assertNotSame(store1, store2);
    }

    @Test
    @DisplayName("register() provider that reads conf")
    void registerProviderThatReadsConf() {
        KVStoreFactory.register("conf_aware_kv", new KVStoreProvider() {
            @Override
            public String typeName() { return "conf_aware_kv"; }
            @Override
            public BaseKVStore create(Map<String, Object> conf) {
                assertNotNull(conf);
                return new InMemoryKVStoreProvider().create(Map.of());
            }
        });

        BaseKVStore store = KVStoreFactory.create("conf_aware_kv", Map.of("url", "redis://localhost:6379"));
        assertNotNull(store);
    }

    @Test
    @DisplayName("create() with empty string type throws IllegalArgumentException")
    void createWithEmptyTypeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> KVStoreFactory.create("", Map.of()));
    }

    @Test
    @DisplayName("hasProvider() returns true for in_memory")
    void hasProviderInMemory() {
        assertTrue(KVStoreFactory.hasProvider("in_memory"));
    }

    @Test
    @DisplayName("register and create with conf containing nested map")
    void createWithNestedConf() {
        KVStoreFactory.register("nested_conf_kv", new KVStoreProvider() {
            @Override
            public String typeName() { return "nested_conf_kv"; }
            @Override
            public BaseKVStore create(Map<String, Object> conf) {
                // Provider should receive the full conf including nested maps
                Object conn = conf.get("connection");
                return new InMemoryKVStoreProvider().create(Map.of());
            }
        });

        BaseKVStore store = KVStoreFactory.create("nested_conf_kv",
                Map.of("connection", Map.of("host", "localhost", "port", 6379)));
        assertNotNull(store);
    }
}
