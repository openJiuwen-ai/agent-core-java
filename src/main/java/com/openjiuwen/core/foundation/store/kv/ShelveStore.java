/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.kv;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shelve-based (in-memory file-backed) KV store.
 * <p>
 * Mirrors Python's {@code ShelveStore} class from
 * <code>foundation/store/kv/shelve_store.py</code>.
 *
 * <p>In Java, this uses a ConcurrentHashMap as an in-memory equivalent
 * to Python's shelve module. Data is not persisted to disk.
 */
public class ShelveStore extends BaseKVStore {

    private final Map<String, byte[]> data = new ConcurrentHashMap<>();

    @Override
    public java.util.concurrent.CompletableFuture<Void> set(String key, byte[] value) {
        data.put(key, value);
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    @Override
    public java.util.concurrent.CompletableFuture<Boolean> exclusiveSet(String key, byte[] value, Integer expiry) {
        byte[] existing = data.putIfAbsent(key, value);
        return java.util.concurrent.CompletableFuture.completedFuture(existing == null);
    }

    @Override
    public java.util.concurrent.CompletableFuture<byte[]> get(String key) {
        return java.util.concurrent.CompletableFuture.completedFuture(data.get(key));
    }

    @Override
    public java.util.concurrent.CompletableFuture<Boolean> exists(String key) {
        return java.util.concurrent.CompletableFuture.completedFuture(data.containsKey(key));
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> delete(String key) {
        data.remove(key);
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    @Override
    public java.util.concurrent.CompletableFuture<java.util.Collection<byte[]>> multiGet(java.util.Collection<String> keys) {
        java.util.List<byte[]> results = new java.util.ArrayList<>();
        for (String key : keys) {
            results.add(data.get(key));
        }
        return java.util.concurrent.CompletableFuture.completedFuture(results);
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> multiSet(Map<String, byte[]> pairs) {
        data.putAll(pairs);
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> close() {
        data.clear();
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }
}
