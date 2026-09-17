/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store;

import java.time.Duration;
import java.util.List;

/**
 * Optional key expiry operations. Implementations must not split an expiring
 * write into a plain write followed by expiry, or silently ignore failures.
 */
public interface ExpirableKVStore {
    /** Write a value with a positive, whole-second TTL in one atomic operation. */
    void set(String key, Object value, Duration ttl);

    /** Refresh existing keys without creating missing keys. */
    void refreshTtl(List<String> keys, Duration ttl);
}
