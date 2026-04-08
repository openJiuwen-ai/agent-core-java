/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.spi.store;

/**
 * Abstract base class defining a unified interface for relational database storage.
 * <p>
 * Mirrors Python's {@code BaseDbStore} ABC.
 * <p>
 * In the Python version this returns an SQLAlchemy AsyncEngine. In Java,
 * the concrete implementation should provide a JDBC DataSource or similar.
 *
 * @param <E> the engine/datasource type (e.g., {@code javax.sql.DataSource})
 */
public abstract class BaseDbStore<E> {

    /**
     * Return the database engine or data source, allowing callers to perform
     * database operations.
     *
     * @return the engine instance
     */
    public abstract E getEngine();
}
