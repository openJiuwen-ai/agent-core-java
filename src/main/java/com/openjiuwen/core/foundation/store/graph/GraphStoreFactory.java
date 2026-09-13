/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import com.openjiuwen.core.common.logging.Loggers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Factory class to assemble graph store instances.
 * <p>
 * Mirrors Python's {@code GraphStoreFactory}. Thread-safe registration and creation
 * of graph store backends.
 * 
 * @since 0.1.7
 */
public final class GraphStoreFactory {
    private static final Map<String, Class<? extends GraphStore>> CLASS_MAP = new ConcurrentHashMap<>();

    /**
     * ReentrantLock.
     * 
     * @since 0.1.7
     */
    private static final ReentrantLock LOCK = new ReentrantLock();

    static {
        // Register default backend so GraphStoreFactory works out of the box
        CLASS_MAP.put("in_memory", InMemoryGraphStore.class);
    }

    /**
     * GraphStoreFactory.
     * 
     * @since 0.1.7
     */
    private GraphStoreFactory() {
        throw new UnsupportedOperationException("GraphStoreFactory should not be instantiated");
    }

    /**
     * Register a graph store backend.
     * 
     * @param name name for the backend
     * @param backend class implementing GraphStore
     * @param force whether to force register even if name already exists
     * @since 0.1.7
     */
    public static void registerBackend(String name, Class<? extends GraphStore> backend, boolean force) {
        LOCK.lock();
        try {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Backend name cannot be empty");
            }
            if (CLASS_MAP.containsKey(name) && !force) {
                throw new IllegalStateException(
                        "Entry [" + name + "] -> " + CLASS_MAP.get(name).getName() + " already exists.");
            }
            CLASS_MAP.put(name, backend);
            Loggers.STORE.info("Graph Store registered: %s", name);
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Register a graph store backend (no force).
     * 
     * @param name name
     * @param backend backend
     * @since 0.1.7
     */
    public static void registerBackend(String name, Class<? extends GraphStore> backend) {
        registerBackend(name, backend, false);
    }

    /**
     * Fetch a GraphStore instance by configuration.
     * 
     * @param config database configuration
     * @param backendName optional override for backend choice in config
     * @return instance of graph store
     * @since 0.1.7
     */
    public static GraphStore fromConfig(GraphConfig config, String backendName) {
        LOCK.lock();
        try {
            String name = (backendName != null && !backendName.isBlank()) ? backendName : config.getBackend();
            Class<? extends GraphStore> backendCls = CLASS_MAP.get(name);
            if (backendCls == null) {
                throw new IllegalArgumentException("Backend type [" + name + "] does not exist.");
            }
            try {
                var method = backendCls.getMethod("fromConfig", GraphConfig.class);
                Object result = method.invoke(null, config);
                if (result instanceof GraphStore graphStore) {
                    return graphStore;
                }
                throw new IllegalStateException("fromConfig did not return a GraphStore for backend: " + name);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create GraphStore from config for backend: " + name, e);
            }
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Fetch a GraphStore instance by configuration using config's default backend.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public static GraphStore fromConfig(GraphConfig config) {
        return fromConfig(config, null);
    }
}
