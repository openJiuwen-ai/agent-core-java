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
 * 
 * @since 0.1.7
 */
public abstract class RefCountedResource implements AutoCloseable {
    private final AtomicInteger refCount = new AtomicInteger(1);

    /**
     * System.currentTimeMillis.
     * 
     * @since 0.1.7
     */
    private final long createdAtMillis = System.currentTimeMillis();
    private volatile long lastUsedMillis = createdAtMillis;
    private volatile boolean isClosed;

    /**
     * getRefCount.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getRefCount() {
        return refCount.get();
    }

    /**
     * getLastUsedMillis.
     * 
     * @return the result
     * @since 0.1.7
     */
    public long getLastUsedMillis() {
        return lastUsedMillis;
    }

    /**
     * getCreatedAtMillis.
     * 
     * @return the result
     * @since 0.1.7
     */
    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    /**
     * isClosed.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isClosed() {
        return isClosed;
    }

    /**
     * ageMillis.
     * 
     * @return the result
     * @since 0.1.7
     */
    public long ageMillis() {
        return isClosed ? 0L : Math.max(0L, System.currentTimeMillis() - createdAtMillis);
    }

    /**
     * incrementRef.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int incrementRef() {
        if (isClosed) {
            throw new IllegalStateException("Cannot increment ref on isClosed resource");
        }
        lastUsedMillis = System.currentTimeMillis();
        return refCount.incrementAndGet();
    }

    /**
     * decrementRef.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean decrementRef() {
        if (isClosed) {
            return false;
        }
        lastUsedMillis = System.currentTimeMillis();
        return refCount.decrementAndGet() <= 0;
    }

    /**
     * close.
     * 
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
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
     * doClose.
     * 
     * @throws Exception Exception
     * @since 0.1.7
     */
    protected abstract void doClose() throws Exception;

    /**
     * getStats.
     * 
     * @return the result
     * @since 0.1.7
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
