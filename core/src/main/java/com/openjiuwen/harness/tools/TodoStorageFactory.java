/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TodoStorageFactory.
 *
 * @since 0.1.7
 */
public final class TodoStorageFactory {
    private static final Map<String, TodoStorageProvider> REGISTRY = new ConcurrentHashMap<>();

    static {
        for (TodoStorageProvider provider : ServiceLoader.load(TodoStorageProvider.class)) {
            REGISTRY.putIfAbsent(provider.typeName(), provider);
        }
    }

    private TodoStorageFactory() {
    }

    /**
     * register.
     *
     * @param type type
     * @param provider provider
     * @since 0.1.7
     */
    public static void register(String type, TodoStorageProvider provider) {
        REGISTRY.put(type, provider);
    }

    /**
     * create.
     *
     * @param type type
     * @param conf conf
     * @return the result
     * @since 0.1.7
     */
    public static TodoStorage create(String type, Map<String, Object> conf) {
        TodoStorageProvider provider = REGISTRY.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("No TodoStorage provider registered for type: " + type);
        }
        return provider.create(conf != null ? conf : Map.of());
    }

    /**
     * hasProvider.
     *
     * @param type type
     * @return the result
     * @since 0.1.7
     */
    public static boolean hasProvider(String type) {
        return type != null && REGISTRY.containsKey(type);
    }
}
