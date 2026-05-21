/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.sse;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpTool;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.mcp.client.SseClient;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SSE — Workflow usage example.
 * <p>
 * Demonstrates integrating an SSE MCPTool into an openjiuwen Workflow.
 * <p>
 * Workflow layout:
 * <pre>
 *     Start(a, b) → ToolComponent[add] → End
 * </pre>
 * <p>
 * The ToolComponent is bound to the 'add' MCPTool discovered from the SSE server.
 * The workflow is invoked with {"a": 7, "b": 3} and prints the sum.
 * <p>
 * Mirrors Python's {@code client_as_workflow} in
 * {@code examples.mcp.sse.client_as_workflow}.
 * <p>
 * Prerequisites:
 * <ul>
 *   <li>Start the server first: run server.py</li>
 *   <li>Run this test</li>
 * </ul>
 */
class ClientAsWorkflowTest {

    private static final Logger logger = Loggers.getLogger(ClientAsWorkflowTest.class);
    private static final String SERVER_URL = "http://127.0.0.1:3001/sse";
    private static final String SERVER_NAME = "calculator-sse-server";

    /**
     * Construct a workflow that calls the 'add' calculator tool.
     */
    private Workflow buildWorkflow(McpTool addTool) {
        Workflow workflow = new Workflow(WorkflowCard.builder()
                .id("sse_calculator_workflow")
                .name("SSE Calculator Workflow")
                .version("1.0.0")
                .build());

        // ── Start: expose 'a' and 'b' from the invoke input ──────────────────────
        Start start = new StartComponent();
        workflow.setStartComp("start", start, Map.of("a", "${a}", "b", "${b}"));

        // ── ToolComponent: bound to the SSE 'add' MCPTool ─────────────────────────
        //   inputs_schema maps workflow state → tool parameters.
        //   MCPTool.invoke() receives {"a": <value>, "b": <value>} and forwards them
        //   to the MCP server's 'add' tool.
        ToolComponent toolComp = new ToolComponent(new ToolComponentConfig());
        toolComp.bindTool(addTool);
        workflow.addWorkflowComp("tool", toolComp, Map.of("a", "${start.a}", "b", "${start.b}"));

        // ── End: display the tool result ──────────────────────────────────────────
        //   ToolComponent output contains a 'data' field with the raw tool result.
        EndConfig endConfig = EndConfig.builder()
                .responseTemplate("add(a, b) = {{result}}")
                .build();
        End end = new EndComponent(endConfig);
        workflow.setEndComp("end", end, Map.of("result", "${tool.data}"));

        // ── Connections ───────────────────────────────────────────────────────────
        workflow.addConnection("start", "tool");
        workflow.addConnection("tool", "end");

        return workflow;
    }

    /**
     * Demonstrates workflow integration with MCPTool.
     * <p>
     * This test is disabled by default as it requires a running MCP server.
     * Enable by setting environment variable MCP_SERVER_RUNNING=true.
     */
    @Test
    @DisabledIfEnvironmentVariable(named = "MCP_SERVER_RUNNING", matches = "false", disabledReason = "Requires running MCP server")
    void demonstrateWorkflowWithMcpTool() throws Exception {
        // ── 1. Connect to SSE server ───────────────────────────────────────────────
        McpServerConfig config = McpServerConfig.builder()
                .serverName(SERVER_NAME)
                .serverPath(SERVER_URL)
                .clientType("sse")
                .build();
        McpClient client = new SseClient(config);

        logger.info("Connecting to SSE server at {} ...", SERVER_URL);
        boolean connected = client.connect();
        if (!connected) {
            logger.info("Failed to connect. Make sure server.py is running.");
            return;
        }
        logger.info("Connected.\n");

        // ── 2. Discover tools and wrap in MCPTool ─────────────────────────────────
        List<Object> toolCards = client.listTools();
        logger.info("Discovered {} tool(s): {}", toolCards.size(),
                toolCards.stream().map(t -> ((McpToolCard) t).getName()).toList());

        Map<String, McpTool> mcpTools = new HashMap<>();
        for (Object card : toolCards) {
            McpToolCard toolCard = (McpToolCard) card;
            mcpTools.put(toolCard.getName(), new McpTool(client, toolCard));
        }

        // ── 3. Build workflow with the 'add' tool ─────────────────────────────────
        Workflow workflow = buildWorkflow(mcpTools.get("add"));

        // ── 4. Invoke the workflow ─────────────────────────────────────────────────
        WorkflowSessionApi session = new WorkflowSession("sse_workflow_session");
        Map<String, Object> inputs = Map.of("a", 7, "b", 3);
        logger.info("Invoking workflow with inputs: {}", inputs);
        
        Object result = workflow.invoke(inputs, session);
        logger.info("Workflow output: {}\n", result);

        // ── 5. Disconnect ─────────────────────────────────────────────────────────
        client.disconnect();
        logger.info("Disconnected.");
    }

    /**
     * Basic workflow construction test without requiring a running server.
     * Verifies that workflow components can be instantiated and connected correctly.
     */
    @Test
    void workflowCanBeConstructed() {
        Workflow workflow = new Workflow(WorkflowCard.builder()
                .id("test_workflow")
                .name("Test Workflow")
                .version("1.0.0")
                .build());

        assertNotNull(workflow);
        
        Start start = new StartComponent();
        workflow.setStartComp("start", start, Map.of("a", "${a}", "b", "${b}"));
        
        End end = new EndComponent();
        workflow.setEndComp("end", end);
        
        workflow.addConnection("start", "end");
        
        assertEquals("test_workflow", workflow.getCard().getId());
    }
}