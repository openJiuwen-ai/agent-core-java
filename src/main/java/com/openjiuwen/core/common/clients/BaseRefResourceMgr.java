/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Mirrors Python's {@code BaseRefResourceMgr} in
 * {@code openjiuwen/core/common/clients/ref_counted.py}.
 *
 * @param <R> resource type bound to {@link RefCountedResource}
 */
public abstract class BaseRefResourceMgr<R extends RefCountedResource> {

    private final Map<String, R> resources = new LinkedHashMap<>();
    private final Object lock = new Object();

    protected abstract String getResourceKey(Object config);

    protected abstract CompletableFuture<R> createResource(Object config);

    public CompletableFuture<ResourceLease<R>> acquire(Object config) {
        String key = getResourceKey(config);
        synchronized (lock) {
            if (resources.containsKey(key)) {
                R resource = resources.get(key);
                if (!resource.isClosed()) {
                    resource.incrementRef();
                    return CompletableFuture.completedFuture(new ResourceLease<>(resource, false));
                }
                resources.remove(key);
            }
            try {
                R resource = createResource(config).join();
                resources.put(key, resource);
                return CompletableFuture.completedFuture(new ResourceLease<>(resource, true));
            } catch (CompletionException exception) {
                return CompletableFuture.failedFuture(exception.getCause() != null ? exception.getCause() : exception);
            }
        }
    }

    public CompletableFuture<Void> release(Object config) {
        String key = getResourceKey(config);
        synchronized (lock) {
            R resource = resources.get(key);
            if (resource == null || resource.isClosed()) {
                return CompletableFuture.completedFuture(null);
            }
            boolean shouldClose = resource.decrementRef();
            if (shouldClose) {
                if (resources.containsKey(key)) {
                    resources.remove(key);
                }
                return resource.close();
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    public CompletableFuture<Void> close(String key) {
        synchronized (lock) {
            if (!resources.containsKey(key)) {
                return CompletableFuture.completedFuture(null);
            }
            R resource = resources.remove(key);
            return resource.close();
        }
    }

    public CompletableFuture<Void> closeAll() {
        Map<String, R> snapshot;
        synchronized (lock) {
            snapshot = new LinkedHashMap<>(resources);
            resources.clear();
        }
        CompletableFuture<Void>[] futures = snapshot.values().stream()
                .map(RefCountedResource::close)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    public CompletableFuture<Map<String, Object>> getStats() {
        synchronized (lock) {
            Map<String, Object> resourceStats = new LinkedHashMap<>();
            resources.forEach((key, resource) -> resourceStats.put(key, resource.getStats()));
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("total_resources", resources.size());
            stats.put("resources", resourceStats);
            return CompletableFuture.completedFuture(stats);
        }
    }

    public record ResourceLease<R extends RefCountedResource>(R resource, boolean isNew) {
    }
}
