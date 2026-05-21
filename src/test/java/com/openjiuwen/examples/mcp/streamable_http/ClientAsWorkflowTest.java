/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.streamable_http;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpTool;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.mcp.client.StreamableHttpClient;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.EndConfig;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.tool.ToolComponentConfig;
import com.openjiuwen.core.workflow.components.flow.EndComponent;
import com.openjiuwen.core.workflow.components.flow.StartComponent;
import com.openjiuwen.core.workflow.components.tool.ToolComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Streamable HTTP — Workflow usage example.
 * <p>
 * Mirrors Python's {@code client_as_workflow} in
 * {@code examples.mcp.streamable_http.client_as_workflow}.
 */
class ClientAsWorkflowTest {

    private static final Logger logger = Loggers.getLogger(ClientAsWorkflowTest.class);
    private static final String SERVER_URL = "http://127.0.0.1:3002/mcp";
    private static final String SERVER_NAME = "notes-streamable-http-server";

    private Workflow buildWorkflow(McpTool addNoteTool) {
        Workflow workflow = new Workflow(WorkflowCard.builder()
                .id("streamable_http_notes_workflow")
                .name("Streamable HTTP Notes Workflow")
                .version("1.0.0")
                .build());

        Start start = new StartComponent();
        workflow.setStartComp("start", start, Map.of("content", "${content}"));

        ToolComponent toolComp = new ToolComponent(new ToolComponentConfig());
        toolComp.bindTool(addNoteTool);
        workflow.addWorkflowComp("tool", toolComp, Map.of("content", "${start.content}"));

        EndConfig endConfig = EndConfig.builder()
                .responseTemplate("Added note: {{result}}")
                .build();
        End end = new EndComponent(endConfig);
        workflow.setEndComp("end", end, Map.of("result", "${tool.data}"));

        workflow.addConnection("start", "tool");
        workflow.addConnection("tool", "end");

        return workflow;
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "MCP_SERVER_RUNNING", matches = "false", disabledReason = "Requires running MCP server")
    void demonstrateWorkflowWithMcpTool() throws Exception {
        McpServerConfig config = McpServerConfig.builder()
                .serverName(SERVER_NAME)
                .serverPath(SERVER_URL)
                .clientType("streamable-http")
                .build();
        McpClient client = new StreamableHttpClient(config);

        logger.info("Connecting to server...");
        boolean connected = client.connect(1, 30.0f);
        if (!connected) {
            logger.info("Failed to connect.");
            return;
        }

        List<Object> toolCards = client.listTools();
        Map<String, McpTool> mcpTools = new HashMap<>();
        for (Object card : toolCards) {
            McpToolCard toolCard = (McpToolCard) card;
            mcpTools.put(toolCard.getName(), new McpTool(client, toolCard));
        }

        Workflow workflow = buildWorkflow(mcpTools.get("add_note"));

        WorkflowSession session = new WorkflowSession("streamable_http_session");
        Map<String, Object> inputs = Map.of("content", "Test note from workflow");
        Object result = workflow.invoke(inputs, session);
        logger.info("Workflow output: {}", result);

        client.disconnect();
    }

    @Test
    void workflowCanBeConstructed() {
        Workflow workflow = new Workflow(WorkflowCard.builder()
                .id("test_workflow")
                .name("Test Workflow")
                .version("1.0.0")
                .build());
        assertNotNull(workflow);
    }
}