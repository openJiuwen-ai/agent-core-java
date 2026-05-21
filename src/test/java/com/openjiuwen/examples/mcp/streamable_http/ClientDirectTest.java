/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.streamable_http;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.mcp.client.StreamableHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Streamable HTTP MCP Client Example.
 * <p>
 * This client connects to the Streamable HTTP MCP server (server.py), lists
 * available tools, and exercises the note-taking API.
 * <p>
 * Mirrors Python's {@code client_direct} in
 * {@code examples.mcp.streamable_http.client_direct}.
 */
class ClientDirectTest {

    private static final Logger logger = Loggers.getLogger(ClientDirectTest.class);
    private static final String SERVER_URL = "http://127.0.0.1:3002/mcp";
    private static final String SERVER_NAME = "notes-streamable-http-server";

    @Test
    @DisabledIfEnvironmentVariable(named = "MCP_SERVER_RUNNING", matches = "false", disabledReason = "Requires running MCP server")
    void demonstrateDirectClientUsage() throws Exception {
        logger.info("Connecting to Streamable HTTP MCP server at {} ...", SERVER_URL);
        
        McpServerConfig config = McpServerConfig.builder()
                .serverName(SERVER_NAME)
                .serverPath(SERVER_URL)
                .clientType("streamable-http")
                .build();
        McpClient client = new StreamableHttpClient(config);

        boolean connected = client.connect(1, 30.0f);
        if (!connected) {
            logger.info("Failed to connect. Make sure server.py is running.");
            return;
        }

        logger.info("Connected successfully!\n");

        List<Object> tools = client.listTools();
        logger.info("Available tools ({}):", tools.size());
        for (Object tool : tools) {
            McpToolCard card = (McpToolCard) tool;
            logger.info("  - {}: {}", card.getName(), card.getDescription());
        }
        logger.info("");

        Object toolInfo = client.getToolInfo("add_note");
        if (toolInfo != null) {
            McpToolCard card = (McpToolCard) toolInfo;
            logger.info("Tool info for 'add_note': {} — {}", card.getName(), card.getDescription());
            logger.info("");
        }

        logger.info("Calling tools:");

        Object result = client.callTool("list_notes", Map.of());
        logger.info("  list_notes (empty):      {}", result);

        result = client.callTool("add_note", Map.of("content", "Buy groceries"));
        logger.info("  add_note 'Buy groceries': {}", result);

        result = client.callTool("add_note", Map.of("content", "Call the dentist"));
        logger.info("  add_note 'Call dentist':  {}", result);

        result = client.callTool("add_note", Map.of("content", "Finish quarterly report"));
        logger.info("  add_note 'Quarterly':     {}", result);

        result = client.callTool("list_notes", Map.of());
        logger.info("  list_notes (3 items):    {}", result);

        result = client.callTool("get_note", Map.of("note_id", 1));
        logger.info("  get_note(1):             {}", result);

        logger.info("");

        client.disconnect();
        logger.info("Disconnected from Streamable HTTP server.");
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