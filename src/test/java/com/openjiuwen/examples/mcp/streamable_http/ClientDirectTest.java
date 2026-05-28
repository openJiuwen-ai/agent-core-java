/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.examples.mcp.streamable_http;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Streamable HTTP MCP Client Example.
 * <p>
 * Mirrors Python's {@code client_direct} in
 * {@code examples.mcp.streamable_http.client_direct}.
 */
@DisabledIfEnvironmentVariable(named = "SKIP_MCP_TESTS", matches = "true")
class ClientDirectTest {

    @Test
    @Tag("level0")
    void testMcpClientClassExists() {
        assertNotNull(McpClient.class);
    }

    @Test
    @Tag("level0")
    void testMcpServerConfigBuilder() {
        McpServerConfig config = McpServerConfig.builder()
                .serverName("streamable-http-server")
                .serverPath("http://127.0.0.1:8080/mcp")
                .clientType("streamable_http")
                .build();

        assertNotNull(config);
        assertEquals("streamable-http-server", config.getServerName());
        assertEquals("http://127.0.0.1:8080/mcp", config.getServerPath());
        assertEquals("streamable_http", config.getClientType());
    }

    @Test
    @Tag("level0")
    void testMcpServerConfigConvenience() {
        McpServerConfig config = new McpServerConfig();
        assertNotNull(config);
        // Default client type should be "sse"
        assertEquals("sse", config.getClientType());
    }
}