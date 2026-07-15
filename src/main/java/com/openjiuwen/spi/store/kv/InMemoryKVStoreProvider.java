/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.kv;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.KVStoreProvider;

import java.util.Map;

/**
 * Built-in KV store provider for in-memory storage.
 * <p>
 * Creates in-memory KV store instances backed by a ConcurrentHashMap.
 * Suitable for testing and single-process scenarios where persistence is not required.
 * 
 * @see KVStoreProvider
 * @see com.openjiuwen.core.foundation.store.kv.InMemoryKVStore
 * @since 0.1.7
 */
public final class InMemoryKVStoreProvider implements KVStoreProvider {
    /**
     * typeName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String typeName() {
        return "in_memory";
    }

    /**
     * Creates a new in-memory KV store instance.
     * 
     * @param conf the configuration map (ignored for in-memory implementation)
     * @return a new InMemoryKVStore instance
     * @since 0.1.7
     */
    @Override
    public BaseKVStore create(Map<String, Object> conf) {
        return new InMemoryKVStore();
    }
}
