/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.stdio;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.client.StdioClient;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MCP stdio client.
 * <p>
 * Mirrors Python's {@code client_direct} in
 * {@code examples.mcp.stdio.client_direct}.
 */
@Disabled("Requires MCP configuration")
class ClientDirectTest {

    @Test
    @DisplayName("Test StdioClient class exists")
    void testStdioClientClassExists() {
        assertNotNull(StdioClient.class);
    }

    @Test
    @DisplayName("Test McpServerConfig can be built")
    void testMcpServerConfigBuilder() {
        McpServerConfig config = McpServerConfig.builder()
                .serverName("text-processor-stdio-server")
                .serverPath("")
                .clientType("stdio")
                .build();
        assertNotNull(config);
        assertEquals("text-processor-stdio-server", config.getServerName());
        assertEquals("", config.getServerPath());
        assertEquals("stdio", config.getClientType());
    }

    @Test
    @DisplayName("Test McpServerConfig with params")
    void testMcpServerConfigWithParams() {
        McpServerConfig config = McpServerConfig.builder()
                .serverName("test-server")
                .clientType("stdio")
                .params(java.util.Map.of(
                        "command", "python",
                        "args", java.util.List.of("server.py")
                ))
                .build();
        assertNotNull(config);
        assertNotNull(config.getParams());
        assertTrue(config.getParams().containsKey("command"));
    }
}
