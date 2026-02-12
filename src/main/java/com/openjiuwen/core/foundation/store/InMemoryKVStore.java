// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.foundation.store;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link BaseKVStore}.
 * <p>
 * Stores key-value pairs in a concurrent {@link HashMap} with optional TTL support.
 * All operations are synchronized using a {@link ReentrantLock}.
 * </p>
 * 
 * <p>Converted from Python: agent-core/openjiuwen/core/foundation/store/in_memory_kv_store.py</p>
 */
public class InMemoryKVStore extends BaseKVStore {
    
    /**
     * Internal storage: key → (value, expiryTimestamp or null)
     */
    private final Map<String, ValueWithExpiry> store = new HashMap<>();
    
    /**
     * Lock for thread-safe access to the store
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Store or overwrite a key-value pair without expiry.
     */
    @Override
    public CompletableFuture<Void> set(String key, String value) {
        return CompletableFuture.runAsync(() -> {
            lock.lock();
            try {
                store.put(key, new ValueWithExpiry(value, null));
            } finally {
                lock.unlock();
            }
        });
    }

    /**
     * Atomically set a key-value pair only if the key does not already exist or is expired.
     */
    @Override
    public CompletableFuture<Boolean> exclusiveSet(String key, String value, Integer expiry) {
        return CompletableFuture.supplyAsync(() -> {
            lock.lock();
            try {
                long currentTime = System.currentTimeMillis();
                
                if (store.containsKey(key)) {
                    ValueWithExpiry existing = store.get(key);
                    // If expired, allow to overwrite
                    if (existing.expiryTimestamp != null && currentTime > existing.expiryTimestamp) {
                        // Expired: allow to overwrite
                    } else {
                        // Not expired: reject
                        return false;
                    }
                }
                
                // Either not present or expired → set it
                Long expiryTs = expiry != null ? currentTime + (expiry * 1000L) : null;
                store.put(key, new ValueWithExpiry(value, expiryTs));
                return true;
            } finally {
                lock.unlock();
            }
        });
    }

    /**
     * Get value by key. Returns null if key doesn't exist or is expired.
     */
    @Override
    public CompletableFuture<String> get(String key) {
        return CompletableFuture.supplyAsync(() -> {
            lock.lock();
            try {
                return getWithoutLock(key);
            } finally {
                lock.unlock();
            }
        });
    }

    /**
     * Check if key exists and is not expired.
     */
    @Override
    public CompletableFuture<Boolean> exists(String key) {
        return get(key).thenApply(Objects::nonNull);
    }

    /**
     * Delete a key.
     */
    @Override
    public CompletableFuture<Void> delete(String key) {
        return CompletableFuture.runAsync(() -> {
            lock.lock();
            try {
                store.remove(key);
            } finally {
                lock.unlock();
            }
        });
    }

    /**
     * Get all key-value pairs with keys starting with the given prefix.
     */
    @Override
    public CompletableFuture<Map<String, String>> getByPrefix(String prefix) {
        return CompletableFuture.supplyAsync(() -> {
            lock.lock();
            try {
                Map<String, String> result = new HashMap<>();
                for (String key : store.keySet()) {
                    if (key.startsWith(prefix)) {
                        String value = getWithoutLock(key);
                        if (value != null) {
                            result.put(key, value);
                        }
                    }
                }
                return result;
            } finally {
                lock.unlock();
            }
        });
    }

    /**
     * Delete all key-value pairs with keys starting with the given prefix.
     */
    @Override
    public CompletableFuture<Void> deleteByPrefix(String prefix) {
        return CompletableFuture.runAsync(() -> {
            lock.lock();
            try {
                List<String> toDelete = store.keySet().stream()
                    .filter(k -> k.startsWith(prefix))
                    .collect(Collectors.toList());
                toDelete.forEach(store::remove);
            } finally {
                lock.unlock();
            }
        });
    }

    /**
     * Bulk-retrieve values for multiple keys.
     */
    @Override
    public CompletableFuture<List<String>> mget(List<String> keys) {
        return CompletableFuture.supplyAsync(() -> {
            lock.lock();
            try {
                return keys.stream()
                    .map(this::getWithoutLock)
                    .collect(Collectors.toList());
            } finally {
                lock.unlock();
            }
        });
    }

    /**
     * Internal helper to get value without acquiring lock (caller must hold lock).
     */
    private String getWithoutLock(String key) {
        if (!store.containsKey(key)) {
            return null;
        }
        
        ValueWithExpiry valueWithExpiry = store.get(key);
        if (valueWithExpiry.expiryTimestamp != null && 
            System.currentTimeMillis() > valueWithExpiry.expiryTimestamp) {
            // Note: we do NOT auto-delete expired keys to allow re-set later
            return null;
        }
        
        return valueWithExpiry.value;
    }

    /**
     * Internal record to store value with optional expiry timestamp.
     */
    private static class ValueWithExpiry {
        final String value;
        final Long expiryTimestamp; // milliseconds since epoch, or null for no expiry

        ValueWithExpiry(String value, Long expiryTimestamp) {
            this.value = value;
            this.expiryTimestamp = expiryTimestamp;
        }
    }
}

