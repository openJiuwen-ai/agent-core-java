/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.streamable_http;

import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Streamable HTTP — Runner / ResourceMgr usage example.
 * <p>
 * Mirrors Python's {@code client_as_resources_runner} in
 * {@code examples.mcp.streamable_http.client_as_resources_runner}.
 */
@DisabledIfEnvironmentVariable(named = "SKIP_MCP_TESTS", matches = "true")
class ClientAsResourcesRunnerTest {

    @Test
    @Tag("level0")
    void demonstrateWorkflowConstruction() {
        // Mirrors Python's workflow construction pattern
        WorkflowCard card = WorkflowCard.builder()
                .id("streamable_http_workflow")
                .name("Streamable HTTP Workflow")
                .version("1.0.0")
                .build();

        Workflow workflow = new Workflow(card);
        assertNotNull(workflow);
        assertNotNull(workflow.getCard());
    }

    @Test
    @Tag("level0")
    void testWorkflowCardBuilder() {
        WorkflowCard card = WorkflowCard.builder()
                .id("test_workflow")
                .name("Test Workflow")
                .version("1.0.0")
                .build();

        assertNotNull(card);
        assertEquals("test_workflow", card.getId());
        assertEquals("Test Workflow", card.getName());
    }

    @Test
    @Tag("level0")
    void testWorkflowConstruction() {
        WorkflowCard card = new WorkflowCard("simple_id", "Simple Name");
        Workflow workflow = new Workflow(card);
        
        assertNotNull(workflow);
        assertEquals("simple_id", workflow.getCard().getId());
    }

    @Test
    @Tag("level0")
    void testMcpServerConfigBuilder() {
        McpServerConfig config = McpServerConfig.builder()
                .serverName("streamable-http-server")
                .serverPath("http://127.0.0.1:8080/mcp")
                .clientType("streamable_http")
                .build();

        assertNotNull(config);
        assertEquals("streamable-http-server", config.getServerName());
    }
}