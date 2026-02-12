/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.store.BaseKVStore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Async multiprocess safe distributed lock.
 * <p>
 * Provides a distributed lock implementation using a key-value store.
 * Uses exclusive set operation with TTL for lock acquisition.
 * <p>
 * Corresponds to Python: common/distributed_lock.py
 */
public class DistributedLock implements AutoCloseable {

    private static final LoggerProtocol logger = Loggers.MEMORY;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final BaseKVStore store;
    private final String lockKey;
    private final int ttl;
    private long retryDelayMs;
    private String lockValue;

    /**
     * Create a distributed lock.
     *
     * @param store    the key-value store to use
     * @param lockName the name of the lock
     */
    public DistributedLock(BaseKVStore store, String lockName) {
        this.store = store;
        this.lockKey = "_lock/" + lockName;
        this.ttl = 10;
        this.retryDelayMs = 10;
        this.lockValue = null;
    }

    /**
     * Acquire the lock.
     * Will retry until successful.
     *
     * @return CompletableFuture that completes with true when lock is acquired
     */
    public CompletableFuture<Boolean> acquire() {
        this.lockValue = UUID.randomUUID().toString();
        return tryAcquire();
    }

    private CompletableFuture<Boolean> tryAcquire() {
        return store.exclusiveSet(lockKey, lockValue, ttl)
            .thenCompose(success -> {
                if (success) {
                    return CompletableFuture.completedFuture(true);
                }
                // Retry after delay
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                }).thenCompose(v -> tryAcquire());
            });
    }

    /**
     * Release the lock.
     * Only releases if the lock is held by this instance.
     *
     * @return CompletableFuture that completes when release is done
     */
    public CompletableFuture<Void> release() {
        return store.get(lockKey)
            .<Void>thenCompose(existing -> {
                if (existing == null || existing.isEmpty()) {
                    return CompletableFuture.completedFuture(null);
                }
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = objectMapper.readValue(existing, Map.class);
                    String storedValue = (String) data.get("value");
                    if (lockValue != null && lockValue.equals(storedValue)) {
                        return store.delete(lockKey);
                    }
                    return CompletableFuture.completedFuture(null);
                } catch (JsonProcessingException e) {
                    logger.error("Error parsing lock data: {}", e.getMessage());
                    return CompletableFuture.completedFuture(null);
                }
            })
            .exceptionally(e -> {
                logger.error("Error releasing lock: {}", e.getMessage());
                return null;
            });
    }

    /**
     * Close the lock (release it).
     * Implements AutoCloseable for try-with-resources support.
     */
    @Override
    public void close() {
        release().join();
    }

    /**
     * Get the lock key.
     *
     * @return the lock key
     */
    public String getLockKey() {
        return lockKey;
    }

    /**
     * Get the lock value (UUID).
     *
     * @return the lock value or null if not acquired
     */
    public String getLockValue() {
        return lockValue;
    }

    /**
     * Set the retry delay in milliseconds.
     * Useful for testing.
     *
     * @param retryDelayMs the retry delay in milliseconds
     */
    public void setRetryDelayMs(long retryDelayMs) {
        this.retryDelayMs = retryDelayMs;
    }
}

