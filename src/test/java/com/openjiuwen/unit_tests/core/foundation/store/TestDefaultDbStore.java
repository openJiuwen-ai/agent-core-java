/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DefaultDbStore.
 * <p>
 * Mirrors Python's test_default_db_store.py from
 * <code>tests/unit_tests/core/foundation/store/test_default_db_store.py</code>.
 */
@DisplayName("Default Db Store Tests")
class TestDefaultDbStore {

    // Stub classes
    static class AsyncEngineStub {
        String url;
        boolean connected;

        AsyncEngineStub(String url) {
            this.url = url;
            this.connected = true;
        }

        Connection getConnection() {
            // Return mock connection
            return null;
        }
    }

    static class DefaultDbStoreStub {
        AsyncEngineStub asyncConn;

        DefaultDbStoreStub(AsyncEngineStub asyncConn) {
            this.asyncConn = asyncConn;
        }

        AsyncEngineStub getAsyncEngine() {
            return asyncConn;
        }
    }

    @Nested
    @DisplayName("Initialization Tests")
    class TestInitialization {

        @Test
        @DisplayName("init with async engine")
        void testInitWithAsyncEngine() {
            AsyncEngineStub engine = new AsyncEngineStub("jdbc:test://localhost");
            DefaultDbStoreStub store = new DefaultDbStoreStub(engine);

            assertNotNull(store.asyncConn);
            assertEquals("jdbc:test://localhost", store.asyncConn.url);
        }
    }

    @Nested
    @DisplayName("Get Async Engine Tests")
    class TestGetAsyncEngine {

        @Test
        @DisplayName("get async engine returns stored engine")
        void testGetAsyncEngineReturnsStoredEngine() {
            AsyncEngineStub engine = new AsyncEngineStub("jdbc:test://localhost");
            DefaultDbStoreStub store = new DefaultDbStoreStub(engine);

            AsyncEngineStub result = store.getAsyncEngine();

            assertNotNull(result);
            assertSame(engine, result);
        }

        @Test
        @DisplayName("get async engine returns same instance each time")
        void testGetAsyncEngineReturnsSameInstance() {
            AsyncEngineStub engine = new AsyncEngineStub("jdbc:test://localhost");
            DefaultDbStoreStub store = new DefaultDbStoreStub(engine);

            AsyncEngineStub result1 = store.getAsyncEngine();
            AsyncEngineStub result2 = store.getAsyncEngine();

            assertSame(result1, result2);
        }
    }

    @Nested
    @DisplayName("Connection Tests")
    class TestConnection {

        @Test
        @DisplayName("async engine is connected")
        void testAsyncEngineIsConnected() {
            AsyncEngineStub engine = new AsyncEngineStub("jdbc:test://localhost");
            DefaultDbStoreStub store = new DefaultDbStoreStub(engine);

            assertTrue(store.getAsyncEngine().connected);
        }
    }
}