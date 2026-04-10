/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.memory.support;

import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.KVStorePipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TestInMemoryKVStore extends BaseKVStore {

    private final ConcurrentHashMap<String, Object> data = new ConcurrentHashMap<>();

    @Override
    public void set(String key, Object value) {
        data.put(key, value);
    }

    @Override
    public boolean exclusiveSet(String key, Object value, Integer expiry) {
        return data.putIfAbsent(key, value) == null;
    }

    @Override
    public Object get(String key) {
        return data.get(key);
    }

    @Override
    public boolean exists(String key) {
        return data.containsKey(key);
    }

    @Override
    public void delete(String key) {
        data.remove(key);
    }

    @Override
    public Map<String, Object> getByPrefix(String prefix) {
        Map<String, Object> result = new LinkedHashMap<>();
        data.forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                result.put(key, value);
            }
        });
        return result;
    }

    @Override
    public void deleteByPrefix(String prefix, Integer batchSize) {
        new ArrayList<>(data.keySet()).stream()
                .filter(key -> key.startsWith(prefix))
                .forEach(data::remove);
    }

    @Override
    public List<Object> mget(List<String> keys) {
        List<Object> result = new ArrayList<>(keys.size());
        for (String key : keys) {
            result.add(data.get(key));
        }
        return result;
    }

    @Override
    public int batchDelete(List<String> keys, Integer batchSize) {
        int deleted = 0;
        for (String key : keys) {
            if (data.remove(key) != null) {
                deleted++;
            }
        }
        return deleted;
    }

    @Override
    public KVStorePipeline pipeline() {
        return new KVStorePipeline(operations -> {
            List<Object> results = new ArrayList<>(operations.size());
            for (Object[] operation : operations) {
                String op = String.valueOf(operation[0]);
                String key = String.valueOf(operation[1]);
                switch (op) {
                    case "set" -> {
                        set(key, operation[2]);
                        results.add(null);
                    }
                    case "get" -> results.add(get(key));
                    case "exists" -> results.add(exists(key));
                    default -> throw new IllegalArgumentException("Unsupported pipeline op: " + op);
                }
            }
            return results;
        });
    }
}
