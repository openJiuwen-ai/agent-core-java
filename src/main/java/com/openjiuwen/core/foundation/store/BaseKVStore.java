// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.store;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class defining a unified interface for a key-value storage.
 * <p>
 * All operations return {@link CompletableFuture} to support asynchronous execution.
 * Implementations can choose between truly async I/O or wrapping synchronous operations.
 * </p>
 * 
 * <p>Converted from Python: agent-core/openjiuwen/core/foundation/store/base_kv_store.py</p>
 */
public abstract class BaseKVStore {

    /**
     * Store or overwrite a key-value pair.
     *
     * @param key   the unique string identifier for the entry
     * @param value the string payload to associate with the key
     * @return a CompletableFuture that completes when the operation finishes
     */
    public abstract CompletableFuture<Void> set(String key, String value);

    /**
     * Atomically set a key-value pair only if the key does not already exist.
     *
     * @param key    the string key to set
     * @param value  the string value to associate with the key
     * @param expiry optional expiry time in seconds for the key-value pair (null for no expiry)
     * @return a CompletableFuture with true if the key-value pair was successfully set,
     *         false if the key already existed
     */
    public abstract CompletableFuture<Boolean> exclusiveSet(String key, String value, Integer expiry);

    /**
     * Retrieve the value associated with the given key.
     *
     * @param key the string key to look up
     * @return a CompletableFuture with the stored string value, or null if the key is absent
     */
    public abstract CompletableFuture<String> get(String key);

    /**
     * Check whether a key exists in the store.
     *
     * @param key the string key to check
     * @return a CompletableFuture with true if the key exists, false otherwise
     */
    public abstract CompletableFuture<Boolean> exists(String key);

    /**
     * Remove the specified key from the store.
     *
     * @param key the string key to delete (no action is taken if the key does not exist)
     * @return a CompletableFuture that completes when the operation finishes
     */
    public abstract CompletableFuture<Void> delete(String key);

    /**
     * Retrieve all key-value pairs whose keys start with the given prefix.
     *
     * @param prefix the string prefix to match against existing keys
     * @return a CompletableFuture with a map from every matching key to its corresponding value
     */
    public abstract CompletableFuture<Map<String, String>> getByPrefix(String prefix);

    /**
     * Remove all key-value pairs whose keys start with the given prefix.
     *
     * @param prefix the string prefix to match against existing keys
     * @return a CompletableFuture that completes when the operation finishes
     */
    public abstract CompletableFuture<Void> deleteByPrefix(String prefix);

    /**
     * Bulk-retrieve values for multiple keys in a single operation.
     *
     * @param keys a list of string keys to fetch
     * @return a CompletableFuture with a list of string values (or null) in the same order as the input keys
     */
    public abstract CompletableFuture<List<String>> mget(List<String> keys);
}

