/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import java.util.List;

/**
 * Package exports for the agent teams CLI module.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.cli} module in
 * {@code openjiuwen/agent_teams/cli/__init__.py}.</p>
 */
public final class AgentTeamsCliPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/cli/__init__.py";
    public static final String DESCRIPTION = "Interactive CLI for the agent_teams runtime.";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "SpecEntry",
            "SpecRegistry",
            "TeamCli",
            "load_spec_yaml",
            "run_team_cli"
    );

    private AgentTeamsCliPackage() {
    }
}
