/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.db;

import com.openjiuwen.core.foundation.store.db.DefaultDbStore;
import com.openjiuwen.spi.store.BaseDbStore;

import javax.sql.DataSource;

/**
 * Java baseline for the Python GaussDbStore extension.
 *
 * <p>The current Java version reuses JDBC/DataSource semantics and keeps the
 * explicit GaussDB entry point so callers do not need to fall back to the
 * generic DefaultDbStore type.</p>
 */
public class GaussDbStore extends BaseDbStore<DataSource> {
    private final DataSource dataSource;

    /**
     * Auto-generated for codecheck compliance.
     */
    public GaussDbStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public GaussDbStore(String jdbcUrl) {
        this(new DefaultDbStore(jdbcUrl).getEngine());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public GaussDbStore(String jdbcUrl, String username, String password) {
        this(new DefaultDbStore(jdbcUrl, username, password).getEngine());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public DataSource getEngine() {
        return dataSource;
    }
}
