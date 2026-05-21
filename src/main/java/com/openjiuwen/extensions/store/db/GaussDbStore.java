/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

import javax.sql.DataSource;

/**
 * GaussDB database store implementation.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.store.db.gauss_db_store.GaussDbStore}.
 *
 * This class wraps a DataSource for GaussDB database operations.
 */
public class GaussDbStore {

    private final DataSource dataSource;

    /**
     * Initialize GaussDbStore with a DataSource.
     *
     * @param dataSource The DataSource instance for GaussDB connections.
     */
    public GaussDbStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Get a database connection.
     *
     * @return Database connection.
     * @throws SQLException If connection fails.
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Get a database connection asynchronously.
     *
     * @return CompletableFuture with connection.
     */
    public CompletableFuture<Connection> getConnectionAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return dataSource.getConnection();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get GaussDB connection", e);
            }
        });
    }

    /**
     * Get the underlying DataSource.
     *
     * @return The DataSource instance.
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Close the store (cleanup resources).
     */
    public void close() {
        // DataSource cleanup handled externally
    }
}