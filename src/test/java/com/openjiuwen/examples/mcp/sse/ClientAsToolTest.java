/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.sse;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpTool;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.mcp.client.SseClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SSE — MCPTool usage example.
 * <p>
 * Demonstrates using MCPTool (com.openjiuwen.core.foundation.tool.mcp.McpTool)
 * instead of calling the transport client directly.
 * <p>
 * MCPTool wraps an McpClient + McpToolCard and exposes the standard Tool.invoke()
 * interface, making MCP tools interchangeable with any other openjiuwen Tool.
 * <p>
 * Mirrors Python's {@code client_as_tool} in
 * {@code examples.mcp.sse.client_as_tool}.
 * <p>
 * Prerequisites:
 * <ul>
 *   <li>Start the server first: run server.py</li>
 *   <li>Run this test</li>
 * </ul>
 */
class ClientAsToolTest {

    private static final Logger logger = Loggers.getLogger(ClientAsToolTest.class);
    private static final String SERVER_URL = "http://127.0.0.1:3001/sse";
    private static final String SERVER_NAME = "calculator-sse-server";

    /**
     * Demonstrates MCPTool usage with an SSE MCP server.
     * <p>
     * This test is disabled by default as it requires a running MCP server.
     * Enable by setting environment variable MCP_SERVER_RUNNING=true.
     */
    @Test
    @DisabledIfEnvironmentVariable(named = "MCP_SERVER_RUNNING", matches = "false", disabledReason = "Requires running MCP server")
    void demonstrateMcpToolUsage() throws Exception {
        // ── 1. Create and connect the transport client ────────────────────────────
        McpServerConfig config = McpServerConfig.builder()
                .serverName(SERVER_NAME)
                .serverPath(SERVER_URL)
                .clientType("sse")
                .build();
        McpClient client = new SseClient(config);

        logger.info("Connecting to SSE server at {} ...", SERVER_URL);
        boolean connected = client.connect();
        if (!connected) {
            logger.info("Failed to connect. Make sure server.py is running.");
            return;
        }
        logger.info("Connected.\n");

        // ── 2. Discover tools — each McpToolCard describes one server tool ────────
        List<Object> toolCards = client.listTools();
        logger.info("Discovered {} tool(s): {}", toolCards.size(), 
                toolCards.stream().map(t -> ((McpToolCard) t).getName()).toList());

        // ── 3. Wrap every card in MCPTool ─────────────────────────────────────────
        //   MCPTool(mcp_client, tool_info) — the client is shared across all tools.
        //   MCPTool.invoke() delegates to client.callTool() internally.
        Map<String, McpTool> tools = new HashMap<>();
        for (Object card : toolCards) {
            McpToolCard toolCard = (McpToolCard) card;
            tools.put(toolCard.getName(), new McpTool(client, toolCard));
        }

        // ── 4. Invoke tools via the standard Tool.invoke() interface ─────────────
        //   invoke() always returns {"result": <value>}

        Map<String, Object> result = (Map<String, Object>) tools.get("add").invoke(Map.of("a", 10, "b", 3));
        logger.info("add(10, 3)       → {}", result);

        result = (Map<String, Object>) tools.get("subtract").invoke(Map.of("a", 10, "b", 3));
        logger.info("subtract(10, 3)  → {}", result);

        result = (Map<String, Object>) tools.get("multiply").invoke(Map.of("a", 10, "b", 3));
        logger.info("multiply(10, 3)  → {}", result);

        result = (Map<String, Object>) tools.get("divide").invoke(Map.of("a", 10, "b", 4));
        logger.info("divide(10, 4)    → {}", result);

        result = (Map<String, Object>) tools.get("divide").invoke(Map.of("a", 10, "b", 0));
        logger.info("divide(10, 0)    → {}", result);  // error message

        result = (Map<String, Object>) tools.get("power").invoke(Map.of("base", 2, "exponent", 10));
        logger.info("power(2, 10)     → {}", result);

        logger.info("");

        // ── 5. Disconnect ─────────────────────────────────────────────────────────
        client.disconnect();
        logger.info("Disconnected.");
    }

    /**
     * Basic connectivity test without requiring a running server.
     * Verifies that McpTool and SseClient can be instantiated correctly.
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
        
        McpToolCard card = McpToolCard.builder()
                .name("test_tool")
                .description("A test tool")
                .build();
        
        McpTool tool = new McpTool(client, card);
        assertNotNull(tool);
        assertEquals("test_tool", tool.getCard().getName());
    }
}