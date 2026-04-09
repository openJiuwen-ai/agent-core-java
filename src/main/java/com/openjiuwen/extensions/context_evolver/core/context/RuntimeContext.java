/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.extensions.context_evolver.core.context;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.core.context.runtime_context.RuntimeContext}.
 * 
 * Context object passed between operations in a flow.
 */
public class RuntimeContext {
    
    private final Map<String, Object> data;
    
    public RuntimeContext() {
        this.data = new ConcurrentHashMap<>();
    }
    
    /**
     * Get a value from context.
     *
     * @param key context key
     * @return value or null
     */
    public Object get(String key) {
        return data.get(key);
    }
    
    /**
     * Get a value from context with default.
     *
     * @param key          context key
     * @param defaultValue default value
     * @return value or default
     */
    public Object get(String key, Object defaultValue) {
        return data.getOrDefault(key, defaultValue);
    }
    
    /**
     * Set a value in context.
     *
     * @param key   context key
     * @param value value to store
     */
    public void set(String key, Object value) {
        data.put(key, value);
    }
    
    /**
     * Check if key exists.
     */
    public boolean has(String key) {
        return data.containsKey(key);
    }
    
    /**
     * Remove a key from context.
     */
    public void remove(String key) {
        data.remove(key);
    }
    
    /**
     * Get string value.
     */
    public String getString(String key) {
        Object value = get(key);
        return value != null ? value.toString() : null;
    }
    
    /**
     * Get string value with default.
     */
    public String getString(String key, String defaultValue) {
        Object value = get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * Get integer value.
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
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
    
    /**
     * Get list value.
     */
    @SuppressWarnings("unchecked")
    public <T> java.util.List<T> getList(String key) {
        Object value = get(key);
        if (value instanceof java.util.List) {
            return (java.util.List<T>) value;
        }
        return null;
    }
    
    /**
     * Get map value.
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
     */
    public Map<String, Object> toDict() {
        return new HashMap<>(data);
    }
    
    /**
     * Clear all data.
     */
    public void clear() {
        data.clear();
    }
    
    @Override
    public String toString() {
        return "RuntimeContext(" + data + ")";
    }
}
