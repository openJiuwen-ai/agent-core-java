/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.sse;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.mcp.client.SseClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SSE (Server-Sent Events) MCP Client Example.
 * <p>
 * This client connects to the SSE MCP server (server.py), lists available tools,
 * and calls each calculator tool to demonstrate usage.
 * <p>
 * Mirrors Python's {@code client_direct} in
 * {@code examples.mcp.sse.client_direct}.
 * <p>
 * Prerequisites:
 * <ul>
 *   <li>Start the server first: run server.py</li>
 *   <li>Then run this test</li>
 * </ul>
 */
class ClientDirectTest {

    private static final Logger logger = Loggers.getLogger(ClientDirectTest.class);
    private static final String SERVER_URL = "http://127.0.0.1:3001/sse";
    private static final String SERVER_NAME = "calculator-sse-server";

    /**
     * Demonstrates direct MCP client usage with an SSE MCP server.
     * <p>
     * This test is disabled by default as it requires a running MCP server.
     * Enable by setting environment variable MCP_SERVER_RUNNING=true.
     */
    @Test
    @DisabledIfEnvironmentVariable(named = "MCP_SERVER_RUNNING", matches = "false", disabledReason = "Requires running MCP server")
    void demonstrateDirectClientUsage() throws Exception {
        logger.info("Connecting to SSE MCP server at {} ...", SERVER_URL);
        
        McpServerConfig config = McpServerConfig.builder()
                .serverName(SERVER_NAME)
                .serverPath(SERVER_URL)
                .clientType("sse")
                // Optional: add auth headers or query params if the server requires them
                // .authHeaders(Map.of("Authorization", "Bearer <token>"))
                // .authQueryParams(Map.of("api_key", "secret"))
                .build();
        McpClient client = new SseClient(config);

        boolean connected = client.connect();
        if (!connected) {
            logger.info("Failed to connect to SSE server. Make sure server.py is running.");
            return;
        }

        logger.info("Connected successfully!\n");

        // --- List available tools ---
        List<Object> tools = client.listTools();
        logger.info("Available tools ({}):", tools.size());
        for (Object tool : tools) {
            McpToolCard card = (McpToolCard) tool;
            logger.info("  - {}: {}", card.getName(), card.getDescription());
        }
        logger.info("");

        // --- Get info for a specific tool ---
        Object toolInfo = client.getToolInfo("add");
        if (toolInfo != null) {
            McpToolCard card = (McpToolCard) toolInfo;
            logger.info("Tool info for 'add': {} — {}", card.getName(), card.getDescription());
            logger.info("");
        }

        // --- Call tools ---
        logger.info("Calling tools:");

        Object result = client.callTool("add", Map.of("a", 10, "b", 3));
        logger.info("  add(10, 3)        = {}", result);

        result = client.callTool("subtract", Map.of("a", 10, "b", 3));
        logger.info("  subtract(10, 3)   = {}", result);

        result = client.callTool("multiply", Map.of("a", 10, "b", 3));
        logger.info("  multiply(10, 3)   = {}", result);

        result = client.callTool("divide", Map.of("a", 10, "b", 3));
        logger.info("  divide(10, 3)     = {}", result);

        result = client.callTool("divide", Map.of("a", 10, "b", 0));
        logger.info("  divide(10, 0)     = {}", result);

        result = client.callTool("power", Map.of("base", 2, "exponent", 8));
        logger.info("  power(2, 8)       = {}", result);

        logger.info("");

        // --- Disconnect ---
        client.disconnect();
        logger.info("Disconnected from SSE server.");
    }

    /**
     * Basic client instantiation test without requiring a running server.
     */
    @Test
    void clientCanBeInstantiated() {
        McpServerConfig config = McpServerConfig.builder()
                .serverName(SERVER_NAME)
                .serverPath(SERVER_URL)
                .clientType("sse")
                .build();
        
        McpClient client = new SseClient(config);
        assertNotNull(client);
        
        // Verify config values
        assertEquals(SERVER_NAME, config.getServerName());
        assertEquals(SERVER_URL, config.getServerPath());
        assertEquals("sse", config.getClientType());
    }
}