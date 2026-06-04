/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * Reference-counted resource management.
 * <p>
 * Provides base class for resources that need reference counting
 * and a manager for acquiring/releasing such resources.
 * <p>
 * Mirrors Python's {@code ref_counted} module in
 * {@code openjiuwen.core.common.clients.ref_counted}.
 */
public class RefCounted {

    private static final Logger logger = Logger.getLogger(RefCounted.class.getName());

    /**
     * Abstract base class for reference-counted resources.
     */
    public abstract static class RefCountedResource {
        private int refCount;
        private boolean closed;
        private final long createdAt;
        private long lastUsed;

        public RefCountedResource() {
            this.refCount = 1;
            this.closed = false;
            this.createdAt = System.currentTimeMillis();
            this.lastUsed = createdAt;
        }

        public int getRefCount() { return refCount; }
        public long getLastUsed() { return lastUsed; }
        public boolean isClosed() { return closed; }
        public long getCreatedAt() { return createdAt; }

        public double getAge() {
            if (closed) return 0;
            return (System.currentTimeMillis() - createdAt) / 1000.0;
        }

        public int incrementRef() {
            if (closed) {
                throw new RuntimeException("Cannot increment ref on closed resource");
            }
            refCount++;
            lastUsed = System.currentTimeMillis();
            return refCount;
        }

        public boolean decrementRef() {
            if (closed) return false;
            refCount--;
            return refCount <= 0;
        }

        /**
         * Implement actual close logic.
         */
        protected abstract void doClose(Map<String, Object> kwargs);

        /**
         * Close the resource.
         */
        public void close(Map<String, Object> kwargs) {
            if (closed) return;
            try {
                decrementRef();
                doClose(kwargs);
            } finally {
                closed = true;
            }
        }

        public Map<String, Object> getStats() {
            Map<String, Object> stats = new HashMap<>();
            stats.put("ref_count", refCount);
            stats.put("closed", closed);
            stats.put("created_at", createdAt);
            stats.put("last_used", lastUsed);
            stats.put("age", getAge());
            return stats;
        }
    }

    /**
     * Base resource manager with reference counting.
     */
    public abstract static class BaseRefResourceMgr<T extends RefCountedResource> {
        private final Map<String, T> resources;
        private final ReentrantLock lock;

        public BaseRefResourceMgr() {
            this.resources = new HashMap<>();
            this.lock = new ReentrantLock();
        }

        /**
         * Get resource key from config.
         */
        protected abstract String getResourceKey(Object config);

        /**
         * Create a new resource from config.
         */
        protected abstract CompletableFuture<T> createResource(Object config);

        /**
         * Acquire a resource.
         * <p>
         * Returns: (resource, isNew)
         */
        public CompletableFuture<Pair<T, Boolean>> acquire(Object config) {
            String key = getResourceKey(config);

            return CompletableFuture.supplyAsync(() -> {
                lock.lock();
                try {
                    if (resources.containsKey(key)) {
                        T resource = resources.get(key);
                        if (!resource.isClosed()) {
                            resource.incrementRef();
                            return new Pair<>(resource, false);
                        } else {
                            resources.remove(key);
                        }
                    }

                    // Create new resource
                    T resource = createResource(config).join();
                    resources.put(key, resource);
                    return new Pair<>(resource, true);
                } finally {
                    lock.unlock();
                }
            });
        }

        /**
         * Release a resource.
         */
        public CompletableFuture<Void> release(Object config) {
            String key = getResourceKey(config);

            return CompletableFuture.runAsync(() -> {
                lock.lock();
                try {
                    T resource = resources.get(key);
                    if (resource == null || resource.isClosed()) return;

                    boolean shouldClose = resource.decrementRef();
                    if (shouldClose) {
                        resources.remove(key);
                        resource.close(new HashMap<>());
                    }
                } finally {
                    lock.unlock();
                }
            });
        }

        /**
         * Close a specific resource by key.
         */
        public CompletableFuture<Void> close(String key) {
            return CompletableFuture.runAsync(() -> {
                lock.lock();
                try {
                    T resource = resources.remove(key);
                    if (resource != null) {
                        resource.close(new HashMap<>());
                    }
                } finally {
                    lock.unlock();
                }
            });
        }

        /**
         * Close all resources.
         */
        public CompletableFuture<Void> closeAll() {
            return CompletableFuture.runAsync(() -> {
                lock.lock();
                try {
                    for (Map.Entry<String, T> entry : resources.entrySet()) {
                        entry.getValue().close(new HashMap<>());
                    }
                    resources.clear();
                } finally {
                    lock.unlock();
                }
            });
        }

        /**
         * Get statistics for all resources.
         */
        public CompletableFuture<Map<String, Object>> getStats() {
            return CompletableFuture.supplyAsync(() -> {
                lock.lock();
                try {
                    Map<String, Object> resourcesStats = new HashMap<>();
                    for (Map.Entry<String, T> entry : resources.entrySet()) {
                        resourcesStats.put(entry.getKey(), entry.getValue().getStats());
                    }

                    Map<String, Object> stats = new HashMap<>();
                    stats.put("total_resources", resources.size());
                    stats.put("resources", resourcesStats);
                    return stats;
                } finally {
                    lock.unlock();
                }
            });
        }
    }

    /**
     * Simple pair class.
     */
    public static class Pair<T, U> {
        public final T first;
        public final U second;
        public Pair(T first, U second) {
            this.first = first;
            this.second = second;
        }
    }
}