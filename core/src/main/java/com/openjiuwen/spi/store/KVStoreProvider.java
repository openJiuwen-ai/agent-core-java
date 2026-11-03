/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store;

import java.util.Map;

/**
 * Provider interface for creating KV store instances.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} from
 * {@code META-INF/services/com.openjiuwen.spi.store.KVStoreProvider}.
 * Each provider declares which {@code typeName()} it supports.
 * Service adapters can also register providers programmatically via
 * {@link KVStoreFactory#register(String, KVStoreProvider)}.
 * 
 * @see KVStoreFactory
 * @see BaseKVStore
 * @since 0.1.7
 */
public interface KVStoreProvider {
    /**
     * typeName.
     * 
     * @return the result
     * @since 0.1.7
     */
    String typeName();

    /**
     * Create a KV store with the given configuration.
     * 
     * @param conf the configuration map
     * @return a new BaseKVStore instance
     * @since 0.1.7
     */
    BaseKVStore create(Map<String, Object> conf);
}
