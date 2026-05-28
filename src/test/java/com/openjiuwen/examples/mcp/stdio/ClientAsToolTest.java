/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.stdio;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpTool;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.mcp.client.StdioClient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stdio — MCPTool usage example.
 * <p>
 * Demonstrates using MCPTool with the Stdio transport.
 * <p>
 * Mirrors Python's {@code client_as_tool} in
 * {@code examples.mcp.stdio.client_as_tool}.
 */
@DisabledIfEnvironmentVariable(named = "SKIP_MCP_TESTS", matches = "true")
class ClientAsToolTest {

    @Test
    @Tag("level0")
    void testStdioClientClassExists() {
        // Verify StdioClient class is available
        assertNotNull(StdioClient.class);
    }

    @Test
    @Tag("level0")
    void testMcpToolClassExists() {
        // Verify MCPTool class is available
        assertNotNull(McpTool.class);
    }

    @Test
    @Tag("level0")
    void testMcpToolCardClassExists() {
        // Verify McpToolCard class is available
        assertNotNull(McpToolCard.class);
    }

    @Test
    @Tag("level0")
    void testMcpClientClassExists() {
        assertNotNull(com.openjiuwen.core.foundation.tool.mcp.McpClient.class);
    }

    @Test
    @Tag("level0")
    void testMcpServerConfigBuilderForStdio() {
        // Mirrors Python's McpServerConfig construction for stdio client
        McpServerConfig config = McpServerConfig.builder()
                .serverName("text-processor-stdio-server")
                .serverPath("")
                .clientType("stdio")
                .build();

        assertNotNull(config);
        assertEquals("text-processor-stdio-server", config.getServerName());
        assertEquals("stdio", config.getClientType());
    }

    @Test
    @Tag("level0")
    void testStdioClientConstruction() {
        // Mirrors Python's StdioClient creation
        McpServerConfig config = McpServerConfig.builder()
                .serverName("test-stdio-server")
                .serverPath("")
                .clientType("stdio")
                .build();

        StdioClient client = new StdioClient(config);
        assertNotNull(client);
    }
}