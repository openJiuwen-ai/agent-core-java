/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.spi.store.BaseKVStore;

import java.util.Map;

/**
 * Provider for creating persistence-based checkpointers.
 * <p>
 * Mirrors Python's {@code PersistenceCheckpointerProvider}.
 * <p>
 * Configuration format:
 * <pre>
 * {
 *   "kv_store": BaseKVStore instance
 * }
 * </pre>
 */
public class PersistenceCheckpointerProvider implements CheckpointerProvider {

    @Override
    public Checkpointer create(Map<String, Object> conf) {
        Object kvStoreObj = conf != null ? conf.get("kv_store") : null;
        if (!(kvStoreObj instanceof BaseKVStore kvStore)) {
            throw new IllegalArgumentException(
                    "PersistenceCheckpointerProvider requires 'kv_store' of type BaseKVStore in conf");
        }
        return new PersistenceCheckpointer(kvStore);
    }
}
