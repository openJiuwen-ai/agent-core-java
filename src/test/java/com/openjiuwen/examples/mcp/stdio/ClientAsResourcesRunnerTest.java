/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.stdio;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.tool.ToolComponentConfig;
import com.openjiuwen.core.workflow.components.flow.StartComponent;
import com.openjiuwen.core.workflow.components.tool.ToolComponent;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stdio — Runner / ResourceMgr usage example.
 * <p>
 * Demonstrates registering a Stdio MCP server (subprocess), invoking tools, and
 * running a workflow entirely through the Runner and ResourceMgr.
 * <p>
 * Mirrors Python's {@code client_as_resources_runner} in
 * {@code examples.mcp.stdio.client_as_resources_runner}.
 * <p>
 * Note: The full Runner/ResourceMgr integration requires Python environment.
 * This test demonstrates the workflow construction pattern.
 */
class ClientAsResourcesRunnerTest {

    private static final Logger logger = LoggerFactory.getLogger(ClientAsResourcesRunnerTest.class);
    private static final String SERVER_NAME = "text-processor-stdio-server";
    private static final String SERVER_ID = "stdio-text-server-01";
    private static final String WORKFLOW_ID = "stdio_text_workflow";

    private static LocalFunction wordCountTool() {
        ToolCard card = ToolCard.builder()
                .id("word_count")
                .name("word_count")
                .description("Count the number of words in the given text.")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of("text", Map.of("type", "string")),
                        "required", java.util.List.of("text")
                ))
                .build();
        return new LocalFunction(card, inputs -> String.valueOf(inputs.getOrDefault("text", ""))
                .trim()
                .isEmpty()
                ? 0
                : String.valueOf(inputs.get("text")).trim().split("\\s+").length);
    }

    /**
     * Demonstrates workflow construction for Runner integration.
     * <p>
     * Full Runner integration requires:
     * <ul>
     *   <li>Runner.resourceMgr.addMcpServer() with client_type="stdio"</li>
     *   <li>Runner.resourceMgr.getMcpTool() for tool discovery</li>
     *   <li>Runner.runWorkflow() for execution</li>
     * </ul>
     */
    @Test
    void demonstrateWorkflowConstruction() {
        // Build the workflow that would be registered with Runner
        Workflow workflow = new Workflow(WorkflowCard.builder()
                .id(WORKFLOW_ID)
                .name("Stdio Text Processing Workflow")
                .version("1.0.0")
                .build());

        Start start = new StartComponent();
        workflow.setStartComp("start", start, Map.of("text", "${text}"));

        // ToolComponent is bound to the word_count tool discovered from ResourceMgr in full integration.
        ToolComponent toolComp = new ToolComponent(new ToolComponentConfig());
        toolComp.bindTool(wordCountTool());
        workflow.addWorkflowComp("tool", toolComp, Map.of("text", "${start.text}"));
        End end = new End(Map.of("response_template", "word_count = {{result}}"));
        workflow.setEndComp("end", end, Map.of("result", "${tool.data}"));
        workflow.addConnection("start", "tool");
        workflow.addConnection("tool", "end");

        assertNotNull(workflow);
        assertEquals(WORKFLOW_ID, workflow.getCard().getId());
        
        logger.info("Workflow '{}' constructed successfully", WORKFLOW_ID);
        logger.info("For full Runner integration, use:");
        logger.info("  1. Runner.resourceMgr.addMcpServer(config with client_type='stdio')");
        logger.info("  2. Runner.resourceMgr.getMcpTool(serverId, toolName)");
        logger.info("  3. Runner.runWorkflow(workflowId, inputs)");
    }

    /**
     * Demonstrates McpServerConfig for Stdio transport.
     */
    @Test
    void stdioServerConfigCanBeCreated() {
        McpServerConfig config = McpServerConfig.builder()
                .serverId(SERVER_ID)
                .serverName(SERVER_NAME)
                .serverPath("")
                .clientType("stdio")
                .params(Map.of(
                        "command", "python",
                        "args", java.util.List.of("server.py")
                ))
                .build();

        assertNotNull(config);
        assertEquals(SERVER_ID, config.getServerId());
        assertEquals(SERVER_NAME, config.getServerName());
        assertEquals("stdio", config.getClientType());
        
        logger.info("Stdio McpServerConfig created for Runner integration");
    }
}
