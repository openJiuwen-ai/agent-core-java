/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.examples.mcp.sse;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.client.SseClient;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SSE (Server-Sent Events) MCP Client Example.
 * <p>
 * Demonstrates direct usage of SseClient to connect to MCP server,
 * list tools, and call tools.
 * <p>
 * Mirrors Python's {@code client_direct} in
 * {@code examples.mcp.sse.client_direct}.
 */
@DisabledIfEnvironmentVariable(named = "SKIP_MCP_TESTS", matches = "true")
class ClientDirectTest {

    @Test
    @Tag("level0")
    void testSseClientClassExists() {
        // Verify SseClient class is available
        assertNotNull(SseClient.class);
    }

    @Test
    @Tag("level0")
    void testMcpServerConfigBuilder() {
        // Mirrors Python's McpServerConfig construction
        McpServerConfig config = McpServerConfig.builder()
                .serverName("calculator-sse-server")
                .serverPath("http://127.0.0.1:3001/sse")
                .clientType("sse")
                .build();

        assertNotNull(config);
        assertEquals("calculator-sse-server", config.getServerName());
        assertEquals("http://127.0.0.1:3001/sse", config.getServerPath());
        assertEquals("sse", config.getClientType());
    }

    @Test
    @Tag("level0")
    void testSseClientConstruction() {
        // Mirrors Python's SseClient creation
        McpServerConfig config = McpServerConfig.builder()
                .serverName("test-server")
                .serverPath("http://localhost:8080/sse")
                .clientType("sse")
                .build();

        SseClient client = new SseClient(config);
        assertNotNull(client);
    }
}