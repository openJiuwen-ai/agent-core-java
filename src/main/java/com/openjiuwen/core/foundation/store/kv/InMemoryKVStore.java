/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.kv;

import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.foundation.store.BasedKVStorePipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Mirrors Python's {@code InMemoryKVStore} in
 * {@code openjiuwen/core/foundation/store/kv/in_memory_kv_store.py}.
 */
public class InMemoryKVStore extends BaseKVStore {

    private final Map<String, ValueEntry> store = new LinkedHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public CompletableFuture<Void> set(String key, Object value) {
        lock.lock();
        try {
            store.put(key, new ValueEntry(value, null));
            return CompletableFuture.completedFuture(null);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public CompletableFuture<Boolean> exclusiveSet(String key, Object value, Integer expiry) {
        lock.lock();
        try {
            double currentTime = currentTimeSeconds();
            ValueEntry existing = store.get(key);
            if (existing != null) {
                Long expiryTimestamp = existing.expiryTimestamp();
                if (expiryTimestamp == null || currentTime <= expiryTimestamp) {
                    return CompletableFuture.completedFuture(false);
                }
            }

            Long expiryTimestamp = expiry == null ? null : (long) (currentTime + expiry);
            store.put(key, new ValueEntry(value, expiryTimestamp));
            return CompletableFuture.completedFuture(true);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public CompletableFuture<Object> get(String key) {
        lock.lock();
        try {
            return CompletableFuture.completedFuture(getWithoutLock(key));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public CompletableFuture<Boolean> exists(String key) {
        lock.lock();
        try {
            return CompletableFuture.completedFuture(getWithoutLock(key) != null);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public CompletableFuture<Void> delete(String key) {
        lock.lock();
        try {
            store.remove(key);
            return CompletableFuture.completedFuture(null);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public CompletableFuture<Map<String, Object>> getByPrefix(String prefix) {
        lock.lock();
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : store.keySet()) {
                if (key.startsWith(prefix)) {
                    result.put(key, getWithoutLock(key));
                }
            }
            return CompletableFuture.completedFuture(result);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public CompletableFuture<Void> deleteByPrefix(String prefix, Integer batchSize) {
        lock.lock();
        try {
            List<String> toDelete = new ArrayList<>();
            for (String key : store.keySet()) {
                if (key.startsWith(prefix)) {
                    toDelete.add(key);
                }
            }

            if (batchSize == null || batchSize <= 0) {
                for (String key : toDelete) {
                    store.remove(key);
                }
            } else {
                for (int index = 0; index < toDelete.size(); index += batchSize) {
                    List<String> batch = toDelete.subList(index, Math.min(index + batchSize, toDelete.size()));
                    for (String key : batch) {
                        store.remove(key);
                    }
                }
            }
            return CompletableFuture.completedFuture(null);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public CompletableFuture<List<Object>> mget(List<String> keys) {
        lock.lock();
        try {
            List<Object> result = new ArrayList<>(keys.size());
            for (String key : keys) {
                result.add(getWithoutLock(key));
            }
            return CompletableFuture.completedFuture(result);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public CompletableFuture<Integer> batchDelete(List<String> keys, Integer batchSize) {
        if (keys.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }

        lock.lock();
        try {
            int deleted = 0;
            if (batchSize == null || batchSize <= 0) {
                for (String key : keys) {
                    if (store.remove(key) != null) {
                        deleted++;
                    }
                }
            } else {
                for (int index = 0; index < keys.size(); index += batchSize) {
                    List<String> batch = keys.subList(index, Math.min(index + batchSize, keys.size()));
                    for (String key : batch) {
                        if (store.remove(key) != null) {
                            deleted++;
                        }
                    }
                }
            }
            return CompletableFuture.completedFuture(deleted);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public BasedKVStorePipeline pipeline() {
        return new BasedKVStorePipeline(operations -> {
            lock.lock();
            try {
                List<Object> results = new ArrayList<>(operations.size());
                for (BasedKVStorePipeline.PipelineOperation operation : operations) {
                    switch (operation.kind()) {
                        case "set" -> {
                            store.put(operation.key(), new ValueEntry(operation.value(), null));
                            results.add(null);
                        }
                        case "get" -> results.add(getWithoutLock(operation.key()));
                        case "exists" -> results.add(getWithoutLock(operation.key()) != null);
                        default -> throw new IllegalArgumentException("Unsupported pipeline op: " + operation.kind());
                    }
                }
                return CompletableFuture.completedFuture(results);
            } finally {
                lock.unlock();
            }
        });
    }

    private Object getWithoutLock(String key) {
        ValueEntry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        Long expiryTimestamp = entry.expiryTimestamp();
        if (expiryTimestamp != null && currentTimeSeconds() > expiryTimestamp) {
            return null;
        }
        return entry.value();
    }

    private double currentTimeSeconds() {
        return System.currentTimeMillis() / 1000.0;
    }

    private record ValueEntry(Object value, Long expiryTimestamp) {
    }
}
