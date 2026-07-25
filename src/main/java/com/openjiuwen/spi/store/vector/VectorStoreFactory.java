/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.vector;

import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory and registry for vector store instances.
 * <p>
 * Built-in types are discovered via {@link ServiceLoader} from
 * {@code META-INF/services/com.openjiuwen.spi.store.vector.VectorStoreProvider}.
 * Service adapters can register additional types via
 * {@link #register(String, VectorStoreProvider)} without modifying Core source.
 * <p>
 * Calling point: RAG retrieval, IR indexing, etc.
 *
 * @since 0.1.12
 * @see VectorStoreProvider
 * @see BaseVectorStore
 */
public final class VectorStoreFactory {
    private static final Map<String, VectorStoreProvider> REGISTRY = new ConcurrentHashMap<>();

    static {
        // Discover and register providers via ServiceLoader
        for (VectorStoreProvider provider : ServiceLoader.load(VectorStoreProvider.class)) {
            REGISTRY.putIfAbsent(provider.typeName(), provider);
        }
        // Register aliases for common short names
        if (REGISTRY.containsKey("in_memory") && !REGISTRY.containsKey("memory")) {
            REGISTRY.put("memory", REGISTRY.get("in_memory"));
        }
        if (REGISTRY.containsKey("pgvector") && !REGISTRY.containsKey("pg")) {
            REGISTRY.put("pg", REGISTRY.get("pgvector"));
        }
        if (REGISTRY.containsKey("elasticsearch") && !REGISTRY.containsKey("es")) {
            REGISTRY.put("es", REGISTRY.get("elasticsearch"));
        }
    }

    private VectorStoreFactory() {
    }

    /**
     * Register a vector store provider for a given type name.
     *
     * @param type     the store type name (e.g. "milvus", "custom_weaviate")
     * @param provider the provider that creates BaseVectorStore instances
     */
    public static void register(String type, VectorStoreProvider provider) {
        REGISTRY.put(type, provider);
    }

    /**
     * Create a vector store from a type name and configuration.
     *
     * @param storeType the store type
     * @param conf      the configuration map
     * @return a new BaseVectorStore instance
     * @throws IllegalArgumentException if storeType is null
     * @throws UnsupportedOperationException if no provider is registered for the type
     */
    public static BaseVectorStore create(String storeType, Map<String, Object> conf) {
        if (storeType == null) {
            throw new IllegalArgumentException("storeType cannot be null");
        }
        VectorStoreProvider provider = REGISTRY.get(storeType.toLowerCase(Locale.ROOT));
        if (provider == null) {
            throw new UnsupportedOperationException(
                    "No vector store provider registered for type: " + storeType);
        }
        return provider.create(conf != null ? conf : Map.of());
    }

    /**
     * Create a vector store with empty configuration.
     *
     * @param storeType the store type
     * @return a new BaseVectorStore instance
     * @throws IllegalArgumentException if storeType is null
     */
    public static BaseVectorStore create(String storeType) {
        return create(storeType, Map.of());
    }

    /**
     * Check whether a provider is registered for the given type.
     *
     * @param type the store type name
     * @return true if a provider exists
     */
    public static boolean hasProvider(String type) {
        if (type == null) {
            return false;
        }
        return REGISTRY.containsKey(type.toLowerCase(Locale.ROOT));
    }
}
