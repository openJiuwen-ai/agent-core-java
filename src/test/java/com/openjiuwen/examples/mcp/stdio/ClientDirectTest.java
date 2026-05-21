/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.stdio;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.mcp.client.StdioClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stdio MCP Client Example.
 * <p>
 * This client launches server.py as a subprocess (Stdio transport), communicates
 * with it over stdin/stdout, lists available tools, and calls each text-processing
 * tool to demonstrate usage.
 * <p>
 * Mirrors Python's {@code client_direct} in
 * {@code examples.mcp.stdio.client_direct}.
 * <p>
 * Prerequisites:
 * <ul>
 *   <li>No need to start the server manually — the client starts it automatically.</li>
 * </ul>
 */
class ClientDirectTest {

    private static final Logger logger = Loggers.getLogger(ClientDirectTest.class);
    private static final String SERVER_NAME = "text-processor-stdio-server";

    /**
     * Demonstrates direct Stdio MCP client usage.
     * <p>
     * This test is disabled by default as it requires Python environment.
     * Enable by setting environment variable PYTHON_MCP_SERVER_AVAILABLE=true.
     */
    @Test
    @DisabledIfEnvironmentVariable(named = "PYTHON_MCP_SERVER_AVAILABLE", matches = "false", disabledReason = "Requires Python MCP server environment")
    void demonstrateDirectClientUsage() throws Exception {
        Path serverScript = Paths.get("examples/mcp/stdio/server.py");
        
        logger.info("Launching Stdio MCP server: python {}", serverScript);

        McpServerConfig config = McpServerConfig.builder()
                .serverName(SERVER_NAME)
                .serverPath("")
                .clientType("stdio")
                .params(Map.of(
                        "command", "python",
                        "args", List.of(serverScript.toString()),
                        "env", Map.of(),
                        "cwd", serverScript.getParent().toString(),
                        "encoding_error_handler", "strict"
                ))
                .build();
        McpClient client = new StdioClient(config);

        boolean connected = client.connect();
        if (!connected) {
            logger.info("Failed to start Stdio server.");
            return;
        }

        logger.info("Stdio server started and connected!\n");

        // --- List available tools ---
        List<Object> tools = client.listTools();
        logger.info("Available tools ({}):", tools.size());
        for (Object tool : tools) {
            McpToolCard card = (McpToolCard) tool;
            logger.info("  - {}: {}", card.getName(), card.getDescription());
        }
        logger.info("");

        // --- Get info for a specific tool ---
        Object toolInfo = client.getToolInfo("word_count");
        if (toolInfo != null) {
            McpToolCard card = (McpToolCard) toolInfo;
            logger.info("Tool info for 'word_count': {} — {}", card.getName(), card.getDescription());
            logger.info("");
        }

        String sampleText = "The quick brown fox jumps over the lazy dog";
        logger.info("Sample text: '{}'\n", sampleText);

        // --- Call tools ---
        logger.info("Calling tools:");

        Object result = client.callTool("word_count", Map.of("text", sampleText));
        logger.info("  word_count     → {}", result);

        result = client.callTool("char_count", Map.of("text", sampleText));
        logger.info("  char_count     → {}", result);

        result = client.callTool("reverse_text", Map.of("text", sampleText));
        logger.info("  reverse_text   → {}", result);

        result = client.callTool("to_uppercase", Map.of("text", sampleText));
        logger.info("  to_uppercase   → {}", result);

        result = client.callTool("to_lowercase", Map.of("text", sampleText));
        logger.info("  to_lowercase   → {}", result);

        logger.info("");

        // --- Disconnect ---
        client.disconnect();
        logger.info("Disconnected from Stdio server.");
    }

    /**
     * Basic client instantiation test without requiring Python environment.
     */
    @Test
    void clientCanBeInstantiated() {
        McpServerConfig config = McpServerConfig.builder()
                .serverName(SERVER_NAME)
                .serverPath("")
                .clientType("stdio")
                .params(Map.of(
                        "command", "python",
                        "args", List.of("server.py")
                ))
                .build();
        
        McpClient client = new StdioClient(config);
        assertNotNull(client);
    }
}