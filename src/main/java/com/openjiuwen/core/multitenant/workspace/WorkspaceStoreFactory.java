/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant.workspace;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory that discovers and registers workspace store providers by type.
 *
 * @since 0.1.7
 */
public final class WorkspaceStoreFactory {
    private static final Map<String, WorkspaceStoreProvider> REGISTRY = new ConcurrentHashMap<>();

    static {
        for (WorkspaceStoreProvider provider : ServiceLoader.load(WorkspaceStoreProvider.class)) {
            REGISTRY.putIfAbsent(provider.typeName(), provider);
        }
    }

    private WorkspaceStoreFactory() {}

    /**
     * Registers a workspace store provider under the given type name.
     *
     * @param type the type name identifying the provider
     * @param provider the provider to register
     * @since 0.1.7
     */
    public static void register(String type, WorkspaceStoreProvider provider) {
        REGISTRY.put(type, provider);
    }

    /**
     * Creates a workspace store for the given type using the provided configuration.
     *
     * @param type the type name identifying the provider
     * @param conf the configuration passed to the provider
     * @return the created workspace store
     * @throws IllegalArgumentException if no provider is registered for the type
     * @since 0.1.7
     */
    public static WorkspaceStore create(String type, Map<String, Object> conf) {
        WorkspaceStoreProvider provider = REGISTRY.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("No workspace store provider registered for type: " + type);
        }
        return provider.create(conf != null ? conf : Map.of());
    }

    /**
     * Checks whether a provider is registered for the given type.
     *
     * @param type the type name to check
     * @return true if a provider is registered, false otherwise
     * @since 0.1.7
     */
    public static boolean hasProvider(String type) {
        return type != null && REGISTRY.containsKey(type);
    }
}
