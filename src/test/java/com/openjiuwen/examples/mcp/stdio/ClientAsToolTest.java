/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.stdio;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpTool;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.mcp.client.StdioClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stdio — MCPTool usage example.
 * <p>
 * Demonstrates using MCPTool with the Stdio transport.
 * The client launches server.py as a subprocess; MCPTool wraps each discovered
 * tool card so it can be invoked via the standard Tool.invoke() interface.
 * <p>
 * Mirrors Python's {@code client_as_tool} in
 * {@code examples.mcp.stdio.client_as_tool}.
 * <p>
 * Prerequisites:
 * <ul>
 *   <li>No separate server process needed — the subprocess is managed automatically.</li>
 * </ul>
 */
class ClientAsToolTest {

    private static final Logger logger = Loggers.getLogger(ClientAsToolTest.class);
    private static final String SERVER_NAME = "text-processor-stdio-server";

    /**
     * Demonstrates MCPTool usage with a Stdio MCP server.
     * <p>
     * This test is disabled by default as it requires Python environment.
     * Enable by setting environment variable PYTHON_MCP_SERVER_AVAILABLE=true.
     */
    @Test
    @DisabledIfEnvironmentVariable(named = "PYTHON_MCP_SERVER_AVAILABLE", matches = "false", disabledReason = "Requires Python MCP server environment")
    void demonstrateMcpToolUsage() throws Exception {
        Path serverScript = Paths.get("examples/mcp/stdio/server.py");
        
        // ── 1. Create and connect the transport client ────────────────────────────
        McpServerConfig config = McpServerConfig.builder()
                .serverName(SERVER_NAME)
                .serverPath("")
                .clientType("stdio")
                .params(Map.of(
                        "command", "python",
                        "args", List.of(serverScript.toString()),
                        "cwd", serverScript.getParent().toString(),
                        "encoding_error_handler", "strict"
                ))
                .build();
        McpClient client = new StdioClient(config);

        logger.info("Launching Stdio server: python {}", serverScript);
        boolean connected = client.connect();
        if (!connected) {
            logger.info("Failed to start server subprocess.");
            return;
        }
        logger.info("Server started.\n");

        // ── 2. Discover tools ─────────────────────────────────────────────────────
        List<Object> toolCards = client.listTools();
        logger.info("Discovered {} tool(s): {}", toolCards.size(),
                toolCards.stream().map(t -> ((McpToolCard) t).getName()).toList());

        // ── 3. Wrap every card in MCPTool ─────────────────────────────────────────
        Map<String, McpTool> tools = new HashMap<>();
        for (Object card : toolCards) {
            McpToolCard toolCard = (McpToolCard) card;
            tools.put(toolCard.getName(), new McpTool(client, toolCard));
        }

        // ── 4. Invoke tools via the standard Tool.invoke() interface ─────────────
        String sample = "The quick brown fox jumps over the lazy dog";
        logger.info("Sample text: '{}'\n", sample);

        Map<String, Object> result = (Map<String, Object>) tools.get("word_count").invoke(Map.of("text", sample));
        logger.info("word_count     → {}", result);

        result = (Map<String, Object>) tools.get("char_count").invoke(Map.of("text", sample));
        logger.info("char_count     → {}", result);

        result = (Map<String, Object>) tools.get("reverse_text").invoke(Map.of("text", sample));
        logger.info("reverse_text   → {}", result);

        result = (Map<String, Object>) tools.get("to_uppercase").invoke(Map.of("text", sample));
        logger.info("to_uppercase   → {}", result);

        result = (Map<String, Object>) tools.get("to_lowercase").invoke(Map.of("text", sample));
        logger.info("to_lowercase   → {}", result);

        logger.info("");

        // ── 5. Disconnect ─────────────────────────────────────────────────────────
        client.disconnect();
        logger.info("Disconnected.");
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
        assertEquals("stdio", config.getClientType());
    }
}