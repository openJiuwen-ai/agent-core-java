/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store;

import java.time.Duration;
import java.util.List;

/** Optional KV capability for atomic expiry and TTL refresh. */
public interface ExpirableKVStore {
    /** Write with a positive duration applied atomically with the value. */
    void set(String key, Object value, Duration ttl);

    /** Refresh existing keys only; missing keys must not be created and failures propagate. */
    void refreshTtl(List<String> keys, Duration ttl);
}
