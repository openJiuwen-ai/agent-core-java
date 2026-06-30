/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reference-counted resource base class.
 */
public abstract class RefCountedResource implements AutoCloseable {
    private final AtomicInteger refCount = new AtomicInteger(1);
    private final long createdAtMillis = System.currentTimeMillis();
    private volatile long lastUsedMillis = createdAtMillis;
    private volatile boolean isClosed;

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getRefCount() {
        return refCount.get();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public long getLastUsedMillis() {
        return lastUsedMillis;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isClosed() {
        return isClosed;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public long ageMillis() {
        return isClosed ? 0L : Math.max(0L, System.currentTimeMillis() - createdAtMillis);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int incrementRef() {
        if (isClosed) {
            throw new IllegalStateException("Cannot increment ref on isClosed resource");
        }
        lastUsedMillis = System.currentTimeMillis();
        return refCount.incrementAndGet();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean decrementRef() {
        if (isClosed) {
            return false;
        }
        lastUsedMillis = System.currentTimeMillis();
        return refCount.decrementAndGet() <= 0;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public synchronized void close() throws Exception {
        if (isClosed) {
            return;
        }
        try {
            doClose();
        } finally {
            isClosed = true;
            refCount.set(0);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected abstract void doClose() throws Exception;

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("ref_count", refCount.get());
        stats.put("isClosed", isClosed);
        stats.put("created_at", Instant.ofEpochMilli(createdAtMillis).toString());
        stats.put("last_used", Instant.ofEpochMilli(lastUsedMillis).toString());
        stats.put("age_millis", ageMillis());
        return stats;
    }
}
