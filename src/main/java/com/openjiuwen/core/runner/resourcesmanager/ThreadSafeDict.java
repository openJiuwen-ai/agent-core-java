// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Thread-safe dictionary implementation.
 * 
 * <p>Provides thread-safe operations for a key-value store using ReentrantLock.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public class ThreadSafeDict<K, V> implements Map<K, V> {
    
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<K, V> data;
    
    /**
     * Constructs an empty ThreadSafeDict.
     */
    public ThreadSafeDict() {
        this.data = new HashMap<>();
    }
    
    /**
     * Constructs a ThreadSafeDict with initial data.
     *
     * @param initialData the initial data to copy
     */
    public ThreadSafeDict(Map<K, V> initialData) {
        this.data = initialData != null ? new HashMap<>(initialData) : new HashMap<>();
    }
    
    @Override
    public V get(Object key) {
        lock.lock();
        try {
            return data.get(key);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Gets the value for the key, or returns the default if not found.
     *
     * @param key the key
     * @param defaultValue the default value
     * @return the value or default
     */
    public V getOrDefault(Object key, V defaultValue) {
        lock.lock();
        try {
            return data.getOrDefault(key, defaultValue);
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public V put(K key, V value) {
        lock.lock();
        try {
            return data.put(key, value);
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public V remove(Object key) {
        lock.lock();
        try {
            return data.remove(key);
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public boolean containsKey(Object key) {
        lock.lock();
        try {
            return data.containsKey(key);
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public boolean containsValue(Object value) {
        lock.lock();
        try {
            return data.containsValue(value);
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public int size() {
        lock.lock();
        try {
            return data.size();
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public boolean isEmpty() {
        lock.lock();
        try {
            return data.isEmpty();
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public void clear() {
        lock.lock();
        try {
            data.clear();
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public Set<K> keySet() {
        lock.lock();
        try {
            return new HashSet<>(data.keySet());
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public Collection<V> values() {
        lock.lock();
        try {
            return new ArrayList<>(data.values());
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public Set<Entry<K, V>> entrySet() {
        lock.lock();
        try {
            return new HashSet<>(data.entrySet());
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        lock.lock();
        try {
            data.putAll(m);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Gets the value if present, otherwise sets and returns the default.
     *
     * @param key the key
     * @param defaultValue the default value to set if absent
     * @return the existing or newly set value
     */
    public V getOrSet(K key, V defaultValue) {
        lock.lock();
        try {
            V value = data.get(key);
            if (value == null) {
                data.put(key, defaultValue);
                return defaultValue;
            }
            return value;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Gets the value if present, otherwise creates, sets, and returns it.
     *
     * @param key the key
     * @param creator the function to create the value
     * @return the existing or newly created value
     */
    public V getOrCreate(K key, Function<K, V> creator) {
        lock.lock();
        try {
            if (!data.containsKey(key)) {
                data.put(key, creator.apply(key));
            }
            return data.get(key);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Pops (removes and returns) the value for the key.
     *
     * @param key the key
     * @param defaultValue the default value if key not found
     * @return the removed value or default
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

