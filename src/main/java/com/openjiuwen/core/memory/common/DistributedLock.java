/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.common;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.spi.store.BaseKVStore;

import java.util.UUID;

/**
 * Synchronous distributed lock using KV store exclusive_set.
 *
 * <p>Mirrors Python's {@code DistributedLock} in
 * {@code openjiuwen.core.memory.common.distributed_lock}.
 */
public class DistributedLock implements AutoCloseable {

    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;

    private final BaseKVStore store;
    private final String lockKey;
    private final int ttl;
    private final long retryDelayMs;
    private String lockValue;

    public DistributedLock(BaseKVStore store, String lockName) {
        this.store = store;
        this.lockKey = "_lock/" + lockName;
        this.ttl = 10;
        this.retryDelayMs = 10;
    }

    public void acquire() {
        this.lockValue = UUID.randomUUID().toString();
        while (true) {
            boolean success = store.exclusiveSet(lockKey, lockValue, ttl);
            if (success) {
                return;
            }
            try {
                Thread.sleep(retryDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public void release() {
        try {
            Object storedValue = store.get(lockKey);
            if (lockValue.equals(String.valueOf(storedValue))) {
                store.delete(lockKey);
            }
        } catch (Exception e) {
            MEMORY_LOGGER.error("[{}] Error releasing lock: {}", LogEventType.MEMORY_STORE, e.getMessage());
        }
    }

    @Override
    public void close() {
        release();
    }
}
