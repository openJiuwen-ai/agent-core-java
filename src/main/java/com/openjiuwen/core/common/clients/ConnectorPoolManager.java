/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import com.openjiuwen.core.common.utils.Singleton;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Manager for connector pools with lifecycle management.
 * <p>
 * Mirrors Python's {@code ConnectorPoolManager} in
 * {@code openjiuwen/core/common/clients/connector_pool.py}.
 *
 * <p>This class manages a collection of connector pools, handling creation,
 * reference counting, cleanup, and resource limits.
 */
public class ConnectorPoolManager {

    private static final Logger logger = Logger.getLogger(ConnectorPoolManager.class.getName());
    private static final Map<String, Function<ConnectorPoolConfig, CompletableFuture<ConnectorPool>>> 
        providers = new ConcurrentHashMap<>();

    static {
        registerProvider("default", config -> CompletableFuture.completedFuture(new TcpConnectorPool(config)));
    }

    private final Map<String, ConnectorPool> connectorPools = new ConcurrentHashMap<>();
    private final ConnectorPoolConfig defaultConfig = new ConnectorPoolConfig();
    private final String defaultConfigKey = defaultConfig.generateKey();
    private final int maxPools;
    private final ReentrantLock lock = new ReentrantLock();
    private boolean closed = false;

    public ConnectorPoolManager() {
        this(100);
    }

    public ConnectorPoolManager(int maxPools) {
        this.maxPools = maxPools;
    }

    /**
     * Register a connector pool type provider.
     *
     * @param poolType the type identifier
     * @param provider the factory function
     */
    public static void registerProvider(String poolType, 
            Function<ConnectorPoolConfig, CompletableFuture<ConnectorPool>> provider) {
        providers.put(poolType, provider);
        logger.info("Registered connector pool type: " + poolType);
    }

    /**
     * Get or create a connector pool.
     *
     * @param poolType type of connector pool
     * @param config optional configuration
     * @return a CompletableFuture containing the connector pool
     */
    public CompletableFuture<ConnectorPool> getConnectorPool(String poolType, 
            ConnectorPoolConfig config) {
        if (closed) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("ConnectorPoolManager is closed"));
        }

        ConnectorPoolConfig poolConfig = config != null ? config : defaultConfig;
        String key = config != null ? poolConfig.generateKey() : defaultConfigKey;

        logger.fine("Getting connector pool: type=" + poolType + ", key=" + key);

        lock.lock();
        try {
            // Check for existing pool
            ConnectorPool existingPool = connectorPools.get(key);
            if (existingPool != null) {
                if (existingPool.isClosed()) {
                    connectorPools.remove(key);
                    logger.warning("Removed closed connector pool, key=" + key);
                } else {
                    existingPool.incrementRef();
                    logger.fine("Incremented ref count for pool " + key);
                    return CompletableFuture.completedFuture(existingPool);
                }
            }

            // Check max pools limit
            if (connectorPools.size() >= maxPools) {
                logger.warning("Maximum pools reached (" + maxPools + "), evicting oldest pool");
                evictOldestPool();
            }

            // Create new pool
            return createConnectorPool(poolType, poolConfig)
                .thenApply(pool -> {
                    connectorPools.put(key, pool);
                    logger.info("Created new connector pool: type=" + poolType + ", key=" + key);
                    return pool;
                });
        } finally {
            lock.unlock();
        }
    }

    public CompletableFuture<ConnectorPool> getConnectorPool(ConnectorPoolConfig config) {
        return getConnectorPool("default", config);
    }

    /**
     * Evict the oldest unused connector pool.
     */
    private void evictOldestPool() {
        // Find idle pools (ref count = 0)
        connectorPools.entrySet().stream()
            .filter(e -> e.getValue().getRefCount() == 0 && !e.getValue().isClosed())
            .sorted(Comparator.comparing(e -> e.getValue().getLastUsed()))
            .findFirst()
            .ifPresent(entry -> {
                String oldestKey = entry.getKey();
                logger.info("Evicting oldest idle pool: " + oldestKey);
                forceRemovePool(oldestKey);
            });

        // If no idle pools, evict oldest by creation time
        if (connectorPools.size() >= maxPools) {
            connectorPools.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().getCreatedAt()))
                .findFirst()
                .ifPresent(entry -> {
                    String oldestKey = entry.getKey();
                    logger.warning("No idle pools, evicting oldest active pool: " + oldestKey);
                    forceRemovePool(oldestKey);
                });
        }
    }

    /**
     * Forcefully remove a connector pool.
     */
    private void forceRemovePool(String key) {
        ConnectorPool pool = connectorPools.remove(key);
        if (pool != null) {
            pool.close()
                .exceptionally(e -> {
                    logger.warning("Error closing pool " + key + ": " + e.getMessage());
                    return null;
                });
        }
    }

    /**
     * Create a new connector pool using registered provider.
     */
    private CompletableFuture<ConnectorPool> createConnectorPool(String poolType, 
            ConnectorPoolConfig config) {
        Function<ConnectorPoolConfig, CompletableFuture<ConnectorPool>> provider = 
            providers.get(poolType);
        
        if (provider == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Unknown connector type: " + poolType));
        }

        return provider.apply(config);
    }

    /**
     * Release a connector pool reference.
     */
    public void releaseConnectorPool(ConnectorPoolConfig config) {
        if (closed) {
            return;
        }

        if (config != null) {
            String key = config.generateKey();
            ConnectorPool pool = connectorPools.get(key);
            if (pool != null) {
                pool.decrementRef();
                logger.fine("Released pool " + key);
            }
        }
    }

    /**
     * Close a specific connector pool.
     */
    public CompletableFuture<Void> closeConnectorPool(ConnectorPoolConfig config, 
            boolean force) {
        if (closed) {
            return CompletableFuture.completedFuture(null);
        }

        if (config == null) {
            return CompletableFuture.completedFuture(null);
        }

        String key = config.generateKey();
        lock.lock();
        try {
            ConnectorPool pool = connectorPools.get(key);
            if (pool != null) {
                if (force || pool.isClosed() || pool.getRefCount() == 0) {
                    connectorPools.remove(key);
                    return pool.close();
                } else {
                    logger.warning("Cannot close pool " + key + " with ref_count=" + pool.getRefCount());
                }
            }
        } finally {
            lock.unlock();
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Close all connector pools.
     */
    public CompletableFuture<Void> closeAll() {
        if (closed) {
            return CompletableFuture.completedFuture(null);
        }

        closed = true;
        logger.info("Closing all connector pools, total=" + connectorPools.size());

        CompletableFuture<Void>[] futures = connectorPools.keySet().stream()
            .map(key -> {
                ConnectorPool pool = connectorPools.remove(key);
                return pool != null ? pool.close() : CompletableFuture.completedFuture(null);
            })
            .toArray(CompletableFuture[]::new);

        CompletableFuture<Void> result = CompletableFuture.allOf(futures);
        // Reset closed flag so the manager can be reused (important for test isolation
        // where the singleton persists across test classes in the same JVM)
        result.whenComplete((v, ex) -> closed = false);
        return result;
    }

    /**
     * Get statistics for all connector pools.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_connector_pools", connectorPools.size());
        stats.put("max_pools", maxPools);
        stats.put("closed", closed);

        Map<String, Object> connectorStats = new HashMap<>();
        for (Map.Entry<String, ConnectorPool> entry : connectorPools.entrySet()) {
            connectorStats.put(entry.getKey(), entry.getValue().getStat());
        }
        stats.put("connectors", connectorStats);

        return stats;
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * Get the global connector pool manager instance.
     */
    public static ConnectorPoolManager getInstance() {
        return Singleton.getInstance(ConnectorPoolManager.class, ConnectorPoolManager::new);
    }

    public static ConnectorPoolManager getConnectorPoolManager() {
        return getInstance();
    }
}
