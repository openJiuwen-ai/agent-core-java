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
 * 
 * <pre>
 * {
 * "kv_store": BaseKVStore instance  // Optional if db_type/db_path provided
 * "db_type": "sqlite" | "shelve",    // Optional, creates default KVStore
 * "db_path": "checkpointer.db"       // Optional, database file path
 * }
 * </pre>
 * 
 * @since 0.1.7
 */
public class PersistenceCheckpointerProvider implements CheckpointerProvider {
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
     *
     * <p>Delegates to {@link PersistenceCheckpointer#createFromConfig(Map)} so sqlite /
     * shelve / DataSource / explicit {@link BaseKVStore} configs keep working when this
     * provider is discovered via ServiceLoader.</p>
     *
     * @param conf the configuration map, may contain "kv_store", "db_type", and "db_path"
     * @return a Checkpointer instance
     * @since 0.1.7
     */
    @Override
    public Checkpointer create(Map<String, Object> conf) {
        return PersistenceCheckpointer.createFromConfig(conf);
    }
}
