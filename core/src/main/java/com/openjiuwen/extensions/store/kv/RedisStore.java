/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.extensions.store.kv;

import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.ExpirableKVStore;
import com.openjiuwen.spi.store.KVStorePipeline;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Redis KV store; client-specific commands are isolated behind an internal
 * adapter.
 */
public class RedisStore extends BaseKVStore implements ExpirableKVStore {
    private final RedisOperations operations;

    public RedisStore(Object redisClient) {
        Objects.requireNonNull(redisClient, "redisClient must not be null");
        operations = adapt(redisClient);
    }

    private static RedisOperations adapt(Object client) {
        for (Class<?> type = client.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().startsWith("redis.clients.jedis.")) {
                try {
                    return (RedisOperations) Class.forName("com.openjiuwen.extensions.store.kv.JedisRedisOperations")
                            .getDeclaredConstructor(Object.class).newInstance(client);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot initialize Jedis operations", e);
                }
            }
        }
        return new ReflectiveRedisOperations(client);
    }

    @Override
    public void set(String key, Object value) {
        operations.set(key, value);
    }

    @Override
    public void set(String key, Object value, Duration ttl) {
        operations.set(key, value, ttl);
    }

    @Override
    public boolean exclusiveSet(String key, Object value, Integer expiry) {
        return operations.exclusiveSet(key, value, expiry);
    }

    @Override
    public Object get(String key) {
        return operations.get(key);
    }

    @Override
    public boolean isExists(String key) {
        return operations.isExists(key);
    }

    public boolean exists(String key) {
        return isExists(key);
    }

    @Override
    public void delete(String key) {
        operations.delete(key);
    }

    @Override
    public Map<String, Object> getByPrefix(String prefix) {
        return operations.getByPrefix(prefix);
    }

    @Override
    public void deleteByPrefix(String prefix, Integer batchSize) {
        operations.deleteByPrefix(prefix, batchSize);
    }

    @Override
    public List<Object> mget(List<String> keys) {
        return operations.mget(keys);
    }

    @Override
    public int batchDelete(List<String> keys, Integer batchSize) {
        return operations.batchDelete(keys, batchSize);
    }

    @Override
    public KVStorePipeline pipeline() {
        return operations.pipeline();
    }

    @Override
    public void refreshTtl(List<String> keys, Duration ttl) {
        operations.refreshTtl(keys, ttl);
    }

    public void refreshTtl(List<String> keys, int seconds) {
        operations.refreshTtl(keys, seconds);
    }

    public boolean isCluster() {
        return operations.isCluster();
    }

    @Override
    public void close() {
        operations.close();
    }
}
