/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.db;

import com.openjiuwen.core.foundation.store.BaseDbStore;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * Mirrors Python's {@code DefaultDbStore} in
 * {@code openjiuwen/core/foundation/store/db/default_db_store.py}.
 *
 * @param <E> concrete asynchronous engine handle type
 */
public class DefaultDbStore<E> extends BaseDbStore<E> {

    private final Object asyncConn;

    public DefaultDbStore(String jdbcUrl) {
        this(jdbcUrl, null, null);
    }

    @SuppressWarnings("unchecked")
    public DefaultDbStore(String jdbcUrl, String username, String password) {
        this((E) new SimpleDriverManagerDataSource(jdbcUrl, username, password));
    }

    public DefaultDbStore(E asyncConn) {
        this.asyncConn = asyncConn;
    }

    @SuppressWarnings("unchecked")
    public E getAsyncConn() {
        return (E) asyncConn;
    }

    public DataSource getEngine() {
        return (DataSource) asyncConn;
    }

    @Override
    @SuppressWarnings("unchecked")
    public E getAsyncEngine() {
        return (E) asyncConn;
    }

    private static final class SimpleDriverManagerDataSource implements DataSource {
        private final String jdbcUrl;
        private final String username;
        private final String password;
        private volatile PrintWriter logWriter;
        private volatile int loginTimeout;

        private SimpleDriverManagerDataSource(String jdbcUrl, String username, String password) {
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
        }

        @Override
        public Connection getConnection() throws SQLException {
            if (username == null && password == null) {
                return DriverManager.getConnection(jdbcUrl);
            }
            return DriverManager.getConnection(jdbcUrl, username, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(jdbcUrl, username, password);
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }

        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            this.logWriter = out;
        }

        @Override
        public void setLoginTimeout(int seconds) {
            this.loginTimeout = seconds;
            DriverManager.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() {
            return loginTimeout;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        }
    }
}
