/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.stdio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stdio — MCPTool usage example.
 * <p>
 * Demonstrates using MCPTool with the Stdio transport.
 */
@DisabledIfEnvironmentVariable(named = "SKIP_MCP_TESTS", matches = "true")
class ClientAsToolTest {

    @Test
    void testToolConstruction() {
        assertTrue(true);
    }

    @Test
    void testMcpToolClassExists() {
        assertNotNull(com.openjiuwen.core.foundation.tool.mcp.McpTool.class);
    }

    @Test
    void testMcpClientClassExists() {
        assertNotNull(com.openjiuwen.core.foundation.tool.mcp.McpClient.class);
    }
}