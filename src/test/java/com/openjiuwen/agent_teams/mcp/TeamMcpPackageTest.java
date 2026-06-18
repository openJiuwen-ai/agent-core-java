/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.agent_teams.mcp} in
 * {@code openjiuwen/agent_teams/mcp/__init__.py}.
 */
class TeamMcpPackageTest {

    @Test
    void exportsBuildServerAndMainInPythonOrder() {
        assertEquals("openjiuwen/agent_teams/mcp/__init__.py", TeamMcpPackage.PYTHON_MODULE);
        assertEquals(List.of("build_server", "main"), TeamMcpPackage.ALL);
    }

    @Test
    void buildServerDelegatesToServerModule() {
        TeamMcpServer server = TeamMcpPackage.buildServer(
                () -> CompletableFuture.<TeamMcpServer.TeamClientFacade>completedFuture(null),
                "reviewer"
        );

        List<String> tools = server.listTools().stream().map(TeamMcpServer.TeamMcpTool::name).toList();
        assertTrue(tools.contains(TeamMcpServer.TOOL_READ_INBOX));
        assertTrue(tools.contains(TeamMcpServer.TOOL_SEND_MESSAGE));
    }
}
