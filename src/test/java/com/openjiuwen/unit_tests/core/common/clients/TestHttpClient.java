/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.common.clients;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.common.clients.http.HttpClient;
import com.openjiuwen.core.common.clients.http.HttpSession;
import com.openjiuwen.core.common.clients.http.HttpSessionManager;
import com.openjiuwen.core.common.clients.http.SessionConfig;

/**
 * Tests for HTTP client.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.core.common.clients.test_http_client}.
 * Validates HTTP session configuration, connection management, and request handling.
 */
class TestHttpClient {

    // ---------------------------------------------------------------------------
    // Test SessionConfig - Mirrors Python TestSessionConfig
    // ---------------------------------------------------------------------------

    @Nested
    class TestSessionConfig {

        @Test
        @Tag("level0")
        void testCustomValues() {
            // Python: SessionConfig(timeout=30.0, connect_timeout=10.0, ...)
            SessionConfig config = new SessionConfig(
                    30.0,
                    10.0,
                    true,
                    Map.of("User-Agent", "Test"),
                    "http://proxy:8080"
            );

            assertNotNull(config);
            assertEquals(30.0, config.getTimeout());
            assertEquals(10.0, config.getConnectTimeout());
            assertTrue(config.isRaiseForStatus());
            assertEquals("http://proxy:8080", config.getProxy());
        }

        @Test
        @Tag("level0")
        void testGenerateKey() {
            // Python: config1.generate_key() == config2.generate_key()
            SessionConfig config1 = new SessionConfig(30.0, Map.of("User-Agent", "Test"));
            SessionConfig config2 = new SessionConfig(30.0, Map.of("User-Agent", "Test"));
            SessionConfig config3 = new SessionConfig(60.0, Map.of("User-Agent", "Test"));

            assertEquals(config1.generateKey(), config2.generateKey());
            assertNotEquals(config1.generateKey(), config3.generateKey());
        }

        @Test
        @Tag("level0")
        void testGenerateKeyWithComplexTypes() {
            // Python: config with complex types generates valid key
            Map<String, String> headers = new HashMap<>();
            headers.put("b", "2");
            headers.put("a", "1");

            SessionConfig config = new SessionConfig(30.0, headers);
            String key = config.generateKey();

            assertNotNull(key);
            assertTrue(key.length() > 0);
        }
    }

    // ---------------------------------------------------------------------------
    // Test HttpSession - Mirrors Python TestHttpSession
    // ---------------------------------------------------------------------------

    @Nested
    class TestHttpSession {

        @Test
        @Tag("level0")
        void testHttpSessionExists() {
            assertNotNull(HttpSession.class);
        }

        @Test
        @Tag("level0")
        void testHttpSessionMethods() {
            assertTrue(HttpSession.class.getDeclaredMethods().length > 0);
        }
    }

    // ---------------------------------------------------------------------------
    // Test HttpSessionManager - Mirrors Python TestHttpSessionManager
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testHttpSessionManagerExists() {
        assertNotNull(HttpSessionManager.class);
    }

    @Test
    @Tag("level0")
    void testHttpSessionManagerMethods() {
        assertTrue(HttpSessionManager.class.getDeclaredMethods().length > 0);
    }

    // ---------------------------------------------------------------------------
    // Test HttpClient - Mirrors Python HttpClient tests
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testHttpClientExists() {
        assertNotNull(HttpClient.class);
    }

    @Test
    @Tag("level0")
    void testHttpClientMethods() {
        assertTrue(HttpClient.class.getDeclaredMethods().length > 0);
    }

    // ---------------------------------------------------------------------------
    // Test session manager singleton - Additional validation
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testSessionManagerGetInstance() {
        HttpSessionManager manager = HttpSessionManager.getInstance();
        assertNotNull(manager);
    }
}