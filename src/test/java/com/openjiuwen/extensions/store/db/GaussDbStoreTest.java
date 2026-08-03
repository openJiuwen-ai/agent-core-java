/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.db;

import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertSame;

class GaussDbStoreTest {

    @Test
    void getAsyncEngineReturnsWrappedDataSource() {
        DataSource dataSource = new StubDataSource();
        GaussDbStore store = new GaussDbStore(dataSource);
        assertSame(dataSource, store.getAsyncEngine());
    }

    private static final class StubDataSource implements DataSource {
        @Override
        public Connection getConnection() {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public Connection getConnection(String username, String password) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException("not needed");
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not needed");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
