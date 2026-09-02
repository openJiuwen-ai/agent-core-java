/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store;

import java.util.List;
import java.util.Map;

/**
 * Abstract base class defining a unified interface for key-value storage.
 * <p>
 * Mirrors Python's {@code BaseKVStore} ABC. In Java, the async methods
 * are replaced with synchronous blocking calls (to be run on virtual threads).
 * <p>
 * Implements {@link AutoCloseable} so that implementations can override
 * {@link #close()} to release underlying resources (e.g. Redis connection
 * pool, in-memory map). The default {@link #close()} is a no-op for backward
 * compatibility with existing implementations that have no resources to release.
 *
 * @since 0.1.7
 */
public abstract class BaseKVStore implements AutoCloseable {
    /**
     * set.
     * 
     * @param key key
     * @param value value
     * @since 0.1.7
     */
    public abstract void set(String key, Object value);

    /**
     * Atomically set a key only if it does not already exist.
     * 
     * @param key string key
     * @param value string or bytes payload
     * @param expiry optional expiry in seconds (null for no expiry)
     * @return true if set, false if key already isExisted
     * @since 0.1.7
     */
    public abstract boolean exclusiveSet(String key, Object value, Integer expiry);

    /**
     * Retrieve the value associated with the given key.
     * 
     * @param key string key
     * @return the value, or null if absent
     * @since 0.1.7
     */
    public abstract Object get(String key);

    /**
     * Check whether a key exists.
     * 
     * @param key string key
     * @return true if the key isExists
     * @since 0.1.7
     */
    public abstract boolean isExists(String key);

    /**
     * Remove the specified key.
     * 
     * @param key string key
     * @since 0.1.7
     */
    public abstract void delete(String key);

    /**
     * Retrieve all key-value pairs whose keys start with the given prefix.
     * 
     * @param prefix string prefix
     * @return map of matching keys to values
     * @since 0.1.7
     */
    public abstract Map<String, Object> getByPrefix(String prefix);

    /**
     * Remove all key-value pairs whose keys start with the given prefix.
     * 
     * @param prefix string prefix
     * @param batchSize optional batch size for deletion (null for single-op)
     * @since 0.1.7
     */
    public abstract void deleteByPrefix(String prefix, Integer batchSize);

    /**
     * Bulk-retrieve values for multiple keys.
     * 
     * @param keys list of string keys
     * @return list of values (or null) in the same order
     * @since 0.1.7
     */
    public abstract List<Object> mget(List<String> keys);

    /**
     * Delete a batch of keys.
     * 
     * @param keys list of keys
     * @param batchSize optional batch size
     * @return number of keys successfully deleted
     * @since 0.1.7
     */
    public abstract int batchDelete(List<String> keys, Integer batchSize);

    /**
     * Create a pipeline for batch operations.
     * 
     * @return a pipeline instance
     * @since 0.1.7
     */
    public abstract KVStorePipeline pipeline();

    /**
     * Release underlying resources associated with this store.
     * <p>
     * Default implementation is a no-op for backward compatibility.
     * Implementations that hold resources (e.g. Redis connection pool,
     * in-memory map) should override this method to perform cleanup.
     * <p>
     * After {@code close()}, the store instance should not be used;
     * behaviour of subsequent operations is undefined.
     *
     * @since 0.1.13
     */
    @Override
    public void close() {
        // no-op default
    }
}
