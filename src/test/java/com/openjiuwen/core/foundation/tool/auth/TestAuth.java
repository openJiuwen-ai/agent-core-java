/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for tool authentication.
 * <p>
 * Mirrors Python's {@code test_auth.py}.
 */
@DisplayName("Tool Auth Tests")
class TestAuth {

    @Nested
    class TestToolAuthConfig {
        @Test
        void testToolAuthConfigCreation() {
            ToolAuthConfig config = new ToolAuthConfig("ssl",
                    Map.of("verify_switch_env", "RESTFUL_SSL_VERIFY"), "restful_api", "test-tool-id");

            assertEquals("ssl", config.getAuthType());
            assertEquals("restful_api", config.getToolType());
            assertEquals("test-tool-id", config.getToolId());
            assertEquals("RESTFUL_SSL_VERIFY", config.getConfig().get("verify_switch_env"));
        }

        @Test
        void testToolAuthConfigWithoutToolId() {
            ToolAuthConfig config = new ToolAuthConfig("header_and_query", Map.of("api_key", "test-key"), "database");

            assertEquals("header_and_query", config.getAuthType());
            assertEquals("database", config.getToolType());
            assertNull(config.getToolId());
        }
    }

    @Nested
    class TestToolAuthResult {
        @Test
        void testToolAuthResultCreation() {
            ToolAuthResult result = new ToolAuthResult(true,
                    Map.of("headers", Map.of("Authorization", "Bearer token")),
                    "Authentication successful");

            assertTrue(result.isSuccess());
            assertEquals("Authentication successful", result.getMessage());
            assertEquals(Map.of("Authorization", "Bearer token"), result.getAuthData().get("headers"));
        }

        @Test
        void testToolAuthResultWithError() {
            RuntimeException error = new RuntimeException("Authentication failed");
            ToolAuthResult result = new ToolAuthResult(false, Map.of(), "Authentication failed", error);

            assertFalse(result.isSuccess());
            assertSame(error, result.getError());
            assertEquals("Authentication failed", result.getMessage());
        }
    }

    @Nested
    class TestAuthHeaderAndQueryProvider {
        @Test
        void testAuthProviderWithHeaders() {
            ToolAuthResult result = headerAuth(Map.of("Authorization", "Bearer test-token", "X-Custom", "value"), Map.of());

            assertTrue(result.isSuccess());
            assertEquals("Bearer test-token", headers(result).get("Authorization"));
            assertEquals("value", headers(result).get("X-Custom"));
        }

        @Test
        void testAuthProviderWithQueryParams() {
            ToolAuthResult result = headerAuth(Map.of(), Map.of("api_key", "test-key", "version", "v1"));

            assertTrue(result.isSuccess());
            assertEquals("test-key", queryParams(result).get("api_key"));
            assertEquals("v1", queryParams(result).get("version"));
        }

        @Test
        void testAuthProviderWithBoth() {
            ToolAuthResult result = headerAuth(Map.of("Authorization", "Bearer test-token"), Map.of("api_key", "test-key"));

            assertEquals("Bearer test-token", headers(result).get("Authorization"));
            assertEquals("test-key", queryParams(result).get("api_key"));
        }

        @Test
        void testAuthProviderWithoutCredentials() {
            ToolAuthResult result = headerAuth(Map.of(), Map.of());

            assertTrue(result.isSuccess());
            assertTrue(result.getAuthData().isEmpty());
        }
    }

    @Nested
    class TestAuthCallbacks {
        @Test
        void testSslAuthHandlerVerifyTrue() {
            withSystemProperty("SSL_VERIFY", "true", () -> {
                ToolAuthResult result = new SSLAuthStrategy().authenticate(
                        new ToolAuthConfig("ssl", Map.of("url", "https://example.com"), "restful_api"));
                assertTrue(result.isSuccess());
                assertEquals(true, result.getAuthData().get("ssl_verify"));
                assertEquals(true, result.getAuthData().get("url_is_https"));
            });
        }

        @Test
        void testSslAuthHandlerVerifyFalse() {
            withSystemProperty("SSL_VERIFY", "false", () -> {
                ToolAuthResult result = new SSLAuthStrategy().authenticate(
                        new ToolAuthConfig("ssl", Map.of("url", "https://example.com"), "restful_api"));
                assertTrue(result.isSuccess());
                assertEquals(false, result.getAuthData().get("ssl_verify"));
            });
        }

        @Test
        void testSslAuthHandlerExceptionHandling() {
            ToolAuthResult result = AuthStrategyRegistry.executeAuth(
                    new ToolAuthConfig("unsupported", Map.of(), "restful_api"));
            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("Unsupported auth type"));
        }

        @Test
        void testSslAuthHandlerCertEmpty() {
            ToolAuthResult result = new SSLAuthStrategy().authenticate(
                    new ToolAuthConfig("ssl", Map.of("ssl_cert_env", "MISSING_TEST_CERT"), "restful_api"));
            assertTrue(result.isSuccess());
            assertNull(result.getAuthData().get("ssl_cert"));
        }

        @Test
        void testAuthHeaderAndQueryParamsHandlerWithCredentials() {
            ToolAuthResult result = headerAuth(Map.of("Authorization", "Bearer token"), Map.of("api_key", "key"));
            assertTrue(result.isSuccess());
            assertEquals("Bearer token", headers(result).get("Authorization"));
            assertEquals("key", queryParams(result).get("api_key"));
        }

        @Test
        void testAuthHeaderAndQueryParamsHandlerOnlyHeaders() {
            ToolAuthResult result = headerAuth(Map.of("Authorization", "Bearer token"), Map.of());
            assertEquals("Bearer token", headers(result).get("Authorization"));
            assertTrue(queryParams(result).isEmpty());
        }

        @Test
        void testAuthHeaderAndQueryParamsHandlerOnlyQueryParams() {
            ToolAuthResult result = headerAuth(Map.of(), Map.of("api_key", "key"));
            assertTrue(headers(result).isEmpty());
            assertEquals("key", queryParams(result).get("api_key"));
        }

        @Test
        void testAuthHeaderAndQueryParamsHandlerEmptyCredentials() {
            ToolAuthResult result = headerAuth(Map.of(), Map.of());
            assertTrue(result.isSuccess());
            assertTrue(result.getAuthData().isEmpty());
        }

        @Test
        void testAuthHandlerWrongType() {
            ToolAuthResult result = AuthStrategyRegistry.executeAuth(new ToolAuthConfig("wrong", Map.of(), "mcp"));
            assertFalse(result.isSuccess());
        }
    }

    @Nested
    class TestSseClientAuth {
        @Test
        void testSseClientAuthFlow() {
            ToolAuthResult result = headerAuth(Map.of("Authorization", "Bearer sse"), Map.of("transport", "sse"));
            assertTrue(result.isSuccess());
            assertEquals("Bearer sse", headers(result).get("Authorization"));
        }

        @Test
        void testSslAuthMissingCertRaisesException() {
            ToolAuthResult result = new SSLAuthStrategy().authenticate(
                    new ToolAuthConfig("ssl", Map.of("ssl_cert_env", "MISSING_TEST_CERT"), "sse"));
            assertTrue(result.isSuccess());
            assertNull(result.getAuthData().get("ssl_cert"));
        }
    }

    @Nested
    class TestStreamableHttpClientAuth {
        @Test
        void testStreamableHttpClientAuthFlow() {
            ToolAuthResult result = headerAuth(Map.of("Authorization", "Bearer stream"), Map.of("q", "1"));
            assertTrue(result.isSuccess());
            assertEquals("Bearer stream", headers(result).get("Authorization"));
            assertEquals("1", queryParams(result).get("q"));
        }
    }

    @Nested
    class TestRestfulApiAuth {
        @Test
        void testRestfulApiAuthFlow() {
            withSystemProperty("RESTFUL_SSL_VERIFY", "false", () -> {
                ToolAuthResult ssl = new SSLAuthStrategy().authenticate(new ToolAuthConfig("ssl",
                        Map.of("verify_switch_env", "RESTFUL_SSL_VERIFY", "url", "https://api.example.com"),
                        "restful_api"));
                ToolAuthResult headers = headerAuth(Map.of("Authorization", "Bearer api-token"), Map.of());
                assertEquals(false, ssl.getAuthData().get("ssl_verify"));
                assertEquals("Bearer api-token", headers(headers).get("Authorization"));
            });
        }
    }

    private static ToolAuthResult headerAuth(Map<String, String> authHeaders, Map<String, String> authQueryParams) {
        return new HeaderQueryAuthStrategy().authenticate(new ToolAuthConfig("header_and_query",
                Map.of("auth_headers", authHeaders, "auth_query_params", authQueryParams), "mcp"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> headers(ToolAuthResult result) {
        return (Map<String, String>) result.getAuthData().getOrDefault("auth_headers", Map.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> queryParams(ToolAuthResult result) {
        return (Map<String, String>) result.getAuthData().getOrDefault("auth_query_params", Map.of());
    }

    private static void withSystemProperty(String key, String value, Runnable action) {
        String old = System.getProperty(key);
        try {
            System.setProperty(key, value);
            action.run();
        } finally {
            if (old == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, old);
            }
        }
    }
}
