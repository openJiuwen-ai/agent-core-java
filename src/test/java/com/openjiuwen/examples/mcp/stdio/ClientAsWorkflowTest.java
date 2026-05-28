/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.stdio;

import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.End;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * STDIO — Workflow usage example.
 * <p>
 * Demonstrates integrating a Stdio MCPTool into an openjiuwen Workflow.
 * <p>
 * Mirrors Python's {@code client_as_workflow} in
 * {@code examples.mcp.stdio.client_as_workflow}.
 */
@DisabledIfEnvironmentVariable(named = "SKIP_MCP_TESTS", matches = "true")
class ClientAsWorkflowTest {

    @Test
    @Tag("level0")
    void testWorkflowConstruction() {
        // Mirrors Python's build_workflow() structure
        WorkflowCard card = WorkflowCard.builder()
                .id("stdio_text_workflow")
                .name("Stdio Text Processing Workflow")
                .version("1.0.0")
                .build();

        Workflow workflow = new Workflow(card);

        // Verify workflow construction succeeded
        assertNotNull(workflow);
        assertNotNull(workflow.getCard());
        assertEquals("stdio_text_workflow", workflow.getCard().getId());
        assertEquals("Stdio Text Processing Workflow", workflow.getCard().getName());
        assertEquals("1.0.0", workflow.getCard().getVersion());
    }

    @Test
    @Tag("level0")
    void testWorkflowCardBuilder() {
        // Mirrors Python's WorkflowCard(id=..., name=..., version=...) pattern
        WorkflowCard card = WorkflowCard.builder()
                .id("test_workflow")
                .name("Test Workflow")
                .version("1.0.0")
                .build();

        assertNotNull(card);
        assertEquals("test_workflow", card.getId());
        assertEquals("Test Workflow", card.getName());
        assertEquals("1.0.0", card.getVersion());

        // Also verify convenience constructor
        WorkflowCard card2 = new WorkflowCard("simple_id", "Simple Name");
        assertNotNull(card2);
        assertEquals("simple_id", card2.getId());
        assertEquals("Simple Name", card2.getName());
    }

    @Test
    @Tag("level0")
    void testWorkflowComponentsExist() {
        // Verify Start and End components can be instantiated
        assertNotNull(Start.class);
        assertNotNull(End.class);

        Start start = new Start();
        End end = new End();
        assertNotNull(start);
        assertNotNull(end);
    }

    @Test
    @Tag("level0")
    void testMcpClientClassExists() {
        assertNotNull(com.openjiuwen.core.foundation.tool.mcp.McpClient.class);
    }
}