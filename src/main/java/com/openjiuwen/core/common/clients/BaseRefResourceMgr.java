/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Abstract base class for reference-counted resource managers.
 * <p>
 * Mirrors Python's {@code BaseRefResourceMgr} abstract class from
 * <code>common/clients/ref_counted.py</code>.
 *
 * <p>Provides thread-safe resource acquisition, release, and cleanup capabilities
 * using reference counting to manage shared resources efficiently.
 *
 * @param <R> the resource type, must extend RefCountedResource
 */
public abstract class BaseRefResourceMgr<R extends RefCountedResource> {

    protected final Map<String, R> resources = new ConcurrentHashMap<>();
    protected final ReentrantLock lock = new ReentrantLock();

    /**
     * Get the unique key for a resource based on its configuration.
     *
     * @param config the configuration object
     * @return the unique resource key
     */
    protected abstract String getResourceKey(Object config);

    /**
     * Create a new resource based on the configuration.
     *
     * @param config the configuration object
     * @return a CompletableFuture containing the created resource
     */
    protected abstract CompletableFuture<R> createResource(Object config);

    /**
     * Acquire a resource, creating it if necessary.
     *
     * @param config the configuration object
     * @return a CompletableFuture containing a tuple of (resource, isNew)
     */
    public CompletableFuture<Map.Entry<R, Boolean>> acquire(Object config) {
        String key = getResourceKey(config);

        lock.lock();
        try {
            R existing = resources.get(key);
            if (existing != null && !existing.isClosed()) {
                existing.incrementRef();
                return CompletableFuture.completedFuture(
                    Map.entry(existing, false)
                );
            }

            return createResource(config).thenApply(resource -> {
                resources.put(key, resource);
                return Map.entry(resource, true);
            });
        } finally {
            lock.unlock();
        }
    }

    /**
     * Release a resource, decrementing its reference count.
     *
     * @param config the configuration object
     * @return a CompletableFuture that completes when release is done
     */
    public CompletableFuture<Void> release(Object config) {
        String key = getResourceKey(config);

        lock.lock();
        try {
            R resource = resources.get(key);
            if (resource == null) {
                return CompletableFuture.completedFuture(null);
            }

            boolean shouldClose = resource.decrementRef();
            if (shouldClose) {
                resources.remove(key);
                return resource.close();
            }
            return CompletableFuture.completedFuture(null);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get an existing resource without incrementing ref count.
     *
     * @param key the resource key
     * @return the resource, or null if not found
     */
    public R getResource(String key) {
        return resources.get(key);
    }

    /**
     * Get all active resources.
     *
     * @return a map of all resources
     */
    public Map<String, R> getAllResources() {
        return new HashMap<>(resources);
    }

    /**
     * Close all resources and clear the registry.
     *
     * @return a CompletableFuture that completes when all resources are closed
     */
    public CompletableFuture<Void> closeAll() {
        lock.lock();
        try {
            CompletableFuture<Void>[] futures = resources.values().stream()
                .map(RefCountedResource::close)
                .toArray(CompletableFuture[]::new);

            resources.clear();
            return CompletableFuture.allOf(futures);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get the number of active resources.
     *
     * @return the count of active resources
     */
    public int getResourceCount() {
        return resources.size();
    }
}