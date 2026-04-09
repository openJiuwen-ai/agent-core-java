/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.kv;

import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.KVStorePipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory key-value store with optional expiry support.
 */
public class InMemoryKVStore extends BaseKVStore {

    private final Map<String, Object> values = new ConcurrentHashMap<>();
    private final Map<String, Long> expiryAt = new ConcurrentHashMap<>();

    @Override
    public void set(String key, Object value) {
        values.put(key, value);
        expiryAt.remove(key);
    }

    @Override
    public boolean exclusiveSet(String key, Object value, Integer expiry) {
        cleanupIfExpired(key);
        if (values.containsKey(key)) {
            return false;
        }
        values.put(key, value);
        if (expiry != null && expiry > 0) {
            expiryAt.put(key, System.currentTimeMillis() + expiry * 1000L);
        }
        return true;
    }

    @Override
    public Object get(String key) {
        cleanupIfExpired(key);
        return values.get(key);
    }

    @Override
    public boolean exists(String key) {
        cleanupIfExpired(key);
        return values.containsKey(key);
    }

    @Override
    public void delete(String key) {
        values.remove(key);
        expiryAt.remove(key);
    }

    @Override
    public Map<String, Object> getByPrefix(String prefix) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : new ArrayList<>(values.keySet())) {
            cleanupIfExpired(key);
            if (key.startsWith(prefix) && values.containsKey(key)) {
                result.put(key, values.get(key));
            }
        }
        return result;
    }

    @Override
    public void deleteByPrefix(String prefix, Integer batchSize) {
        for (String key : new ArrayList<>(values.keySet())) {
            if (key.startsWith(prefix)) {
                delete(key);
            }
        }
    }

    @Override
    public List<Object> mget(List<String> keys) {
        List<Object> result = new ArrayList<>();
        for (String key : keys) {
            result.add(get(key));
        }
        return result;
    }

    @Override
    public int batchDelete(List<String> keys, Integer batchSize) {
        int removed = 0;
        for (String key : keys) {
            if (exists(key)) {
                delete(key);
                removed++;
            }
        }
        return removed;
    }

    @Override
    public KVStorePipeline pipeline() {
        return new KVStorePipeline(operations -> {
            List<Object> results = new ArrayList<>(operations.size());
            for (Object[] operation : operations) {
                String action = String.valueOf(operation[0]);
                String key = operation.length > 1 ? String.valueOf(operation[1]) : "";
                switch (action) {
                    case "set" -> {
                        set(key, operation.length > 2 ? operation[2] : null);
                        results.add(true);
                    }
                    case "get" -> results.add(get(key));
                    case "exists" -> results.add(exists(key));
                    default -> results.add(null);
                }
            }
            return results;
        });
    }

    private void cleanupIfExpired(String key) {
        Long expireTime = expiryAt.get(key);
        if (expireTime != null && expireTime <= System.currentTimeMillis()) {
            values.remove(key);
            expiryAt.remove(key);
        }
    }
}
