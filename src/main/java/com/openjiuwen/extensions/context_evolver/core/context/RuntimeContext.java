/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.context;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.core.context.runtime_context.RuntimeContext}.
 * Context object isPassed between operations in a flow.
 * 
 * @since 0.1.7
 */
public class RuntimeContext {
    private final Map<String, Object> data;

    /**
     * RuntimeContext.
     * 
     * @since 0.1.7
     */
    public RuntimeContext() {
        this.data = new ConcurrentHashMap<>();
    }

    /**
     * Get a value from context.
     * 
     * @param key context key
     * @return value or null
     * @since 0.1.7
     */
    public Object get(String key) {
        return data.get(key);
    }

    /**
     * Get a value from context with default.
     * 
     * @param key context key
     * @param defaultValue default value
     * @return value or default
     * @since 0.1.7
     */
    public Object get(String key, Object defaultValue) {
        return data.getOrDefault(key, defaultValue);
    }

    /**
     * Set a value in context.
     * 
     * @param key context key
     * @param value value to store
     * @since 0.1.7
     */
    public void set(String key, Object value) {
        data.put(key, value);
    }

    /**
     * Check if key exists.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    public boolean has(String key) {
        return data.containsKey(key);
    }

    /**
     * Remove a key from context.
     * 
     * @param key key
     * @since 0.1.7
     */
    public void remove(String key) {
        data.remove(key);
    }

    /**
     * Get string value.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    public String getString(String key) {
        Object value = get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Get string value with default.
     * 
     * @param key key
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    public String getString(String key, String defaultValue) {
        Object value = get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * Get integer value.
     * 
     * @param key key
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    public int getInt(String key, int defaultValue) {
        Object value = get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    /**
     * Get boolean value.
     * 
     * @param key key
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    /**
     * getList.
     * 
     * @param key key
     * @return List<T>
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public <T> java.util.List<T> getList(String key) {
        Object value = get(key);
        if (value instanceof java.util.List) {
            return (java.util.List<T>) value;
        }
        return java.util.Collections.emptyList();
    }

    /**
     * getMap.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> getMap(String key) {
        Object value = get(key);
        if (value instanceof Map) {
            return (Map<K, V>) value;
        }
        return null;
    }

    /**
     * Convert context to dictionary.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> toDict() {
        return new HashMap<>(data);
    }

    /**
     * Clear all data.
     * 
     * @since 0.1.7
     */
    public void clear() {
        data.clear();
    }

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        return "RuntimeContext(" + data + ")";
    }
}
