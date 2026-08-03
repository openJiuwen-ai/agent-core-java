/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.common;

import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.foundation.store.BasedKVStorePipeline;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused regression tests for the distributed memory lock.
 *
 * <p>Mirrors Python's lock surface in
 * {@code openjiuwen/core/memory/common/distributed_lock.py}.</p>
 */
class DistributedLockTest {

    @Test
    void acquireStoresGeneratedLockValue() {
        FakeKvStore store = new FakeKvStore();
        DistributedLock lock = new DistributedLock(store, "memory");

        assertTrue(lock.acquire().join());
        assertNotNull(lock.getLockValue());
        assertEquals(lock.getLockValue(), store.values.get("_lock/memory"));
    }

    @Test
    void acquireRetriesUntilExclusiveSetSucceeds() {
        FakeKvStore store = new FakeKvStore();
        store.failExclusiveSetAttempts = 2;
        DistributedLock lock = new DistributedLock(store, "retry", 10, 1);

        assertTrue(lock.acquire().join());
        assertEquals(3, store.exclusiveSetAttempts.get());
        assertEquals(lock.getLockValue(), store.values.get("_lock/retry"));
    }

    @Test
    void releaseDeletesWhenLockValueMatches() {
        FakeKvStore store = new FakeKvStore();
        DistributedLock lock = new DistributedLock(store, "release");
        lock.acquire().join();

        lock.release().join();

        assertFalse(store.values.containsKey("_lock/release"));
    }

    @Test
    void releaseKeepsForeignLockValue() {
        FakeKvStore store = new FakeKvStore();
        DistributedLock lock = new DistributedLock(store, "foreign");
        lock.acquire().join();
        store.values.put("_lock/foreign", "other-owner");

        lock.release().join();

        assertEquals("other-owner", store.values.get("_lock/foreign"));
    }

    @Test
    void enterAndExitMirrorAsyncContextManager() {
        FakeKvStore store = new FakeKvStore();
        DistributedLock lock = new DistributedLock(store, "ctx");

        DistributedLock entered = lock.enter().join();
        entered.exit().join();

        assertSame(lock, entered);
        assertNull(store.values.get("_lock/ctx"));
    }

    @Test
    void releaseSwallowsStoreErrors() {
        FakeKvStore store = new FakeKvStore();
        DistributedLock lock = new DistributedLock(store, "broken");
        lock.acquire().join();
        store.throwOnGet = true;

        lock.release().join();

        assertTrue(store.values.containsKey("_lock/broken"));
    }

    private static final class FakeKvStore extends BaseKVStore {

        private final Map<String, Object> values = new LinkedHashMap<>();

        private final AtomicInteger exclusiveSetAttempts = new AtomicInteger();

        private int failExclusiveSetAttempts;

        private boolean throwOnGet;

        @Override
        public CompletableFuture<Void> set(String key, Object value) {
            values.put(key, value);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> exclusiveSet(String key, Object value, Integer expiry) {
            int attempt = exclusiveSetAttempts.incrementAndGet();
            if (attempt <= failExclusiveSetAttempts) {
                return CompletableFuture.completedFuture(Boolean.FALSE);
            }
            values.put(key, value);
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Object> get(String key) {
            if (throwOnGet) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.completeExceptionally(new RuntimeException("get failed"));
                return future;
            }
            return CompletableFuture.completedFuture(values.get(key));
        }

        @Override
        public CompletableFuture<Boolean> exists(String key) {
            return CompletableFuture.completedFuture(values.containsKey(key));
        }

        @Override
        public CompletableFuture<Void> delete(String key) {
            values.remove(key);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> getByPrefix(String prefix) {
            return CompletableFuture.completedFuture(Map.of());
        }

        @Override
        public CompletableFuture<Void> deleteByPrefix(String prefix, Integer batchSize) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<Object>> mget(List<String> keys) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Integer> batchDelete(List<String> keys, Integer batchSize) {
            return CompletableFuture.completedFuture(0);
        }

        @Override
        public BasedKVStorePipeline pipeline() {
            return null;
        }
    }
}
