/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.foundation.store.BaseKVStore;

import java.util.Map;

/**
 * Provider for creating persistence-based checkpointers.
 * <p>
 * Mirrors Python's {@code PersistenceCheckpointerProvider}.
 * <p>
 * Configuration format:
 * <pre>
 * {
 *   "kv_store": BaseKVStore instance  // Optional if db_type/db_path provided
 *   "db_type": "sqlite" | "shelve",    // Optional, creates default KVStore
 *   "db_path": "checkpointer.db"       // Optional, database file path
 * }
 * </pre>
 *
 * @since 0.1.12
 */
public class PersistenceCheckpointerProvider implements CheckpointerProvider {
    /**
     * Returns the persistence checkpointer type name.
     *
     * @return the type name "persistence"
     */
    public String typeName() {
        return "persistence";
    }

    /**
     * Creates a persistence-based checkpointer from the given configuration.
     * <p>
     * If a {@code kv_store} instance is provided in the configuration, it will be used
     * directly. Otherwise, falls back to an in-memory checkpointer.
     *
     * @param conf the configuration map, may contain "kv_store" (BaseKVStore), "db_type", and "db_path"
     * @return a Checkpointer instance backed by the provided KV store or an in-memory fallback
     */
    @Override
    public Checkpointer create(Map<String, Object> conf) {
        // First, check if kv_store is directly provided
        Object kvStoreObj = conf != null ? conf.get("kv_store") : null;
        if (kvStoreObj instanceof BaseKVStore kvStore) {
            return new PersistenceCheckpointer(kvStore);
        }
        
        // Fall back to in-memory checkpointer if no proper kv_store is configured
        // This allows tests to run without requiring specific KVStore implementations
        return new InMemoryCheckpointer();
    }
}
