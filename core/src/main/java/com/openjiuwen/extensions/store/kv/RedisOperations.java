/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.extensions.store.kv;

import com.openjiuwen.spi.store.ExpirableKVStore;
import com.openjiuwen.spi.store.KVStorePipeline;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Internal command contract; does not expose a client library to KV consumers.
 */
interface RedisOperations extends ExpirableKVStore {
    void set(String key, Object value);

    boolean exclusiveSet(String key, Object value, Integer expiry);

    Object get(String key);

    boolean isExists(String key);

    void delete(String key);

    Map<String, Object> getByPrefix(String prefix);

    void deleteByPrefix(String prefix, Integer batchSize);

    List<Object> mget(List<String> keys);

    int batchDelete(List<String> keys, Integer batchSize);

    KVStorePipeline pipeline();

    void refreshTtl(List<String> keys, int seconds);

    boolean isCluster();

    void close();

    static int ttlSeconds(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("TTL must be positive");
        }
        if (ttl.getSeconds() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("TTL exceeds supported seconds range");
        }
        long seconds = ttl.getSeconds() + (ttl.getNano() == 0 ? 0 : 1);
        if (seconds > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("TTL exceeds supported seconds range");
        }
        return (int) seconds;
    }
}
