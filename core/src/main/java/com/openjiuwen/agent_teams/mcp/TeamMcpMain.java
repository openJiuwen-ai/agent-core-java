/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.mcp;

/**
 * Command-line entry point for the team-member MCP server.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.mcp.__main__} in
 * {@code openjiuwen/agent_teams/mcp/__main__.py}.</p>
 */
public final class TeamMcpMain {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/mcp/__main__.py";

    private TeamMcpMain() {
    }

    public static void main(String[] args) {
        TeamMcpServer.main(args);
    }
}
