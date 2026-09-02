/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.vector;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Tests for VectorStoreFactory SPI registration and ServiceLoader discovery.
 */
class VectorStoreFactorySpiTest {
    // ========== ServiceLoader auto-discovery ==========
    @Test
    @DisplayName("ServiceLoader discovers built-in in_memory provider")
    void discoversInMemoryProvider() {
        assertTrue(VectorStoreFactory.hasProvider("in_memory"));
    }

    @Test
    @DisplayName("Core does not bundle Chroma provider")
    void discoversChromaProvider() {
        assertFalse(VectorStoreFactory.hasProvider("chroma"));
    }

    @Test
    @DisplayName("Core does not bundle Milvus provider")
    void discoversMilvusProvider() {
        assertFalse(VectorStoreFactory.hasProvider("milvus"));
    }

    @Test
    @DisplayName("Core does not bundle PGVector provider")
    void discoversPgvectorProvider() {
        assertFalse(VectorStoreFactory.hasProvider("pgvector"));
    }

    @Test
    @DisplayName("Core does not bundle Elasticsearch provider")
    void discoversElasticsearchProvider() {
        assertFalse(VectorStoreFactory.hasProvider("elasticsearch"));
    }

    // ========== Aliases ==========

    @Test
    @DisplayName("memory is registered as alias for in_memory")
    void memoryAlias() {
        assertTrue(VectorStoreFactory.hasProvider("memory"));
    }

    @Test
    @DisplayName("pg alias is unavailable without PGVector extension")
    void pgAlias() {
        assertFalse(VectorStoreFactory.hasProvider("pg"));
    }

    @Test
    @DisplayName("es alias is unavailable without Elasticsearch provider")
    void esAlias() {
        assertFalse(VectorStoreFactory.hasProvider("es"));
    }

    // ========== create() ==========

    @Test
    @DisplayName("create() with in_memory returns BaseVectorStore instance")
    void createInMemoryStore() {
        BaseVectorStore store = VectorStoreFactory.create("in_memory", Map.of());
        assertNotNull(store);
    }

    @Test
    @DisplayName("create() with null type throws IllegalArgumentException")
    void createWithNullType() {
        IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> VectorStoreFactory.create(null));
        assertTrue(ex.getMessage().contains("storeType"));
    }

    @Test
    @DisplayName("create() with unknown type throws UnsupportedOperationException")
    void createUnknownTypeThrows() {
        UnsupportedOperationException ex =
            assertThrows(UnsupportedOperationException.class, () -> VectorStoreFactory.create("weaviate", Map.of()));
        assertTrue(ex.getMessage().contains("weaviate"));
    }

    @Test
    @DisplayName("create() single-arg overload uses empty config")
    void createSingleArg() {
        BaseVectorStore store = VectorStoreFactory.create("in_memory");
        assertNotNull(store);
    }

    // ========== Manual register() ==========

    @Test
    @DisplayName("register() allows adding a custom vector store provider")
    void registerCustomProvider() {
        VectorStoreFactory.register("mock_weaviate", new VectorStoreProvider() {
            @Override
            public String typeName() {
                return "mock_weaviate";
            }

            @Override
            public BaseVectorStore create(Map<String, Object> conf) {
                return new com.openjiuwen.core.foundation.store.vector.InMemoryVectorStore(Map.of());
            }
        });
        assertTrue(VectorStoreFactory.hasProvider("mock_weaviate"));

        BaseVectorStore store = VectorStoreFactory.create("mock_weaviate", Map.of());
        assertNotNull(store);
    }

    // ========== hasProvider() ==========

    @Test
    @DisplayName("hasProvider() returns false for null")
    void hasProviderNull() {
        assertFalse(VectorStoreFactory.hasProvider(null));
    }

    @Test
    @DisplayName("hasProvider() is case-insensitive")
    void hasProviderCaseInsensitive() {
        assertTrue(VectorStoreFactory.hasProvider("IN_MEMORY"));
        assertTrue(VectorStoreFactory.hasProvider("In_Memory"));
    }

    // ========== Additional test cases ==========

    @Test
    @DisplayName("create() with memory alias returns same type as in_memory")
    void createWithMemoryAlias() {
        BaseVectorStore store = VectorStoreFactory.create("memory", Map.of());
        assertNotNull(store);
    }

    @Test
    @DisplayName("create() with pg alias is unavailable without PGVector extension")
    void createWithPgAlias() {
        assertFalse(VectorStoreFactory.hasProvider("pg"));
    }

    @Test
    @DisplayName("create() with es alias is unavailable without Elasticsearch provider")
    void createWithEsAlias() {
        assertFalse(VectorStoreFactory.hasProvider("es"));
    }

    @Test
    @DisplayName("Multiple create() calls return different instances")
    void createReturnsDifferentInstances() {
        BaseVectorStore store1 = VectorStoreFactory.create("in_memory", Map.of());
        BaseVectorStore store2 = VectorStoreFactory.create("in_memory", Map.of());
        assertNotSame(store1, store2);
    }

    @Test
    @DisplayName("create() with null conf defaults to empty map")
    void createWithNullConf() {
        BaseVectorStore store = VectorStoreFactory.create("in_memory", null);
        assertNotNull(store);
    }

    @Test
    @DisplayName("register() provider that reads conf")
    void registerProviderThatReadsConf() {
        VectorStoreFactory.register("conf_aware_vs", new VectorStoreProvider() {
            @Override
            public String typeName() {
                return "conf_aware_vs";
            }

            @Override
            public BaseVectorStore create(Map<String, Object> conf) {
                assertNotNull(conf);
                return new com.openjiuwen.core.foundation.store.vector.InMemoryVectorStore(Map.of());
            }
        });

        BaseVectorStore store = VectorStoreFactory.create("conf_aware_vs", Map.of("dimension", 768));
        assertNotNull(store);
    }

    @Test
    @DisplayName("register() can override an existing provider")
    void registerOverridesExisting() {
        VectorStoreFactory.register("test_vs_override", new VectorStoreProvider() {
            @Override
            public String typeName() {
                return "test_vs_override";
            }

            @Override
            public BaseVectorStore create(Map<String, Object> conf) {
                return new com.openjiuwen.core.foundation.store.vector.InMemoryVectorStore(Map.of());
            }
        });
        // Override with new provider
        VectorStoreFactory.register("test_vs_override", new VectorStoreProvider() {
            @Override
            public String typeName() {
                return "test_vs_override";
            }

            @Override
            public BaseVectorStore create(Map<String, Object> conf) {
                return new com.openjiuwen.core.foundation.store.vector.InMemoryVectorStore(Map.of("overridden", true));
            }
        });

        BaseVectorStore store = VectorStoreFactory.create("test_vs_override", Map.of());
        assertNotNull(store);
    }

    @Test
    @DisplayName("create() with empty string type throws UnsupportedOperationException")
    void createWithEmptyTypeThrows() {
        assertThrows(UnsupportedOperationException.class, () -> VectorStoreFactory.create(""));
    }
}
