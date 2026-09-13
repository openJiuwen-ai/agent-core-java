/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.foundation.tool.mcp.client.SseClient;
import com.openjiuwen.core.foundation.tool.mcp.client.StdioClient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Tests for McpClientFactory SPI registration and ServiceLoader discovery.
 */
class McpClientFactorySpiTest {
    // ========== ServiceLoader auto-discovery ==========
    @Test
    @DisplayName("ServiceLoader discovers built-in sse provider")
    void discoversSseProvider() {
        assertTrue(McpClientFactory.hasProvider("sse"));
    }

    @Test
    @DisplayName("ServiceLoader discovers built-in stdio provider")
    void discoversStdioProvider() {
        assertTrue(McpClientFactory.hasProvider("stdio"));
    }

    @Test
    @DisplayName("ServiceLoader discovers built-in openapi provider")
    void discoversOpenApiProvider() {
        assertTrue(McpClientFactory.hasProvider("openapi"));
    }

    @Test
    @DisplayName("ServiceLoader discovers built-in streamable_http provider")
    void discoversStreamableHttpProvider() {
        assertTrue(McpClientFactory.hasProvider("streamable_http"));
    }

    @Test
    @DisplayName("ServiceLoader discovers built-in playwright provider")
    void discoversPlaywrightProvider() {
        assertTrue(McpClientFactory.hasProvider("playwright"));
    }

    // ========== create() ==========

    @Test
    @DisplayName("create() with sse config returns SseClient")
    void createSseClient() {
        McpServerConfig config = McpServerConfig.builder().serverName("test-sse")
                .serverPath("http://localhost:8080/mcp").clientType("sse").build();
        McpClient client = McpClientFactory.create(config);
        assertInstanceOf(SseClient.class, client);
    }

    @Test
    @DisplayName("create() with stdio config returns StdioClient")
    void createStdioClient() {
        McpServerConfig config = McpServerConfig.builder().serverName("test-stdio")
                .serverPath("/usr/local/bin/mcp-server").clientType("stdio").build();
        McpClient client = McpClientFactory.create(config);
        assertInstanceOf(StdioClient.class, client);
    }

    @Test
    @DisplayName("create() with null clientType defaults to sse")
    void createWithNullClientTypeDefaultsToSse() {
        McpServerConfig config = McpServerConfig.builder().serverName("test-default")
                .serverPath("http://localhost:8080/mcp").clientType(null).build();
        McpClient client = McpClientFactory.create(config);
        assertInstanceOf(SseClient.class, client);
    }

    @Test
    @DisplayName("create() with streamable-http normalizes to streamable_http")
    void createNormalizesStreamableHttp() {
        McpServerConfig config = McpServerConfig.builder().serverName("test-streamable")
                .serverPath("http://localhost:8080/mcp").clientType("streamable-http").build();
        McpClient client = McpClientFactory.create(config);
        assertNotNull(client);
    }

    @Test
    @DisplayName("create() with unknown type throws UnsupportedOperationException")
    void createUnknownTypeThrows() {
        McpServerConfig config = McpServerConfig.builder().serverName("test-unknown").clientType("grpc").build();
        UnsupportedOperationException ex =
            assertThrows(UnsupportedOperationException.class, () -> McpClientFactory.create(config));
        assertTrue(ex.getMessage().contains("grpc"));
    }

    // ========== Manual register() ==========

    @Test
    @DisplayName("register() allows adding a custom MCP client provider")
    void registerCustomProvider() {
        McpClientFactory.register("custom_mock", new McpClientProvider() {
            @Override
            public String typeName() {
                return "custom_mock";
            }

            @Override
            public McpClient create(McpServerConfig config) {
                return new SseClient(config);
            }
        });
        assertTrue(McpClientFactory.hasProvider("custom_mock"));

        McpServerConfig config = McpServerConfig.builder().serverName("test-custom")
                .serverPath("http://localhost:9999/mcp").clientType("custom_mock").build();
        McpClient client = McpClientFactory.create(config);
        assertNotNull(client);
    }

    @Test
    @DisplayName("register() can override an existing provider")
    void registerOverridesExisting() {
        // First register a custom provider
        McpClientFactory.register("test_override", new McpClientProvider() {
            @Override
            public String typeName() {
                return "test_override";
            }

            @Override
            public McpClient create(McpServerConfig config) {
                return new SseClient(config);
            }
        });
        // Then override it with a different implementation
        McpClientFactory.register("test_override", new McpClientProvider() {
            @Override
            public String typeName() {
                return "test_override";
            }

            @Override
            public McpClient create(McpServerConfig config) {
                return new StdioClient(config);
            }
        });

        McpServerConfig config = McpServerConfig.builder().serverName("test-override").serverPath("/bin/test")
                .clientType("test_override").build();
        McpClient client = McpClientFactory.create(config);
        // After override, "test_override" now creates StdioClient
        assertInstanceOf(StdioClient.class, client);
    }

    // ========== hasProvider() ==========

    @Test
    @DisplayName("hasProvider() returns false for null")
    void hasProviderNull() {
        assertFalse(McpClientFactory.hasProvider(null));
    }

    @Test
    @DisplayName("hasProvider() returns false for unknown type")
    void hasProviderUnknown() {
        assertFalse(McpClientFactory.hasProvider("nonexistent"));
    }

    // ========== Additional test cases ==========

    @Test
    @DisplayName("hasProvider() is case-insensitive")
    void hasProviderCaseInsensitive() {
        assertTrue(McpClientFactory.hasProvider("SSE"));
        assertTrue(McpClientFactory.hasProvider("Sse"));
        assertTrue(McpClientFactory.hasProvider("STDIO"));
    }

    @Test
    @DisplayName("create() with empty string clientType throws UnsupportedOperationException")
    void createWithEmptyClientTypeThrows() {
        McpServerConfig config = McpServerConfig.builder().serverName("test-empty")
                .serverPath("http://localhost:8080/mcp").clientType("").build();
        assertThrows(UnsupportedOperationException.class, () -> McpClientFactory.create(config));
    }

    @Test
    @DisplayName("create() with openapi config returns non-null client")
    void createOpenApiClient() {
        McpServerConfig config = McpServerConfig.builder().serverName("test-openapi")
                .serverPath("http://localhost:8080/openapi.json").clientType("openapi").build();
        McpClient client = McpClientFactory.create(config);
        assertNotNull(client);
    }

    @Test
    @DisplayName("create() with streamable_http config returns non-null client")
    void createStreamableHttpClient() {
        McpServerConfig config = McpServerConfig.builder().serverName("test-streamable-http")
                .serverPath("http://localhost:8080/mcp").clientType("streamable_http").build();
        McpClient client = McpClientFactory.create(config);
        assertNotNull(client);
    }

    @Test
    @DisplayName("create() with playwright config returns non-null client")
    void createPlaywrightClient() {
        McpServerConfig config = McpServerConfig.builder().serverName("test-playwright")
                .serverPath("http://localhost:8080/playwright").clientType("playwright").build();
        McpClient client = McpClientFactory.create(config);
        assertNotNull(client);
    }

    @Test
    @DisplayName("Multiple create() calls with same config return different instances")
    void createReturnsDifferentInstances() {
        McpServerConfig config = McpServerConfig.builder().serverName("test-multi")
                .serverPath("http://localhost:8080/mcp").clientType("sse").build();
        McpClient client1 = McpClientFactory.create(config);
        McpClient client2 = McpClientFactory.create(config);
        assertNotSame(client1, client2);
    }

    @Test
    @DisplayName("Register provider that reads McpServerConfig params")
    void registerProviderThatReadsConfig() {
        McpClientFactory.register("config_aware", new McpClientProvider() {
            @Override
            public String typeName() {
                return "config_aware";
            }

            @Override
            public McpClient create(McpServerConfig config) {
                assertNotNull(config);
                assertNotNull(config.getServerName());
                return new SseClient(config);
            }
        });

        McpServerConfig config =
            McpServerConfig.builder().serverName("test-config-aware").serverPath("http://localhost:9999/mcp")
                    .clientType("config_aware").params(Map.of("custom_key", "custom_value")).build();
        McpClient client = McpClientFactory.create(config);
        assertInstanceOf(SseClient.class, client);
    }

    @Test
    @DisplayName("McpServerConfig with auth headers is passed to provider")
    void mcpServerConfigWithAuthHeaders() {
        McpServerConfig config =
            McpServerConfig.builder().serverName("test-auth").serverPath("http://localhost:8080/mcp").clientType("sse")
                    .authHeaders(Map.of("Authorization", "Bearer token123")).build();
        McpClient client = McpClientFactory.create(config);
        assertNotNull(client);
        assertInstanceOf(SseClient.class, client);
    }
}
