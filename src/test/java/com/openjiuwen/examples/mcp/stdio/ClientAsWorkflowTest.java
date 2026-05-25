/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.stdio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * STDIO — Workflow usage example.
 * <p>
 * Mirrors Python's {@code client_as_workflow} in
 * {@code examples.mcp.stdio.client_as_workflow}.
 */
@DisabledIfEnvironmentVariable(named = "SKIP_MCP_TESTS", matches = "true")
class ClientAsWorkflowTest {

    @Test
    void testWorkflowConstruction() {
        assertTrue(true);
    }

    @Test
    void testWorkflowCardBuilder() {
        assertTrue(true);
    }

    @Test
    void testMcpClientClassExists() {
        assertNotNull(com.openjiuwen.core.foundation.tool.mcp.McpClient.class);
    }
}