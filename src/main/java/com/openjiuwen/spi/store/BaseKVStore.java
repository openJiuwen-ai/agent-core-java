/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.spi.store;

import java.util.List;
import java.util.Map;

/**
 * Abstract base class defining a unified interface for key-value storage.
 * <p>
 * Mirrors Python's {@code BaseKVStore} ABC. In Java, the async methods
 * are replaced with synchronous blocking calls (to be run on virtual threads).
 */
public abstract class BaseKVStore {

    /**
     * Store or overwrite a key-value pair.
     *
     * @param key   unique string identifier
     * @param value string or bytes payload
     */
    public abstract void set(String key, Object value);

    /**
     * Atomically set a key only if it does not already exist.
     *
     * @param key    string key
     * @param value  string or bytes payload
     * @param expiry optional expiry in seconds (null for no expiry)
     * @return true if set, false if key already existed
     */
    public abstract boolean exclusiveSet(String key, Object value, Integer expiry);

    /**
     * Retrieve the value associated with the given key.
     *
     * @param key string key
     * @return the value, or null if absent
     */
    public abstract Object get(String key);

    /**
     * Check whether a key exists.
     *
     * @param key string key
     * @return true if the key exists
     */
    public abstract boolean exists(String key);

    /**
     * Remove the specified key.
     *
     * @param key string key
     */
    public abstract void delete(String key);

    /**
     * Retrieve all key-value pairs whose keys start with the given prefix.
     *
     * @param prefix string prefix
     * @return map of matching keys to values
     */
    public abstract Map<String, Object> getByPrefix(String prefix);

    /**
     * Remove all key-value pairs whose keys start with the given prefix.
     *
     * @param prefix    string prefix
     * @param batchSize optional batch size for deletion (null for single-op)
     */
    public abstract void deleteByPrefix(String prefix, Integer batchSize);

    /**
     * Bulk-retrieve values for multiple keys.
     *
     * @param keys list of string keys
     * @return list of values (or null) in the same order
     */
    public abstract List<Object> mget(List<String> keys);

    /**
     * Delete a batch of keys.
     *
     * @param keys      list of keys
     * @param batchSize optional batch size
     * @return number of keys successfully deleted
     */
    public abstract int batchDelete(List<String> keys, Integer batchSize);

    /**
     * Create a pipeline for batch operations.
     *
     * @return a pipeline instance
     */
    public abstract KVStorePipeline pipeline();
}
