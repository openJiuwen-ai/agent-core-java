/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store;

import java.time.Duration;
import java.util.List;

/**
 * Optional key expiry operations. Implementations must not split an expiring
 * write into a plain write followed by expiry, or silently ignore failures.
 *
 * @since 0.1.15
 */
public interface ExpirableKVStore {
    /**
     * Writes a value and a positive, whole-second TTL in one atomic operation.
     *
     * @param key the key to write
     * @param value the value to store
     * @param ttl the expiration duration
     * @throws IllegalArgumentException if the TTL is unsupported
     */
    void set(String key, Object value, Duration ttl);

    /**
     * Refreshes existing keys without creating missing keys.
     *
     * @param keys the existing keys to refresh
     * @param ttl the expiration duration
     * @throws IllegalArgumentException if the TTL is unsupported
     */
    void refreshTtl(List<String> keys, Duration ttl);
}
