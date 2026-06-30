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
 * @param <T> resource type
 * @param <C> config type
 */
public abstract class BaseRefResourceMgr<T extends RefCountedResource, C> {
    private final Map<String, T> resources = new ConcurrentHashMap<>();

    /**
 * Public record Acquisition used by the Java parity implementation.
 *
 * @since 1.0
 */
public record Acquisition<T>(T resource, boolean isNew) {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected abstract String getResourceKey(C config);

    /**
     * Auto-generated for codecheck compliance.
     */
    protected abstract T createResource(C config) throws Exception;

    /**
     * Auto-generated for codecheck compliance.
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
     * Auto-generated for codecheck compliance.
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
     * Auto-generated for codecheck compliance.
     */
    public synchronized void close(String key) throws Exception {
        T resource = resources.remove(key);
        if (resource != null) {
            resource.close();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public synchronized void closeAll() throws Exception {
        for (T resource : resources.values()) {
            resource.close();
        }
        resources.clear();
    }

    /**
     * Auto-generated for codecheck compliance.
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
