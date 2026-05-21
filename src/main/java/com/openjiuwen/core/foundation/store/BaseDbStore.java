/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import javax.sql.DataSource;

/**
 * Abstract base class for raw DB access.
 * <p>
 * Mirrors Python's {@code BaseDbStore} ABC from
 * <code>foundation/store/base_db_store.py</code>.
 *
 * <p>Provides access to a database connection pool (DataSource in Java,
 * equivalent to SQLAlchemy AsyncEngine in Python).
 */
public abstract class BaseDbStore {

    /**
     * Return the DataSource for database operations.
     * <p>
     * Equivalent to Python's {@code get_async_engine()}.
     *
     * @return the DataSource instance
     */
    public abstract DataSource getDataSource();
}
