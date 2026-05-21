/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Tool Auth classes.
 * <p>
 * Mirrors Python's test_auth.py from
 * <code>tests/unit_tests/core/foundation/tool/test_auth.py</code>.
 */
@DisplayName("Tool Auth Tests")
class TestAuth {

    @Nested
    @DisplayName("ToolAuthConfig Tests")
    class TestToolAuthConfig {

        @Test
        @DisplayName("tool auth config creation")
        void testToolAuthConfigCreation() {
            Map<String, Object> config = new HashMap<>();
            config.put("verify_switch_env", "RESTFUL_SSL_VERIFY");

            ToolAuthConfig authConfig = new ToolAuthConfig(
                    AuthType.SSL.getValue(),
                    config,
                    "restful_api",
                    "test-tool-id"
            );

            assertEquals(AuthType.SSL.getValue(), authConfig.getAuthType());
            assertEquals(config, authConfig.getConfig());
            assertEquals("restful_api", authConfig.getToolType());
            assertEquals("test-tool-id", authConfig.getToolId());
        }

        @Test
        @DisplayName("tool auth config without tool id")
        void testToolAuthConfigWithoutToolId() {
            Map<String, Object> config = new HashMap<>();
            config.put("api_key", "test-key");

            ToolAuthConfig authConfig = new ToolAuthConfig(
                    "api_key",
                    config,
                    "database"
            );

            assertEquals("api_key", authConfig.getAuthType());
            assertNull(authConfig.getToolId());
        }

        @Test
        @DisplayName("config is immutable")
        void testConfigIsImmutable() {
            Map<String, Object> config = new HashMap<>();
            config.put("key", "value");

            ToolAuthConfig authConfig = new ToolAuthConfig("ssl", config, "restful_api");

            // Attempting to modify returned config should throw
            Map<String, Object> returnedConfig = authConfig.getConfig();
            assertThrows(UnsupportedOperationException.class, () -> {
                returnedConfig.put("new_key", "new_value");
            });
        }
    }

    @Nested
    @DisplayName("ToolAuthResult Tests")
    class TestToolAuthResult {

        @Test
        @DisplayName("tool auth result creation")
        void testToolAuthResultCreation() {
            Map<String, Object> authData = new HashMap<>();
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer token");
            authData.put("headers", headers);

            ToolAuthResult result = new ToolAuthResult(
                    true,
                    authData,
                    "Authentication successful"
            );

            assertTrue(result.isSuccess());
            assertEquals(authData, result.getAuthData());
            assertEquals("Authentication successful", result.getMessage());
            assertNull(result.getError());
        }

        @Test
        @DisplayName("tool auth result with error")
        void testToolAuthResultWithError() {
            RuntimeException error = new RuntimeException("Authentication failed");

            ToolAuthResult result = new ToolAuthResult(
                    false,
                    new HashMap<>(),
                    "Authentication failed",
                    error
            );

            assertFalse(result.isSuccess());
            assertEquals(error, result.getError());
        }

        @Test
        @DisplayName("auth data is immutable")
        void testAuthDataIsImmutable() {
            Map<String, Object> authData = new HashMap<>();
            authData.put("key", "value");

            ToolAuthResult result = new ToolAuthResult(true, authData, "success");

            Map<String, Object> returnedData = result.getAuthData();
            assertThrows(UnsupportedOperationException.class, () -> {
                returnedData.put("new_key", "new_value");
            });
        }
    }

    @Nested
    @DisplayName("AuthType Tests")
    class TestAuthTypeEnum {

        @Test
        @DisplayName("auth type values")
        void testAuthTypeValues() {
            assertEquals("ssl", AuthType.SSL.getValue());
            assertEquals("header_and_query", AuthType.HEADER_AND_QUERY.getValue());
        }

        @Test
        @DisplayName("auth type from value")
        void testAuthTypeFromValue() {
            assertEquals(AuthType.SSL, AuthType.fromValue("ssl"));
            assertEquals(AuthType.SSL, AuthType.fromValue("SSL"));
            assertEquals(AuthType.HEADER_AND_QUERY, AuthType.fromValue("header_and_query"));
            assertNull(AuthType.fromValue("unknown"));
            assertNull(AuthType.fromValue(null));
        }
    }

    @Nested
    @DisplayName("AuthStrategyRegistry Tests")
    class TestAuthStrategyRegistry {

        @Test
        @DisplayName("registry contains SSL strategy")
        void testRegistryContainsSSLStrategy() {
            AuthStrategy strategy = AuthStrategyRegistry.getStrategy(AuthType.SSL.getValue());
            assertNotNull(strategy);
            assertTrue(strategy instanceof SSLAuthStrategy);
        }

        @Test
        @DisplayName("registry contains header and query strategy")
        void testRegistryContainsHeaderAndQueryStrategy() {
            AuthStrategy strategy = AuthStrategyRegistry.getStrategy(AuthType.HEADER_AND_QUERY.getValue());
            assertNotNull(strategy);
            assertTrue(strategy instanceof HeaderQueryAuthStrategy);
        }

        @Test
        @DisplayName("registry returns null for unknown type")
        void testRegistryReturnsNullForUnknownType() {
            AuthStrategy strategy = AuthStrategyRegistry.getStrategy("unknown_type");
            assertNull(strategy);
        }
    }

    @Nested
    @DisplayName("SSLAuthStrategy Tests")
    class TestSSLAuthStrategy {

        @Test
        @DisplayName("SSL auth strategy type")
        void testSSLAuthStrategyType() {
            SSLAuthStrategy strategy = new SSLAuthStrategy();
            assertEquals(AuthType.SSL.getValue(), strategy.getAuthType());
        }
    }

    @Nested
    @DisplayName("HeaderQueryAuthStrategy Tests")
    class TestHeaderQueryAuthStrategy {

        @Test
        @DisplayName("header query auth strategy type")
        void testHeaderQueryAuthStrategyType() {
            HeaderQueryAuthStrategy strategy = new HeaderQueryAuthStrategy();
            assertEquals(AuthType.HEADER_AND_QUERY.getValue(), strategy.getAuthType());
        }
    }
}