/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for reference-counted resources.
 * <p>
 * Mirrors Python's {@code RefCountedResource} abstract class from
 * <code>common/clients/ref_counted.py</code>.
 *
 * <p>Provides reference counting, lifecycle tracking, and async close capabilities
 * for connection pool resources and other managed objects.
 */
public abstract class RefCountedResource {

    private int refCount = 1;
    private boolean closed = false;
    private final Instant createdAt;
    private Instant lastUsed;

    protected RefCountedResource() {
        this.createdAt = Instant.now();
        this.lastUsed = this.createdAt;
    }

    public int getRefCount() {
        return refCount;
    }

    public Instant getLastUsed() {
        return lastUsed;
    }

    public boolean isClosed() {
        return closed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Get the age of this resource in seconds.
     *
     * @return age in seconds, or 0 if closed
     */
    public long getAgeSeconds() {
        if (closed) {
            return 0;
        }
        return Instant.now().getEpochSecond() - createdAt.getEpochSecond();
    }

    /**
     * Increment the reference count.
     *
     * @return the new reference count
     * @throws IllegalStateException if the resource is already closed
     */
    public int incrementRef() {
        if (closed) {
            throw new IllegalStateException("Cannot increment ref on closed resource");
        }
        refCount++;
        lastUsed = Instant.now();
        return refCount;
    }

    /**
     * Decrement the reference count.
     *
     * @return true if the resource should be released (ref count <= 0)
     */
    public boolean decrementRef() {
        if (closed) {
            return false;
        }
        refCount--;
        return refCount <= 0;
    }

    /**
     * Perform the actual close operation. Subclasses must implement this.
     *
     * @return a CompletableFuture that completes when close is done
     */
    protected abstract CompletableFuture<Void> doClose();

    /**
     * Close the resource asynchronously.
     *
     * @return a CompletableFuture that completes when close is done
     */
    public CompletableFuture<Void> close() {
        if (closed) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            decrementRef();
            return doClose();
        } finally {
            closed = true;
        }
    }

    /**
     * Get statistics about this resource.
     *
     * @return a map containing ref_count, closed, created_at, last_used, age
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("ref_count", refCount);
        stats.put("closed", closed);
        stats.put("created_at", createdAt.toString());
        stats.put("last_used", lastUsed.toString());
        stats.put("age_seconds", getAgeSeconds());
        return stats;
    }
}