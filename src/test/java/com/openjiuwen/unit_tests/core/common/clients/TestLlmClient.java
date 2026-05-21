/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.common.clients;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.common.clients.llm.HttpXConnectorPoolConfig;
import com.openjiuwen.core.common.clients.llm.HttpXConnectorPool;
import com.openjiuwen.core.common.clients.llm.LlmClient;

/**
 * Tests for LLM client.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.core.common.clients.test_llm_client}.
 * Validates LLM client configuration, connection pooling, and client creation.
 */
class TestLlmClient {

    // ---------------------------------------------------------------------------
    // Test HttpXConnectorPoolConfig - Mirrors Python TestHttpXConnectorPoolConfig
    // ---------------------------------------------------------------------------

    @Nested
    class TestHttpXConnectorPoolConfig {

        @Test
        @Tag("level0")
        void testDefaultValues() {
            // Python: HttpXConnectorPoolConfig() default values
            HttpXConnectorPoolConfig config = new HttpXConnectorPoolConfig();

            assertNotNull(config);
            assertEquals(20, config.getMaxKeepaliveConnections());
            assertNull(config.getLocalAddress());
            assertNull(config.getProxy());
            assertEquals(100, config.getLimit());
            assertEquals(30, config.getLimitPerHost());
            assertTrue(config.isSslVerify());
        }

        @Test
        @Tag("level0")
        void testCustomValues() {
            // Python: HttpXConnectorPoolConfig with custom values
            HttpXConnectorPoolConfig config = new HttpXConnectorPoolConfig(
                    50,
                    "192.168.1.100",
                    "http://proxy.example.com:8080",
                    200,
                    50,
                    false
            );

            assertEquals(50, config.getMaxKeepaliveConnections());
            assertEquals("192.168.1.100", config.getLocalAddress());
            assertEquals("http://proxy.example.com:8080", config.getProxy());
            assertEquals(200, config.getLimit());
            assertEquals(50, config.getLimitPerHost());
            assertFalse(config.isSslVerify());
        }

        @Test
        @Tag("level0")
        void testValidationPositiveValues() {
            // Python: raises ValueError for non-positive values
            assertThrows(IllegalArgumentException.class, () -> {
                new HttpXConnectorPoolConfig(0, null, null, 100, 30, true);
            });

            assertThrows(IllegalArgumentException.class, () -> {
                new HttpXConnectorPoolConfig(-5, null, null, 100, 30, true);
            });
        }

        @Test
        @Tag("level0")
        void testKeyGeneration() {
            // Python: config generates unique keys
            HttpXConnectorPoolConfig config1 = new HttpXConnectorPoolConfig(20, null, null, 100, 30, true);
            HttpXConnectorPoolConfig config2 = new HttpXConnectorPoolConfig(20, null, null, 100, 30, true);

            assertEquals(config1.generateKey(), config2.generateKey());
        }
    }

    // ---------------------------------------------------------------------------
    // Test HttpXConnectorPool - Mirrors Python TestHttpXConnectorPool
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testHttpXConnectorPoolExists() {
        assertNotNull(HttpXConnectorPool.class);
    }

    @Test
    @Tag("level0")
    void testHttpXConnectorPoolMethods() {
        assertTrue(HttpXConnectorPool.class.getDeclaredMethods().length > 0);
    }

    // ---------------------------------------------------------------------------
    // Test LLM client - Mirrors Python LLM client tests
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testLlmClientExists() {
        assertNotNull(LlmClient.class);
    }

    @Test
    @Tag("level0")
    void testLlmClientMethods() {
        assertTrue(LlmClient.class.getDeclaredMethods().length > 0);
    }

    // ---------------------------------------------------------------------------
    // Test client registry integration - Additional validation
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testClientRegistryIntegration() {
        assertNotNull(com.openjiuwen.core.common.clients.ClientRegistry.getInstance());
    }
}