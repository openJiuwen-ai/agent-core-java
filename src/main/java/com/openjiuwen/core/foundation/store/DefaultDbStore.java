// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.store;

import java.sql.Connection;

/**
 * Default implementation of {@link BaseDbStore}.
 * <p>
 * Simple wrapper around a JDBC Connection.
 * </p>
 * 
 * <p>Converted from Python: agent-core/openjiuwen/core/foundation/store/default_db_store.py</p>
 */
public class DefaultDbStore extends BaseDbStore {
    private final Connection connection;

    /**
     * Constructs a DefaultDbStore with the given database connection.
     *
     * @param connection the JDBC connection to wrap
     */
    public DefaultDbStore(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Connection getConnection() {
        return connection;
    }
}

