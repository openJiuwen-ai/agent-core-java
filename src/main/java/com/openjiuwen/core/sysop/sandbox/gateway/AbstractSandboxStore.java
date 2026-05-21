/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.gateway;

import java.util.List;
import java.util.Optional;

/**
 * Abstract base class for sandbox record storage.
 * <p>
 * Provides async-like interface for storing and retrieving sandbox records.
 * In Java, we use CompletableFuture for async operations, but this interface
 * returns values directly for simplicity in the initial implementation.
 * <p>
 * Mirrors Python's {@code AbstractSandboxStore} in {@code sandbox/gateway/sandbox_store.py}.
 */
public abstract class AbstractSandboxStore {

    /**
     * Retrieve a sandbox record by key.
     *
     * @param key the isolation key
     * @return the sandbox record, or null if not found
     */
    public abstract SandboxRecord get(String key);

    /**
     * Store a sandbox record.
     *
     * @param key    the isolation key
     * @param record the sandbox record to store
     */
    public abstract void set(String key, SandboxRecord record);

    /**
     * Delete a sandbox record by key (hash delete).
     *
     * @param key the isolation key
     * @return the removed sandbox record, or null if not found
     */
    public abstract SandboxRecord hdel(String key);

    /**
     * Clear all records from the store.
     *
     * @return list of all removed records
     */
    public abstract List<SandboxRecord> flushdb();

    /**
     * Evict records that have exceeded their idle TTL.
     *
     * @param idleTtlSeconds maximum idle time in seconds
     * @param now            current timestamp (epoch seconds)
     * @return list of evicted records
     */
    public abstract List<SandboxRecord> evictExpired(int idleTtlSeconds, double now);
}