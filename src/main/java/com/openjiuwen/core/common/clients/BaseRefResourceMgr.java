/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base reference-counted resource manager.
 * 
 * @since 0.1.7
 */
public abstract class BaseRefResourceMgr<T extends RefCountedResource, C> {
    private final Map<String, T> resources = new ConcurrentHashMap<>();

    /**
     * Public record Acquisition used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record Acquisition<T>(T resource, boolean isNew) {
    }

    /**
     * getResourceKey.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    protected abstract String getResourceKey(C config);

    /**
     * createResource.
     * 
     * @param config config
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    protected abstract T createResource(C config) throws Exception;

    /**
     * acquire.
     * 
     * @param config config
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public synchronized Acquisition<T> acquire(C config) throws Exception {
        String key = getResourceKey(config);
        T existing = resources.get(key);
        if (existing != null) {
            if (!existing.isClosed()) {
                existing.incrementRef();
                return new Acquisition<>(existing, false);
            }
            resources.remove(key);
        }
        T resource = createResource(config);
        resources.put(key, resource);
        return new Acquisition<>(resource, true);
    }

    /**
     * release.
     * 
     * @param config config
     * @throws Exception Exception
     * @since 0.1.7
     */
    public synchronized void release(C config) throws Exception {
        String key = getResourceKey(config);
        T resource = resources.get(key);
        if (resource == null || resource.isClosed()) {
            return;
        }
        boolean shouldClose = resource.decrementRef();
        if (shouldClose) {
            resources.remove(key);
            resource.close();
        }
    }

    /**
     * close.
     * 
     * @param key key
     * @throws Exception Exception
     * @since 0.1.7
     */
    public synchronized void close(String key) throws Exception {
        T resource = resources.remove(key);
        if (resource != null) {
            resource.close();
        }
    }

    /**
     * closeAll.
     * 
     * @throws Exception Exception
     * @since 0.1.7
     */
    public synchronized void closeAll() throws Exception {
        for (T resource : resources.values()) {
            resource.close();
        }
        resources.clear();
    }

    /**
     * getStats.
     * 
     * @return the result
     * @since 0.1.7
     */
    public synchronized Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        Map<String, Object> resourceStats = new LinkedHashMap<>();
        resources.forEach((key, resource) -> resourceStats.put(key, resource.getStats()));
        stats.put("total_resources", resources.size());
        stats.put("resources", resourceStats);
        return stats;
    }
}
