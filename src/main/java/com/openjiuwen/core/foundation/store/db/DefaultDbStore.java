/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.store.db;

import com.openjiuwen.spi.store.BaseDbStore;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * Lightweight JDBC-backed default DB store.
 */
public class DefaultDbStore extends BaseDbStore<DataSource> {

    private final DataSource dataSource;

    public DefaultDbStore(String jdbcUrl) {
        this(jdbcUrl, null, null);
    }

    public DefaultDbStore(String jdbcUrl, String username, String password) {
        this.dataSource = new SimpleDriverManagerDataSource(jdbcUrl, username, password);
    }

    @Override
    public DataSource getEngine() {
        return dataSource;
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
            return DriverManager.getConnection(jdbcUrl, username, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(jdbcUrl, username, password);
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
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
