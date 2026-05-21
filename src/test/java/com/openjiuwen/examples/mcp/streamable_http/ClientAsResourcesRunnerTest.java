/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.streamable_http;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.tool.ToolComponentConfig;
import com.openjiuwen.core.workflow.components.flow.StartComponent;
import com.openjiuwen.core.workflow.components.tool.ToolComponent;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Streamable HTTP — Runner / ResourceMgr usage example.
 * <p>
 * Mirrors Python's {@code client_as_resources_runner} in
 * {@code examples.mcp.streamable_http.client_as_resources_runner}.
 */
class ClientAsResourcesRunnerTest {

    private static final Logger logger = Loggers.getLogger(ClientAsResourcesRunnerTest.class);
    private static final String SERVER_NAME = "notes-streamable-http-server";
    private static final String SERVER_ID = "streamable-http-notes-server-01";
    private static final String WORKFLOW_ID = "streamable_http_notes_workflow";

    @Test
    void demonstrateWorkflowConstruction() {
        Workflow workflow = new Workflow(WorkflowCard.builder()
                .id(WORKFLOW_ID)
                .name("Streamable HTTP Notes Workflow")
                .version("1.0.0")
                .build());

        Start start = new StartComponent();
        workflow.setStartComp("start", start, Map.of("content", "${content}"));

        ToolComponent toolComp = new ToolComponent(new ToolComponentConfig());
        workflow.addWorkflowComp("tool", toolComp, Map.of("content", "${start.content}"));

        assertNotNull(workflow);
        logger.info("Workflow '{}' constructed for Runner integration", WORKFLOW_ID);
    }

    @Test
    void serverConfigCanBeCreated() {
        McpServerConfig config = McpServerConfig.builder()
                .serverId(SERVER_ID)
                .serverName(SERVER_NAME)
                .serverPath("http://127.0.0.1:3002/mcp")
                .clientType("streamable-http")
                .build();

        assertNotNull(config);
        assertEquals("streamable-http", config.getClientType());
    }
}