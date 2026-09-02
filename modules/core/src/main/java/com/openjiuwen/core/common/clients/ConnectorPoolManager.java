/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import com.openjiuwen.core.common.utils.SingletonSupport;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Manager for shared connector pools.
 * 
 * @since 0.1.7
 */
public final class ConnectorPoolManager {
    private static final Map<String, Function<ConnectorPoolConfig, ConnectorPool>> PROVIDERS =
        new ConcurrentHashMap<>();

    static {
        register("default", TcpConnectorPool::new);
        register("httpx", config -> new HttpXConnectorPool(
                config instanceof HttpXConnectorPoolConfig typed ? typed : HttpXConnectorPoolConfig.from(config)));
    }

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, ConnectorPool> connectorPools = new ConcurrentHashMap<>();

    /**
     * ConnectorPoolConfig.
     * 
     * @since 0.1.7
     */
    private final ConnectorPoolConfig defaultConfig = new ConnectorPoolConfig();
    private final int maxPools;
    private volatile boolean isClosed;

    /**
     * ConnectorPoolManager.
     * 
     * @since 0.1.7
     */
    private ConnectorPoolManager() {
        this(100);
    }

    /**
     * ConnectorPoolManager.
     * 
     * @param maxPools maxPools
     * @since 0.1.7
     */
    private ConnectorPoolManager(int maxPools) {
        this.maxPools = maxPools;
    }

    /**
     * getInstance.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static ConnectorPoolManager getInstance() {
        return SingletonSupport.getInstance(ConnectorPoolManager.class, ConnectorPoolManager::new);
    }

    /**
     * register.
     * 
     * @param type type
     * @param provider provider
     * @since 0.1.7
     */
    public static void register(String type, Function<ConnectorPoolConfig, ConnectorPool> provider) {
        PROVIDERS.put(type, provider);
    }

    /**
     * getConnectorPool.
     * 
     * @param type type
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public synchronized ConnectorPool getConnectorPool(String type, ConnectorPoolConfig config) {
        if (isClosed) {
            throw new IllegalStateException("ConnectorPoolManager is isClosed");
        }
        String resolvedType = type == null || type.isBlank() ? "default" : type;
        ConnectorPoolConfig resolvedConfig = config != null ? config : defaultConfig;
        String key = poolKey(resolvedType, resolvedConfig);
        ConnectorPool existing = connectorPools.get(key);
        if (existing != null) {
            if (!existing.isClosed()) {
                existing.incrementRef();
                return existing;
            }
            connectorPools.remove(key);
        }
        if (connectorPools.size() >= maxPools) {
            evictOldestPool();
        }
        Function<ConnectorPoolConfig, ConnectorPool> provider = PROVIDERS.get(resolvedType);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown connector type: " + resolvedType);
        }
        ConnectorPool created = provider.apply(resolvedConfig);
        connectorPools.put(key, created);
        return created;
    }

    /**
     * releaseConnectorPool.
     * 
     * @param type type
     * @param config config
     * @throws Exception Exception
     * @since 0.1.7
     */
    public synchronized void releaseConnectorPool(String type, ConnectorPoolConfig config) throws Exception {
        if (isClosed) {
            return;
        }
        String key =
            poolKey(type == null || type.isBlank() ? "default" : type, config != null ? config : defaultConfig);
        ConnectorPool pool = connectorPools.get(key);
        if (pool == null || pool.isClosed()) {
            return;
        }
        boolean shouldClose = pool.decrementRef();
        if (shouldClose) {
            connectorPools.remove(key);
            pool.close();
        }
    }

    /**
     * closeConnectorPool.
     * 
     * @param type type
     * @param config config
     * @param isForce isForce
     * @throws Exception Exception
     * @since 0.1.7
     */
    public synchronized void closeConnectorPool(String type, ConnectorPoolConfig config, boolean isForce)
            throws Exception {
        String key =
            poolKey(type == null || type.isBlank() ? "default" : type, config != null ? config : defaultConfig);
        ConnectorPool pool = connectorPools.get(key);
        if (pool == null) {
            return;
        }
        if (isForce || pool.isClosed() || pool.getRefCount() <= 0) {
            connectorPools.remove(key);
            pool.close();
        }
    }

    /**
     * closeAll.
     * 
     * @throws Exception Exception
     * @since 0.1.7
     */
    public synchronized void closeAll() throws Exception {
        isClosed = true;
        for (ConnectorPool pool : connectorPools.values()) {
            pool.close();
        }
        connectorPools.clear();
    }

    /**
     * getStats.
     * 
     * @return the result
     * @since 0.1.7
     */
    public synchronized Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        Map<String, Object> connectorStats = new LinkedHashMap<>();
        connectorPools.forEach((key, pool) -> connectorStats.put(key, pool.stat()));
        stats.put("total_connector_pools", connectorPools.size());
        stats.put("max_pools", maxPools);
        stats.put("isClosed", isClosed);
        stats.put("connectors", connectorStats);
        return stats;
    }

    synchronized void resetForTests() throws Exception {
        closeAll();
        isClosed = false;
    }

    /**
     * evictOldestPool.
     * 
     * @since 0.1.7
     */
    private void evictOldestPool() {
        connectorPools.entrySet().stream().min(Comparator.comparingLong(entry -> entry.getValue().getCreatedAtMillis()))
                .ifPresent(entry -> connectorPools.remove(entry.getKey()));
    }

    /**
     * poolKey.
     * 
     * @param type type
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private static String poolKey(String type, ConnectorPoolConfig config) {
        return type + ":" + config.generateKey();
    }
}
