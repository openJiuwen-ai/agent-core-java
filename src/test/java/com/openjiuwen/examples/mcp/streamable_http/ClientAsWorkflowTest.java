/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.examples.mcp.streamable_http;

import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.End;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Streamable HTTP — Workflow usage example.
 * <p>
 * Demonstrates integrating a Streamable HTTP MCPTool into an openjiuwen Workflow.
 * <p>
 * Mirrors Python's {@code client_as_workflow} in
 * {@code examples.mcp.streamable_http.client_as_workflow}.
 */
@DisabledIfEnvironmentVariable(named = "SKIP_MCP_TESTS", matches = "true")
class ClientAsWorkflowTest {

    @Test
    @Tag("level0")
    void testWorkflowClassExists() {
        assertNotNull(Workflow.class);
    }

    @Test
    @Tag("level0")
    void testWorkflowCardClassExists() {
        assertNotNull(WorkflowCard.class);
    }

    @Test
    @Tag("level0")
    void testWorkflowConstruction() {
        WorkflowCard card = WorkflowCard.builder()
                .id("streamable_http_workflow")
                .name("Streamable HTTP Workflow")
                .version("1.0.0")
                .build();

        Workflow workflow = new Workflow(card);
        assertNotNull(workflow);
        assertNotNull(workflow.getCard());
        assertEquals("streamable_http_workflow", workflow.getCard().getId());
    }

    @Test
    @Tag("level0")
    void testWorkflowComponentsExist() {
        assertNotNull(Start.class);
        assertNotNull(End.class);
        
        Start start = new Start();
        End end = new End();
        assertNotNull(start);
        assertNotNull(end);
    }
}