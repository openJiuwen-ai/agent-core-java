/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.db;

import com.openjiuwen.core.foundation.store.db.DefaultDbStore;
import com.openjiuwen.spi.store.BaseDbStore;

import javax.sql.DataSource;

/**
 * Java baseline for the Python GaussDbStore extension.
 * <p>
 * The current Java version reuses JDBC/DataSource semantics and keeps the
 * explicit GaussDB entry point so callers do not need to fall back to the
 * generic DefaultDbStore type.
 * </p>
 * 
 * @since 0.1.7
 */
public class GaussDbStore extends BaseDbStore<DataSource> {
    private final DataSource dataSource;

    /**
     * GaussDbStore.
     * 
     * @param dataSource dataSource
     * @since 0.1.7
     */
    public GaussDbStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * GaussDbStore.
     * 
     * @param jdbcUrl jdbcUrl
     * @since 0.1.7
     */
    public GaussDbStore(String jdbcUrl) {
        this(new DefaultDbStore(jdbcUrl).getEngine());
    }

    /**
     * GaussDbStore.
     * 
     * @param jdbcUrl jdbcUrl
     * @param username username
     * @param password password
     * @since 0.1.7
     */
    public GaussDbStore(String jdbcUrl, String username, String password) {
        this(new DefaultDbStore(jdbcUrl, username, password).getEngine());
    }

    /**
     * getEngine.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public DataSource getEngine() {
        return dataSource;
    }
}
