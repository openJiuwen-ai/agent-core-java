/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.kv;

import com.openjiuwen.core.foundation.store.BasedKVStorePipeline;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code RedisStore} behavior in
 * {@code openjiuwen/extensions/store/kv/redis_store.py}.
 */
class RedisStoreTest {

    @Test
    void basicCrudAndPrefixOperationsWork() {
        RedisStore store = new RedisStore(new FakeRedisClient());

        byte[] bytes = new byte[]{1, 2, 3};
        store.set("user:1", "alice").join();
        store.set("user:2", bytes).join();
        store.set("other:1", "ignored").join();

        assertEquals("alice", store.get("user:1").join());
        assertArrayEquals(bytes, (byte[]) store.get("user:2").join());
        assertTrue(store.exists("user:1").join());

        Map<String, Object> byPrefix = store.getByPrefix("user:").join();
        assertEquals(2, byPrefix.size());
        assertEquals("alice", byPrefix.get("user:1"));
        assertArrayEquals(bytes, (byte[]) byPrefix.get("user:2"));

        store.deleteByPrefix("user:", 1).join();
        assertFalse(store.exists("user:1").join());
        assertFalse(store.exists("user:2").join());
        assertTrue(store.exists("other:1").join());
    }

    @Test
    void exclusiveSetMgetAndBatchDeleteFollowRedisSemantics() throws InterruptedException {
        RedisStore store = new RedisStore(new FakeRedisClient());

        assertTrue(store.exclusiveSet("lock", "first", 1).join());
        assertFalse(store.exclusiveSet("lock", "second", 1).join());

        Thread.sleep(1200L);

        assertTrue(store.exclusiveSet("lock", "second", 1).join());
        store.set("k1", "v1").join();
        store.set("k2", "v2").join();

        assertEquals(Arrays.asList("v1", null, "second"), store.mget(List.of("k1", "missing", "lock")).join());
        assertEquals(2, store.batchDelete(List.of("k1", "missing", "k2"), 1).join());
        assertNull(store.get("k1").join());
        assertNull(store.get("k2").join());
    }

    @Test
    void pipelineAndRefreshTtlUseTheStoreContract() throws InterruptedException {
        RedisStore store = new RedisStore(new FakeRedisClient());

        BasedKVStorePipeline pipeline = store.pipeline();
        pipeline.set("pipe:ttl", "value", 1).join();
        pipeline.set("pipe:stable", "stable", null).join();
        pipeline.get("pipe:ttl").join();
        pipeline.exists("pipe:stable").join();

        List<Object> results = pipeline.execute().join();
        assertEquals(4, results.size());
        assertEquals("value", results.get(2));
        assertEquals(Boolean.TRUE, results.get(3));

        Thread.sleep(600L);
        store.refreshTtl(List.of("pipe:ttl"), 2);
        Thread.sleep(700L);
        assertTrue(store.exists("pipe:ttl").join());

        Thread.sleep(1600L);
        assertFalse(store.exists("pipe:ttl").join());
    }

    @Test
    void clusterDetectionAndRefreshFailuresAreHandled() {
        assertTrue(new RedisStore(new FakeRedisClusterClient()).isCluster());
        assertFalse(new RedisStore(new FakeRedisClient()).isCluster());

        RedisStore failingStore = new RedisStore(new ExplodingRedisClient());
        failingStore.set("volatile", "value").join();
        assertDoesNotThrow(() -> failingStore.refreshTtl(List.of("volatile"), 5));
    }

    @Test
    void closeDoesNotReleaseExternalRedisClientByDefault() {
        CloseableRedisClient redisClient = new CloseableRedisClient();
        RedisStore store = new RedisStore(redisClient);

        store.close();

        assertEquals(0, redisClient.closeCount());
    }

    @Test
    void closeReleasesOwnedRedisClient() {
        CloseableRedisClient redisClient = new CloseableRedisClient();
        RedisStore store = new RedisStore(redisClient, true);

        store.close();

        assertEquals(1, redisClient.closeCount());
    }

    static class FakeRedisClient {
        private final Map<String, Object> values = new ConcurrentHashMap<>();
        private final Map<String, Long> expiryAt = new ConcurrentHashMap<>();

        public void set(String key, Object value) {
            cleanup(key);
            values.put(key, value);
            expiryAt.remove(key);
        }

        public boolean set(String key, Object value, boolean nx, Integer expiry) {
            cleanup(key);
            if (nx && values.containsKey(key)) {
                return false;
            }
            values.put(key, value);
            if (expiry != null && expiry > 0) {
                expiryAt.put(key, System.currentTimeMillis() + expiry * 1000L);
            } else {
                expiryAt.remove(key);
            }
            return true;
        }

        public Object get(String key) {
            cleanup(key);
            return values.get(key);
        }

        public long exists(String key) {
            cleanup(key);
            return values.containsKey(key) ? 1L : 0L;
        }

        public long delete(String... keys) {
            long deleted = 0L;
            for (String key : keys) {
                cleanup(key);
                if (values.remove(key) != null) {
                    expiryAt.remove(key);
                    deleted++;
                }
            }
            return deleted;
        }

        public List<Object> mget(String... keys) {
            List<Object> valueList = new ArrayList<>(keys.length);
            for (String key : keys) {
                valueList.add(get(key));
            }
            return valueList;
        }

        public List<String> scanIter(String pattern) {
            String prefix = pattern.endsWith("*") ? pattern.substring(0, pattern.length() - 1) : pattern;
            List<String> keys = new ArrayList<>();
            for (String key : new ArrayList<>(values.keySet())) {
                cleanup(key);
                if (this.values.containsKey(key) && key.startsWith(prefix)) {
                    keys.add(key);
                }
            }
            keys.sort(String::compareTo);
            return keys;
        }

        public boolean expire(String key, int ttlSeconds) {
            cleanup(key);
            if (!values.containsKey(key)) {
                return false;
            }
            expiryAt.put(key, System.currentTimeMillis() + ttlSeconds * 1000L);
            return true;
        }

        public FakeRedisPipeline pipeline() {
            return new FakeRedisPipeline(this);
        }

        private void cleanup(String key) {
            Long expiresAt = expiryAt.get(key);
            if (expiresAt != null && expiresAt <= System.currentTimeMillis()) {
                values.remove(key);
                expiryAt.remove(key);
            }
        }
    }

    static final class FakeRedisClusterClient extends FakeRedisClient {
    }

    static final class CloseableRedisClient extends FakeRedisClient implements AutoCloseable {
        private final AtomicInteger closeCount = new AtomicInteger();

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }

        int closeCount() {
            return closeCount.get();
        }
    }

    static class ExplodingRedisClient extends FakeRedisClient {
        @Override
        public FakeRedisPipeline pipeline() {
            return new ExplodingPipeline(this);
        }
    }

    static class FakeRedisPipeline {
        protected final FakeRedisClient client;
        private final List<Runnable> operations = new ArrayList<>();

        FakeRedisPipeline(FakeRedisClient client) {
            this.client = client;
        }

        public FakeRedisPipeline expire(String key, int ttlSeconds) {
            operations.add(() -> client.expire(key, ttlSeconds));
            return this;
        }

        public List<Object> execute() {
            operations.forEach(Runnable::run);
            operations.clear();
            return List.of();
        }
    }

    static final class ExplodingPipeline extends FakeRedisPipeline {
        ExplodingPipeline(FakeRedisClient client) {
            super(client);
        }

        @Override
        public FakeRedisPipeline expire(String key, int ttlSeconds) {
            throw new IllegalStateException("boom");
        }
    }
}
