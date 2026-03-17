/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.runner.resourcemanager;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Generic thread-safe dictionary wrapper.
 * <p>
 * Mirrors Python's {@code ThreadSafeDict} in {@code resources_manager/thread_safe_dict.py}.
 * All operations are protected by a reentrant lock.
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public class ThreadSafeDict<K, V> {

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<K, V> data;

    public ThreadSafeDict() {
        this.data = new HashMap<>();
    }

    public ThreadSafeDict(Map<K, V> initialData) {
        this.data = initialData != null ? new HashMap<>(initialData) : new HashMap<>();
    }

    public V get(K key) {
        lock.lock();
        try {
            return data.get(key);
        } finally {
            lock.unlock();
        }
    }

    public V getOrDefault(K key, V defaultValue) {
        lock.lock();
        try {
            return data.getOrDefault(key, defaultValue);
        } finally {
            lock.unlock();
        }
    }

    public V put(K key, V value) {
        lock.lock();
        try {
            return data.put(key, value);
        } finally {
            lock.unlock();
        }
    }

    public V remove(K key) {
        lock.lock();
        try {
            return data.remove(key);
        } finally {
            lock.unlock();
        }
    }

    public boolean containsKey(K key) {
        lock.lock();
        try {
            return data.containsKey(key);
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return data.size();
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            data.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically get or set: if key is absent, put the default value and return it.
     */
    public V getOrSet(K key, V defaultValue) {
        lock.lock();
        try {
            V val = data.get(key);
            if (val == null) {
                data.putIfAbsent(key, defaultValue);
                return data.get(key);
            }
            return val;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically get or create: if key is absent, invoke creator and store the result.
     */
    public V getOrCreate(K key, Supplier<V> creator) {
        lock.lock();
        try {
            V val = data.get(key);
            if (val == null) {
                val = creator.get();
                data.put(key, val);
            }
            return val;
        } finally {
            lock.unlock();
        }
    }

    public void putAll(Map<K, V> m) {
        lock.lock();
        try {
            data.putAll(m);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a snapshot of the keys.
     */
    public Set<K> keys() {
        lock.lock();
        try {
            return Set.copyOf(data.keySet());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a snapshot of the values.
     */
    public Collection<V> values() {
        lock.lock();
        try {
            return java.util.List.copyOf(data.values());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a snapshot of the entries.
     */
    public Map<K, V> snapshot() {
        lock.lock();
        try {
            return new HashMap<>(data);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a snapshot of all key-value pairs as a Set of Map.Entry (mirrors Python {@code dict.items()}).
     */
    public Set<Map.Entry<K, V>> items() {
        lock.lock();
        try {
            return new HashMap<>(data).entrySet();
        } finally {
            lock.unlock();
        }
    }

    /**
     * If key is absent or mapped to null, sets it to defaultValue and returns it;
     * otherwise returns the current value (mirrors Python {@code dict.setdefault()}).
     */
    public V setdefault(K key, V defaultValue) {
        lock.lock();
        try {
            V val = data.get(key);
            if (val == null && !data.containsKey(key)) {
                data.put(key, defaultValue);
                return defaultValue;
            }
            return val;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes and returns the value for key, or defaultValue if not present
     * (mirrors Python {@code dict.pop()}).
     */
    public V pop(K key, V defaultValue) {
        lock.lock();
        try {
            if (data.containsKey(key)) {
                return data.remove(key);
            }
            return defaultValue;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes and returns the value for key. Throws if key is absent
     * (mirrors Python {@code dict.pop(key)} without default).
     */
    public V pop(K key) {
        lock.lock();
        try {
            if (!data.containsKey(key)) {
                throw new java.util.NoSuchElementException("Key not found: " + key);
            }
            return data.remove(key);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Bulk update from another map (mirrors Python {@code dict.update()}).
     * Alias for {@link #putAll(Map)}.
     */
    public void update(Map<K, V> m) {
        putAll(m);
    }

    @Override
    public String toString() {
        lock.lock();
        try {
            return data.toString();
        } finally {
            lock.unlock();
        }
    }
}
