/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.streamable_http;

import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpTool;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.mcp.client.StreamableHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Streamable HTTP — MCPTool usage example.
 * <p>
 * Demonstrates using MCPTool with the Streamable HTTP transport.
 * Each note-taking endpoint is wrapped in an MCPTool and invoked via
 * the standard Tool.invoke() interface.
 * <p>
 * Mirrors Python's {@code client_as_tool} in
 * {@code examples.mcp.streamable_http.client_as_tool}.
 */
class ClientAsToolTest {

    private static final Logger logger = LoggerFactory.getLogger(ClientAsToolTest.class);
    private static final String SERVER_URL = "http://127.0.0.1:3002/mcp";
    private static final String SERVER_NAME = "notes-streamable-http-server";

    @Test
    @DisabledIfEnvironmentVariable(named = "MCP_SERVER_RUNNING", matches = "false", disabledReason = "Requires running MCP server")
    void demonstrateMcpToolUsage() throws Exception {
        McpServerConfig config = McpServerConfig.builder()
                .serverName(SERVER_NAME)
                .serverPath(SERVER_URL)
                .clientType("streamable-http")
                .build();
        McpClient client = new StreamableHttpClient(config);

        logger.info("Connecting to Streamable HTTP server at {} ...", SERVER_URL);
        boolean connected = client.connect(1, 30.0f);
        if (!connected) {
            logger.info("Failed to connect. Make sure server.py is running.");
            return;
        }
        logger.info("Connected.\n");

        List<Object> toolCards = client.listTools();
        logger.info("Discovered {} tool(s): {}", toolCards.size(),
                toolCards.stream().map(t -> ((McpToolCard) t).getName()).toList());

        Map<String, McpTool> tools = new HashMap<>();
        for (Object card : toolCards) {
            McpToolCard toolCard = (McpToolCard) card;
            tools.put(toolCard.getName(), new McpTool(client, toolCard));
        }

        Map<String, Object> result = (Map<String, Object>) tools.get("list_notes").invoke(Map.of());
        logger.info("list_notes (empty)          → {}", result);

        result = (Map<String, Object>) tools.get("add_note").invoke(Map.of("content", "Buy groceries"));
        logger.info("add_note 'Buy groceries'    → {}", result);

        result = (Map<String, Object>) tools.get("add_note").invoke(Map.of("content", "Call the dentist"));
        logger.info("add_note 'Call dentist'     → {}", result);

        result = (Map<String, Object>) tools.get("add_note").invoke(Map.of("content", "Finish quarterly report"));
        logger.info("add_note 'Quarterly report' → {}", result);

        result = (Map<String, Object>) tools.get("list_notes").invoke(Map.of());
        logger.info("list_notes (3 items)        → {}", result);

        result = (Map<String, Object>) tools.get("get_note").invoke(Map.of("note_id", 1));
        logger.info("get_note(1)                 → {}", result);

        client.disconnect();
        logger.info("Disconnected.");
    }

    @Test
    void clientCanBeInstantiated() {
        McpServerConfig config = McpServerConfig.builder()
                .serverName(SERVER_NAME)
                .serverPath(SERVER_URL)
                .clientType("streamable-http")
                .build();
        
        McpClient client = new StreamableHttpClient(config);
        assertNotNull(client);
    }
}