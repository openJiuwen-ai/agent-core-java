/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.db;

import com.openjiuwen.spi.store.BaseDbStore;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

/**
 * Lightweight JDBC-backed default DB store.
 * 
 * @since 0.1.7
 */
public class DefaultDbStore extends BaseDbStore<DataSource> {
    private final DataSource dataSource;

    /**
     * DefaultDbStore.
     * 
     * @param jdbcUrl jdbcUrl
     * @since 0.1.7
     */
    public DefaultDbStore(String jdbcUrl) {
        this(jdbcUrl, null, null);
    }

    /**
     * DefaultDbStore.
     * 
     * @param jdbcUrl jdbcUrl
     * @param username username
     * @param password password
     * @since 0.1.7
     */
    public DefaultDbStore(String jdbcUrl, String username, String password) {
        this.dataSource = new SimpleDriverManagerDataSource(jdbcUrl, username, password);
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

    private static final class SimpleDriverManagerDataSource implements DataSource {
        private final String jdbcUrl;
        private final String username;
        private final String password;
        private volatile PrintWriter logWriter;
        private volatile int loginTimeout;

        /**
         * SimpleDriverManagerDataSource.
         * 
         * @param jdbcUrl jdbcUrl
         * @param username username
         * @param password password
         * @since 0.1.7
         */
        private SimpleDriverManagerDataSource(String jdbcUrl, String username, String password) {
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
        }

        /**
         * getConnection.
         * 
         * @return the result
         * @throws SQLException SQLException
         * @since 0.1.7
         */
        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(jdbcUrl, username, password);
        }

        /**
         * getConnection.
         * 
         * @param username username
         * @param password password
         * @return the result
         * @throws SQLException SQLException
         * @since 0.1.7
         */
        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(jdbcUrl, username, password);
        }

        /**
         * unwrap.
         * 
         * @param iface iface
         * @return the result
         * @throws SQLException SQLException
         * @since 0.1.7
         */
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        /**
         * isWrapperFor.
         * 
         * @param iface iface
         * @return the result
         * @since 0.1.7
         */
        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        /**
         * getLogWriter.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }

        /**
         * setLogWriter.
         * 
         * @param out out
         * @since 0.1.7
         */
        @Override
        public void setLogWriter(PrintWriter out) {
            this.logWriter = out;
        }

        /**
         * setLoginTimeout.
         * 
         * @param seconds seconds
         * @since 0.1.7
         */
        @Override
        public void setLoginTimeout(int seconds) {
            this.loginTimeout = seconds;
            DriverManager.setLoginTimeout(seconds);
        }

        /**
         * getLoginTimeout.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public int getLoginTimeout() {
            return loginTimeout;
        }

        /**
         * getParentLogger.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Logger getParentLogger() {
            return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        }
    }
}
