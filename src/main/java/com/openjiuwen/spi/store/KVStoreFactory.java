/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory and registry for KV store instances.
 * <p>
 * Built-in types are discovered via {@link ServiceLoader} from
 * {@code META-INF/services/com.openjiuwen.spi.store.KVStoreProvider}.
 * Service adapters can register additional types via
 * {@link #register(String, KVStoreProvider)} without modifying Core source.
 * <p>
 * Calling point: PersistenceCheckpointer, Workflow state persistence, etc.
 *
 * @since 0.1.12
 * @see KVStoreProvider
 * @see BaseKVStore
 */
public final class KVStoreFactory {
    private static final Map<String, KVStoreProvider> REGISTRY = new ConcurrentHashMap<>();

    static {
        // Discover and register providers via ServiceLoader
        for (KVStoreProvider provider : ServiceLoader.load(KVStoreProvider.class)) {
            REGISTRY.putIfAbsent(provider.typeName(), provider);
        }
    }

    private KVStoreFactory() {
    }

    /**
     * Register a KV store provider for a given type name.
     *
     * @param type     the store type name (e.g. "in_memory", "redis", "hbase")
     * @param provider the provider that creates BaseKVStore instances
     */
    public static void register(String type, KVStoreProvider provider) {
        REGISTRY.put(type, provider);
    }

    /**
     * Create a KV store from a type and configuration.
     *
     * @param type the store type
     * @param conf the configuration map
     * @return a new BaseKVStore instance
     * @throws IllegalArgumentException if no provider is registered for the type
     */
    public static BaseKVStore create(String type, Map<String, Object> conf) {
        KVStoreProvider provider = REGISTRY.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("No KV store provider registered for type: " + type);
        }
        return provider.create(conf != null ? conf : Map.of());
    }

    /**
     * Check whether a provider is registered for the given type.
     *
     * @param type the store type name
     * @return true if a provider exists
     */
    public static boolean hasProvider(String type) {
        return type != null && REGISTRY.containsKey(type);
    }
}
