/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.common;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.multitenant.TenantKVStoreKeyResolver;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * Async multiprocess-safe distributed lock backed by a KV store.
 *
 * <p>Mirrors Python's {@code DistributedLock} in
 * {@code openjiuwen/core/memory/common/distributed_lock.py}.</p>
 */
public class DistributedLock {

    private static final int DEFAULT_TTL_SECONDS = 10;

    private static final long DEFAULT_RETRY_DELAY_MILLIS = 10L;

    private final BaseKVStore store;

    private final String lockKey;

    private final Integer ttl;

    private final long retryDelayMillis;

    private volatile String lockValue;

    public DistributedLock(BaseKVStore store, String lockName) {
        this(store, lockName, DEFAULT_TTL_SECONDS, DEFAULT_RETRY_DELAY_MILLIS);
    }

    DistributedLock(BaseKVStore store, String lockName, Integer ttl, long retryDelayMillis) {
        this.store = store;
        this.lockKey = TenantKVStoreKeyResolver.resolveKey("_lock/" + lockName);
        this.ttl = ttl;
        this.retryDelayMillis = retryDelayMillis;
    }

    public CompletableFuture<Boolean> acquire() {
        String newLockValue = UUID.randomUUID().toString();
        this.lockValue = newLockValue;
        return tryAcquire(newLockValue);
    }

    public CompletableFuture<Void> release() {
        return store.get(lockKey)
                .thenCompose(currentLockValue -> {
                    if (Objects.equals(currentLockValue, lockValue)) {
                        return store.delete(lockKey);
                    }
                    return CompletableFuture.completedFuture(null);
                })
                .exceptionally(exception -> {
                    Throwable cause = unwrap(exception);
                    Loggers.MEMORY.error(
                            "Error releasing lock: {} (event_type={}, exception={})",
                            cause.getMessage(),
                            LogEventType.MEMORY_STORE.getValue(),
                            cause.getClass().getSimpleName()
                    );
                    return null;
                });
    }

    public CompletableFuture<DistributedLock> enter() {
        return acquire().thenApply(ignored -> this);
    }

    public CompletableFuture<Void> exit() {
        return release();
    }

    public CompletableFuture<Void> exit(Throwable excType, Throwable excVal, Throwable excTb) {
        return release();
    }

    String getLockKey() {
        return lockKey;
    }

    String getLockValue() {
        return lockValue;
    }

    private CompletableFuture<Boolean> tryAcquire(String expectedLockValue) {
        return store.exclusiveSet(lockKey, expectedLockValue, ttl)
                .thenCompose(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        return CompletableFuture.completedFuture(Boolean.TRUE);
                    }
                    return CompletableFuture.runAsync(
                                    () -> { },
                                    CompletableFuture.delayedExecutor(retryDelayMillis, TimeUnit.MILLISECONDS)
                            )
                            .thenCompose(ignored -> tryAcquire(expectedLockValue));
                });
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return unwrap(completionException.getCause());
        }
        return throwable;
    }
}
