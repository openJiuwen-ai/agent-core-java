/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
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
 */
public final class GraphStoreFactory {

    private static final Map<String, Class<? extends GraphStore>> CLASS_MAP = new ConcurrentHashMap<>();
    private static final ReentrantLock LOCK = new ReentrantLock();

    static {
        // Register default backend so GraphStoreFactory works out of the box
        CLASS_MAP.put("in_memory", InMemoryGraphStore.class);
    }

    private GraphStoreFactory() {
        throw new UnsupportedOperationException("GraphStoreFactory should not be instantiated");
    }

    /**
     * Register a graph store backend.
     *
     * @param name    name for the backend
     * @param backend class implementing GraphStore
     * @param force   whether to force register even if name already exists
     * @throws IllegalArgumentException if name is empty
     * @throws IllegalStateException    if name already registered and force=false
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
     */
    public static void registerBackend(String name, Class<? extends GraphStore> backend) {
        registerBackend(name, backend, false);
    }

    /**
     * Fetch a GraphStore instance by configuration.
     *
     * @param config      database configuration
     * @param backendName optional override for backend choice in config
     * @return instance of graph store
     * @throws IllegalArgumentException if backend type not registered
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
                return (GraphStore) method.invoke(null, config);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create GraphStore from config for backend: " + name, e);
            }
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Fetch a GraphStore instance by configuration using config's default backend.
     */
    public static GraphStore fromConfig(GraphConfig config) {
        return fromConfig(config, null);
    }
}
