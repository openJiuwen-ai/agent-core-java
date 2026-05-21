/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.foundation.tool.auth.ToolAuthResult;
import com.openjiuwen.core.foundation.tool.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.client.SseClient;
import com.openjiuwen.core.foundation.tool.mcp.client.StreamableHttpClient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Tool Validation.
 * <p>
 * Mirrors Python's test_validation.py from
 * <code>tests/unit_tests/core/foundation/tool/test_validation.py</code>.
 */
@DisplayName("Tool Validation Tests")
class TestValidation {

    @Nested
    @DisplayName("McpServerConfig Tests")
    class TestMcpServerConfig {

        @Test
        @DisplayName("mcp server config can be created")
        void testMcpServerConfigCanBeCreated() {
            McpServerConfig config = new McpServerConfig();
            config.setServerName("test-sse-server");
            config.setServerPath("http://127.0.0.1:8080/sse");
            config.setClientType("sse");
            config.setServerId("test-sse-server-id");

            assertEquals("test-sse-server", config.getServerName());
            assertEquals("http://127.0.0.1:8080/sse", config.getServerPath());
            assertEquals("sse", config.getClientType());
            assertEquals("test-sse-server-id", config.getServerId());
        }

        @Test
        @DisplayName("mcp server config with auth headers")
        void testMcpServerConfigWithAuthHeaders() {
            McpServerConfig config = new McpServerConfig();
            Map<String, String> authHeaders = new HashMap<>();
            authHeaders.put("Authorization", "Bearer test_token");
            authHeaders.put("X-Custom-Header", "test_value");
            config.setAuthHeaders(authHeaders);

            assertNotNull(config.getAuthHeaders());
            assertEquals(2, config.getAuthHeaders().size());
        }

        @Test
        @DisplayName("mcp server config with auth query params")
        void testMcpServerConfigWithAuthQueryParams() {
            McpServerConfig config = new McpServerConfig();
            Map<String, String> authQueryParams = new HashMap<>();
            authQueryParams.put("api_key", "test_key");
            authQueryParams.put("version", "v1");
            config.setAuthQueryParams(authQueryParams);

            assertNotNull(config.getAuthQueryParams());
            assertEquals(2, config.getAuthQueryParams().size());
        }
    }

    @Nested
    @DisplayName("SseClient Tests")
    class TestSseClient {

        @Test
        @DisplayName("sse client can be created")
        void testSseClientCanBeCreated() {
            SseClient client = new SseClient();
            assertNotNull(client);
        }

        @Test
        @DisplayName("sse client is McpClient")
        void testSseClientIsMcpClient() {
            SseClient client = new SseClient();
            assertTrue(client instanceof com.openjiuwen.core.foundation.tool.mcp.McpClient);
        }
    }

    @Nested
    @DisplayName("StreamableHttpClient Tests")
    class TestStreamableHttpClient {

        @Test
        @DisplayName("streamable http client can be created")
        void testStreamableHttpClientCanBeCreated() {
            StreamableHttpClient client = new StreamableHttpClient();
            assertNotNull(client);
        }

        @Test
        @DisplayName("streamable http client is McpClient")
        void testStreamableHttpClientIsMcpClient() {
            StreamableHttpClient client = new StreamableHttpClient();
            assertTrue(client instanceof com.openjiuwen.core.foundation.tool.mcp.McpClient);
        }
    }

    @Nested
    @DisplayName("ToolAuthResult Tests")
    class TestToolAuthResultValidation {

        @Test
        @DisplayName("auth result success validation")
        void testAuthResultSuccessValidation() {
            Map<String, Object> authData = new HashMap<>();
            authData.put("headers", new HashMap<String, String>());

            ToolAuthResult result = new ToolAuthResult(true, authData, "Authentication successful");

            assertTrue(result.isSuccess());
            assertEquals("Authentication successful", result.getMessage());
            assertNull(result.getError());
        }

        @Test
        @DisplayName("auth result failure validation")
        void testAuthResultFailureValidation() {
            Exception error = new RuntimeException("Authentication failed");

            ToolAuthResult result = new ToolAuthResult(false, new HashMap<>(), "Authentication failed", error);

            assertFalse(result.isSuccess());
            assertNotNull(result.getError());
        }
    }

    @Nested
    @DisplayName("ClientType Tests")
    class TestClientType {

        @Test
        @DisplayName("sse client type")
        void testSseClientType() {
            McpServerConfig config = new McpServerConfig();
            config.setClientType("sse");

            assertEquals("sse", config.getClientType());
        }

        @Test
        @DisplayName("streamable_http client type")
        void testStreamableHttpClientType() {
            McpServerConfig config = new McpServerConfig();
            config.setClientType("streamable_http");

            assertEquals("streamable_http", config.getClientType());
        }

        @Test
        @DisplayName("stdio client type")
        void testStdioClientType() {
            McpServerConfig config = new McpServerConfig();
            config.setClientType("stdio");

            assertEquals("stdio", config.getClientType());
        }
    }
}