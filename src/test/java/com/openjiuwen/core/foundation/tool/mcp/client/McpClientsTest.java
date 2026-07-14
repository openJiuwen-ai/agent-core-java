/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.common.clients.ClientRegistry;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpClientsTest {

    @Test
    void registerDefaultsRegistersAllMcpClientFactoriesIdempotently() {
        McpClients.registerDefaults();
        McpClients.registerDefaults();

        List<String> clients = ClientRegistry.getClientRegistry().listClients();

        assertThat(clients).contains(
                "mcp_sse",
                "mcp_stdio",
                "mcp_streamable-http",
                "mcp_streamable_http",
                "mcp_openapi",
                "mcp_playwright"
        );
    }

    @Test
    void packageMarkerRegisterDefaultsDelegatesToMcpClients() {
        FoundationToolMcpClientPackageMarker.registerDefaults();

        List<String> clients = ClientRegistry.getClientRegistry().listClients();

        assertThat(clients).contains("mcp_sse");
    }

    @Test
    void registryFactoriesCreateConcreteClientsFromMcpServerConfig() {
        McpClients.registerDefaults();
        McpServerConfig config = McpServerConfig.builder()
                .serverId("registry-srv")
                .serverName("registry")
                .serverPath("http://127.0.0.1:3001/mcp")
                .clientType("streamable-http")
                .build();

        assertThat(ClientRegistry.getClientRegistry().getClient("sse", "mcp", Map.of("config", config)))
                .isInstanceOf(SseClient.class);
        assertThat(ClientRegistry.getClientRegistry().getClient("stdio", "mcp", Map.of("config", config)))
                .isInstanceOf(StdioClient.class);
        assertThat(ClientRegistry.getClientRegistry().getClient("streamable-http", "mcp", Map.of("config", config)))
                .isInstanceOf(StreamableHttpClient.class);
        assertThat(ClientRegistry.getClientRegistry().getClient("streamable_http", "mcp", Map.of("config", config)))
                .isInstanceOf(StreamableHttpClient.class);
        assertThat(ClientRegistry.getClientRegistry().getClient("openapi", "mcp", Map.of("config", config)))
                .isInstanceOf(OpenApiClient.class);
        assertThat(ClientRegistry.getClientRegistry().getClient("playwright", "mcp", Map.of("config", config)))
                .isInstanceOf(PlaywrightClient.class);
    }

    @Test
    void registryFactoryRejectsMissingOrWrongConfig() {
        McpClients.registerDefaults();

        assertThatThrownBy(() -> ClientRegistry.getClientRegistry().getClient("sse", "mcp", Map.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("MCP client factory requires kwargs['config']");
        assertThatThrownBy(() -> ClientRegistry.getClientRegistry().getClient("sse", "mcp", Map.of("config", "bad")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("McpServerConfig");
    }

    @Test
    void normalizeClientTypeSupportsCommonAliases() {
        assertThat(McpClients.normalizeClientType(null)).isEqualTo("sse");
        assertThat(McpClients.normalizeClientType("   ")).isEqualTo("sse");
        assertThat(McpClients.normalizeClientType("SSE")).isEqualTo("sse");
        assertThat(McpClients.normalizeClientType("mcp_SSE")).isEqualTo("sse");
        assertThat(McpClients.normalizeClientType(" stdio ")).isEqualTo("stdio");
        assertThat(McpClients.normalizeClientType("mcp_stdio")).isEqualTo("stdio");
        assertThat(McpClients.normalizeClientType("streamable_http")).isEqualTo("streamable-http");
        assertThat(McpClients.normalizeClientType("streamableHttp")).isEqualTo("streamable-http");
        assertThat(McpClients.normalizeClientType("STREAMABLE_HTTP")).isEqualTo("streamable-http");
        assertThat(McpClients.normalizeClientType("mcp_streamable_http")).isEqualTo("streamable-http");
        assertThat(McpClients.normalizeClientType("mcp-streamable-http")).isEqualTo("streamable-http");
        assertThat(McpClients.normalizeClientType("open-api")).isEqualTo("openapi");
        assertThat(McpClients.normalizeClientType("playwright")).isEqualTo("playwright");
    }
}
