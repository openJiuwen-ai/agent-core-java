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
 * 
 * @since 0.1.7
 */
public class InMemoryKVStore extends BaseKVStore {

    /**
     * Internal entry bundling a value with its optional expiry timestamp so
     * that write-value and clear-set-expiry happen as one atomic
     * {@link ConcurrentHashMap#compute} step, eliminating the TOCTOU window
     * the previous dual-map design exposed under concurrent set/get/cleanup.
     *
     * @since 0.1.7
     */
    private static final class Entry {
        private final Object value;
        private final Long expiryAt;

        Entry(Object value, Long expiryAt) {
            this.value = value;
            this.expiryAt = expiryAt;
        }

        boolean isExpired(long now) {
            return expiryAt != null && expiryAt <= now;
        }
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /**
     * set.
     * 
     * @param key key
     * @param value value
     * @since 0.1.7
     */
    @Override
    public void set(String key, Object value) {
        store.compute(key, (k, existing) -> new Entry(value, null));
    }

    /**
     * exclusiveSet.
     * 
     * @param key key
     * @param value value
     * @param expiry expiry
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean exclusiveSet(String key, Object value, Integer expiry) {
        cleanupIfExpired(key);
        Long expiryMs = expiry != null && expiry > 0
                ? System.currentTimeMillis() + expiry * 1000L
                : null;
        return store.putIfAbsent(key, new Entry(value, expiryMs)) == null;
    }

    /**
     * get.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object get(String key) {
        cleanupIfExpired(key);
        Entry entry = store.get(key);
        return entry != null ? entry.value : null;
    }

    /**
     * isExists.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean isExists(String key) {
        cleanupIfExpired(key);
        return store.containsKey(key);
    }

    /**
     * delete.
     * 
     * @param key key
     * @since 0.1.7
     */
    @Override
    public void delete(String key) {
        store.remove(key);
    }

    /**
     * getByPrefix.
     * 
     * @param prefix prefix
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> getByPrefix(String prefix) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : new ArrayList<>(store.keySet())) {
            cleanupIfExpired(key);
            Entry entry = store.get(key);
            if (key.startsWith(prefix) && entry != null) {
                result.put(key, entry.value);
            }
        }
        return result;
    }

    /**
     * deleteByPrefix.
     * 
     * @param prefix prefix
     * @param batchSize batchSize
     * @since 0.1.7
     */
    @Override
    public void deleteByPrefix(String prefix, Integer batchSize) {
        for (String key : new ArrayList<>(store.keySet())) {
            if (key.startsWith(prefix)) {
                delete(key);
            }
        }
    }

    /**
     * mget.
     * 
     * @param keys keys
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<Object> mget(List<String> keys) {
        List<Object> result = new ArrayList<>();
        for (String key : keys) {
            result.add(get(key));
        }
        return result;
    }

    /**
     * batchDelete.
     * 
     * @param keys keys
     * @param batchSize batchSize
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int batchDelete(List<String> keys, Integer batchSize) {
        int removed = 0;
        for (String key : keys) {
            if (isExists(key)) {
                delete(key);
                removed++;
            }
        }
        return removed;
    }

    /**
     * pipeline.
     * 
     * @return the result
     * @since 0.1.7
     */
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
                    case "isExists" -> results.add(isExists(key));
                    default -> results.add(null);
                }
            }
            return results;
        });
    }

    /**
     * Release in-memory state by clearing all stored keys and expiry markers.
     * <p>
     * After {@code close()}, the store is empty and subsequent reads return
     * {@code null}. Safe to call multiple times.
     *
     * @since 0.1.13
     */
    @Override
    public void close() {
        store.clear();
    }

    /**
     * Atomically remove the entry for {@code key} when its expiry has elapsed.
     *
     * @param key key
     * @since 0.1.7
     */
    private void cleanupIfExpired(String key) {
        long now = System.currentTimeMillis();
        store.computeIfPresent(key, (k, entry) -> entry.isExpired(now) ? null : entry);
    }
}
