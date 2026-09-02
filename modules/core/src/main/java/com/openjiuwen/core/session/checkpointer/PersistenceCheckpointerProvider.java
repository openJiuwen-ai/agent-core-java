/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.KVStoreFactory;

import java.util.Map;

/**
 * Provider for creating persistence-based checkpointers.
 * <p>
 * Mirrors Python's {@code PersistenceCheckpointerProvider}.
 * <p>
 * Configuration format:
 * 
 * <pre>
 * {
 * "kv_store": BaseKVStore instance  // Optional if db_type/db_path provided
 * "db_type": "sqlite" | "shelve",    // Optional; shelve retains the legacy in-memory fallback
 * "db_path": "checkpointer.db"       // Optional, database file path
 * }
 * </pre>
 * 
 * @since 0.1.7
 */
public class PersistenceCheckpointerProvider implements CheckpointerProvider {
    private static final String DEFAULT_DATABASE_TYPE = "sqlite";

    /**
     * typeName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String typeName() {
        return "persistence";
    }

    /**
     * Creates a persistence-based checkpointer from the given configuration.
     * <p>
     * If a valid {@code kv_store} instance is provided in the configuration, it is used directly. Otherwise, SQLite
     * is created when it is selected explicitly or no database type is configured. Unsupported or malformed
     * configurations retain the legacy in-memory fallback for backward compatibility.
     * 
     * @param conf the configuration map, may contain "kv_store" (BaseKVStore), "db_type", and "db_path"
     * @return a persistence checkpointer backed by the configured KV store, or the legacy in-memory fallback
     * @since 0.1.7
     */
    @Override
    public Checkpointer create(Map<String, Object> conf) {
        Map<String, Object> safeConf = conf == null ? Map.of() : conf;
        Object configuredStore = safeConf.get("kv_store");
        if (configuredStore instanceof BaseKVStore kvStore) {
            return new PersistenceCheckpointer(kvStore);
        }
        if (configuredStore != null) {
            return new InMemoryCheckpointer();
        }

        Object configuredType = safeConf.getOrDefault("db_type", DEFAULT_DATABASE_TYPE);
        if (DEFAULT_DATABASE_TYPE.equals(configuredType)) {
            return new PersistenceCheckpointer(KVStoreFactory.create(DEFAULT_DATABASE_TYPE, safeConf));
        }
        return new InMemoryCheckpointer();
    }
}
