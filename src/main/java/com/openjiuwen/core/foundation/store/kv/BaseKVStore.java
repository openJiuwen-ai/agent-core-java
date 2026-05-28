/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.kv;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for all KV-store backends.
 * <p>
 * Mirrors Python's {@code BaseKVStore} ABC from
 * <code>foundation/store/base_kv_store.py</code>.
 *
 * <p>Plugin authors may subclass this and export the class directly;
 * callers instantiate directly — there is no factory lookup.
 */
public abstract class BaseKVStore {

    /**
     * Store or overwrite a key-value pair.
     *
     * @param key   the unique string identifier
     * @param value the string or bytes payload
     */
    public abstract CompletableFuture<Void> set(String key, byte[] value);

    /**
     * Atomically set a key-value pair only if the key does not already exist.
     *
     * @param key    the string key to set
     * @param value  the value to associate
     * @param expiry optional expiry in seconds; null means no expiry
     * @return true if the pair was set, false if key already existed
     */
    public abstract CompletableFuture<Boolean> exclusiveSet(String key, byte[] value, Integer expiry);

    /**
     * Retrieve the value associated with the given key.
     *
     * @param key the string key to look up
     * @return the stored value, or null if absent
     */
    public abstract CompletableFuture<byte[]> get(String key);

    /**
     * Check whether a key exists.
     *
     * @param key the string key to check
     * @return true if the key exists
     */
    public abstract CompletableFuture<Boolean> exists(String key);

    /**
     * Remove the specified key from the store.
     *
     * @param key the string key to delete
     */
    public abstract CompletableFuture<Void> delete(String key);

    /**
     * Retrieve multiple values by their keys.
     *
     * @param keys the keys to look up
     * @return list of values in the same order; null for missing keys
     */
    public abstract CompletableFuture<Collection<byte[]>> multiGet(Collection<String> keys);

    /**
     * Store multiple key-value pairs.
     *
     * @param pairs the key-value pairs to store
     */
    public abstract CompletableFuture<Void> multiSet(Map<String, byte[]> pairs);

    /**
     * Close the store and release resources.
     */
    public abstract CompletableFuture<Void> close();
}
