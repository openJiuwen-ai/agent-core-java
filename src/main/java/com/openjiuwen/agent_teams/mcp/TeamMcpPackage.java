/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.mcp;

import java.util.List;

/**
 * Public package facade for the team MCP server module.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.mcp} in
 * {@code openjiuwen/agent_teams/mcp/__init__.py}.</p>
 */
public final class TeamMcpPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/mcp/__init__.py";
    public static final List<String> ALL = List.of("build_server", "main");

    private TeamMcpPackage() {
    }

    public static TeamMcpServer buildServer() {
        return TeamMcpServer.buildServer();
    }

    public static TeamMcpServer buildServer(TeamMcpServer.ClientFactory clientFactory) {
        return TeamMcpServer.buildServer(clientFactory);
    }

    public static TeamMcpServer buildServer(TeamMcpServer.ClientFactory clientFactory, String role) {
        return TeamMcpServer.buildServer(clientFactory, role);
    }

    public static void main(String[] args) {
        TeamMcpServer.main(args);
    }
}
