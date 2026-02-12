// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.store;

import java.sql.Connection;

/**
 * Abstract base class defining a unified interface for a database storage.
 * <p>
 * Provides access to a database connection for performing database operations.
 * </p>
 * 
 * <p>Converted from Python: agent-core/openjiuwen/core/foundation/store/base_db_store.py</p>
 */
public abstract class BaseDbStore {

    /**
     * Return the database connection, allowing callers to perform database operations
     * such as issuing raw SQL statements or using JDBC APIs.
     * <p>
     * Note: In Python this returns AsyncEngine from SQLAlchemy. In Java we use
     * standard JDBC Connection. For true async database access, consider using
     * R2DBC or other reactive database libraries in the future.
     * </p>
     *
     * @return the JDBC Connection instance
     */
    public abstract Connection getConnection();
}

