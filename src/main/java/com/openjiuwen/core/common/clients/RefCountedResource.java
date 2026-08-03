/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Mirrors Python's {@code RefCountedResource} in
 * {@code openjiuwen/core/common/clients/ref_counted.py}.
 */
public abstract class RefCountedResource {

    private int refCount = 1;
    private boolean closed;
    private final double createdAt = epochSeconds();
    private double lastUsed = createdAt;

    public int getRefCount() {
        return refCount;
    }

    public double getLastUsed() {
        return lastUsed;
    }

    public boolean isClosed() {
        return closed;
    }

    public double getCreatedAt() {
        return createdAt;
    }

    public double getAge() {
        return closed ? 0.0d : Math.max(0.0d, epochSeconds() - createdAt);
    }

    public int incrementRef() {
        if (closed) {
            throw new IllegalStateException("Cannot increment ref on closed resource");
        }
        refCount++;
        lastUsed = monotonicSeconds();
        return refCount;
    }

    public boolean decrementRef() {
        if (closed) {
            return false;
        }
        refCount--;
        return refCount <= 0;
    }

    protected abstract CompletableFuture<Void> doClose(Map<String, Object> kwargs);

    public CompletableFuture<Void> close() {
        return close(Map.of());
    }

    public CompletableFuture<Void> close(Map<String, Object> kwargs) {
        if (closed) {
            return CompletableFuture.completedFuture(null);
        }
        decrementRef();
        CompletableFuture<Void> future;
        try {
            future = doClose(kwargs != null ? kwargs : Map.of());
        } catch (Exception exception) {
            closed = true;
            return CompletableFuture.failedFuture(exception);
        }
        if (future == null) {
            future = CompletableFuture.completedFuture(null);
        }
        return future.handle((ignored, throwable) -> {
            closed = true;
            if (throwable != null) {
                throw new CompletionException(throwable);
            }
            return null;
        });
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("ref_count", refCount);
        stats.put("closed", closed);
        stats.put("created_at", createdAt);
        stats.put("last_used", lastUsed);
        stats.put("age", getAge());
        return stats;
    }

    private static double epochSeconds() {
        return System.currentTimeMillis() / 1000.0d;
    }

    private static double monotonicSeconds() {
        return System.nanoTime() / 1_000_000_000.0d;
    }
}
