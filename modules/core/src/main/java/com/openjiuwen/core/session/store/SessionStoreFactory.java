/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.store;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for discovering and creating tenant-aware session stores via SPI.
 *
 * @since 0.1.7
 */
public final class SessionStoreFactory {
    private static final Map<String, SessionStoreProvider> REGISTRY = new ConcurrentHashMap<>();

    static {
        for (SessionStoreProvider provider : ServiceLoader.load(SessionStoreProvider.class)) {
            REGISTRY.putIfAbsent(provider.typeName(), provider);
        }
    }

    private SessionStoreFactory() {
    }

    /**
     * Register a session store provider for the given type.
     *
     * @param type the store type name
     * @param provider the store provider
     * @since 0.1.7
     */
    public static void register(String type, SessionStoreProvider provider) {
        REGISTRY.put(type, provider);
    }

    /**
     * Create a store of the given type.
     *
     * @param type the store type name
     * @param conf the store configuration, or null for defaults
     * @return the created store
     * @since 0.1.7
     */
    public static Store create(String type, Map<String, Object> conf) {
        SessionStoreProvider provider = REGISTRY.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("No SessionStore provider registered for type: " + type);
        }
        return provider.createStore(conf != null ? conf : Map.of());
    }

    /**
     * Check whether a provider is registered for the given type.
     *
     * @param type the store type name
     * @return true if a provider is registered
     * @since 0.1.7
     */
    public static boolean hasProvider(String type) {
        return type != null && REGISTRY.containsKey(type);
    }
}
