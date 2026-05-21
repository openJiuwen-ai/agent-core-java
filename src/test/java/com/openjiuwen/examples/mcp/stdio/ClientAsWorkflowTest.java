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
import com.openjiuwen.core.session.WorkflowSessionApi;
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stdio — Workflow usage example.
 * <p>
 * Demonstrates integrating a Stdio MCPTool into an openjiuwen Workflow.
 * <p>
 * Workflow layout:
 * <pre>
 *     Start(text) → ToolComponent[word_count] → End
 * </pre>
 * <p>
 * The StdioClient launches server.py as a subprocess automatically.
 * The ToolComponent is bound to the 'word_count' MCPTool.
 * <p>
 * Mirrors Python's {@code client_as_workflow} in
 * {@code examples.mcp.stdio.client_as_workflow}.
 */
class ClientAsWorkflowTest {

    private static final Logger logger = Loggers.getLogger(ClientAsWorkflowTest.class);
    private static final String SERVER_NAME = "text-processor-stdio-server";

    private Workflow buildWorkflow(McpTool wordCountTool) {
        Workflow workflow = new Workflow(WorkflowCard.builder()
                .id("stdio_text_workflow")
                .name("Stdio Text Processing Workflow")
                .version("1.0.0")
                .build());

        Start start = new StartComponent();
        workflow.setStartComp("start", start, Map.of("text", "${text}"));

        ToolComponent toolComp = new ToolComponent(new ToolComponentConfig());
        toolComp.bindTool(wordCountTool);
        workflow.addWorkflowComp("tool", toolComp, Map.of("text", "${start.text}"));

        EndConfig endConfig = EndConfig.builder()
                .responseTemplate("Word count: {{result}}")
                .build();
        End end = new EndComponent(endConfig);
        workflow.setEndComp("end", end, Map.of("result", "${tool.data}"));

        workflow.addConnection("start", "tool");
        workflow.addConnection("tool", "end");

        return workflow;
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "PYTHON_MCP_SERVER_AVAILABLE", matches = "false", disabledReason = "Requires Python MCP server environment")
    void demonstrateWorkflowWithMcpTool() throws Exception {
        Path serverScript = Paths.get("examples/mcp/stdio/server.py");

        McpServerConfig config = McpServerConfig.builder()
                .serverName(SERVER_NAME)
                .serverPath("")
                .clientType("stdio")
                .params(Map.of(
                        "command", "python",
                        "args", List.of(serverScript.toString()),
                        "cwd", serverScript.getParent().toString()
                ))
                .build();
        McpClient client = new StdioClient(config);

        logger.info("Launching Stdio server...");
        boolean connected = client.connect();
        if (!connected) {
            logger.info("Failed to start server.");
            return;
        }
        logger.info("Server started.\n");

        List<Object> toolCards = client.listTools();
        logger.info("Discovered {} tool(s)", toolCards.size());

        Map<String, McpTool> mcpTools = new HashMap<>();
        for (Object card : toolCards) {
            McpToolCard toolCard = (McpToolCard) card;
            mcpTools.put(toolCard.getName(), new McpTool(client, toolCard));
        }

        Workflow workflow = buildWorkflow(mcpTools.get("word_count"));

        WorkflowSessionApi session = new WorkflowSession("stdio_workflow_session");
        Map<String, Object> inputs = Map.of("text", "Hello world from Stdio MCP");
        logger.info("Invoking workflow with inputs: {}", inputs);

        Object result = workflow.invoke(inputs, session);
        logger.info("Workflow output: {}\n", result);

        client.disconnect();
        logger.info("Disconnected.");
    }

    @Test
    void workflowCanBeConstructed() {
        Workflow workflow = new Workflow(WorkflowCard.builder()
                .id("test_workflow")
                .name("Test Workflow")
                .version("1.0.0")
                .build());

        assertNotNull(workflow);
        assertEquals("test_workflow", workflow.getCard().getId());
    }
}