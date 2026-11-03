/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Mirrors Python's {@code ThreadSafeDict} in
 * {@code openjiuwen/core/runner/resources_manager/thread_safe_dict.py}.
 *
 * @param <K> key type
 * @param <V> value type
 */
public final class ThreadSafeDict<K, V> implements Iterable<K> {

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<K, V> data;

    public ThreadSafeDict() {
        this(null);
    }

    public ThreadSafeDict(Map<K, V> initialData) {
        this.data = initialData != null ? initialData : new LinkedHashMap<>();
    }

    public V get(K key) {
        lock.lock();
        try {
            return data.get(key);
        } finally {
            lock.unlock();
        }
    }

    public V get(K key, V defaultValue) {
        lock.lock();
        try {
            return data.containsKey(key) ? data.get(key) : defaultValue;
        } finally {
            lock.unlock();
        }
    }

    public void put(K key, V value) {
        lock.lock();
        try {
            data.put(key, value);
        } finally {
            lock.unlock();
        }
    }

    public void delete(K key) {
        lock.lock();
        try {
            if (!data.containsKey(key)) {
                throw new NoSuchElementException("Key not found: " + key);
            }
            data.remove(key);
        } finally {
            lock.unlock();
        }
    }

    public boolean containsKey(Object key) {
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

    public V getOrSet(K key, V defaultValue) {
        lock.lock();
        try {
            V value = data.get(key);
            if (value == null) {
                if (!data.containsKey(key)) {
                    data.put(key, defaultValue);
                }
                return data.get(key);
            }
            return value;
        } finally {
            lock.unlock();
        }
    }

    public V setdefault(K key, V defaultValue) {
        lock.lock();
        try {
            if (!data.containsKey(key)) {
                data.put(key, defaultValue);
                return defaultValue;
            }
            return data.get(key);
        } finally {
            lock.unlock();
        }
    }

    public V getOrCreate(K key, Supplier<? extends V> creator) {
        lock.lock();
        try {
            if (!data.containsKey(key)) {
                data.put(key, creator.get());
            }
            return data.get(key);
        } finally {
            lock.unlock();
        }
    }

    public V pop(K key, V defaultValue) {
        lock.lock();
        try {
            return data.containsKey(key) ? data.remove(key) : defaultValue;
        } finally {
            lock.unlock();
        }
    }

    public V pop(K key) {
        lock.lock();
        try {
            if (!data.containsKey(key)) {
                throw new NoSuchElementException("Key not found: " + key);
            }
            return data.remove(key);
        } finally {
            lock.unlock();
        }
    }

    public void update(Map<? extends K, ? extends V> mapping) {
        lock.lock();
        try {
            if (mapping != null) {
                data.putAll(mapping);
            }
        } finally {
            lock.unlock();
        }
    }

    public void update(Iterable<? extends Map.Entry<? extends K, ? extends V>> entries) {
        lock.lock();
        try {
            if (entries == null) {
                return;
            }
            for (Map.Entry<? extends K, ? extends V> entry : entries) {
                data.put(entry.getKey(), entry.getValue());
            }
        } finally {
            lock.unlock();
        }
    }

    public Set<K> keys() {
        lock.lock();
        try {
            return new LinkedHashSet<>(data.keySet());
        } finally {
            lock.unlock();
        }
    }

    public Collection<V> values() {
        lock.lock();
        try {
            return new ArrayList<>(data.values());
        } finally {
            lock.unlock();
        }
    }

    public Set<Map.Entry<K, V>> items() {
        lock.lock();
        try {
            LinkedHashSet<Map.Entry<K, V>> snapshot = new LinkedHashSet<>();
            for (Map.Entry<K, V> entry : data.entrySet()) {
                snapshot.add(new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue()));
            }
            return snapshot;
        } finally {
            lock.unlock();
        }
    }

    public Map<K, V> snapshot() {
        lock.lock();
        try {
            return new LinkedHashMap<>(data);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Iterator<K> iterator() {
        lock.lock();
        try {
            return List.copyOf(data.keySet()).iterator();
        } finally {
            lock.unlock();
        }
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
