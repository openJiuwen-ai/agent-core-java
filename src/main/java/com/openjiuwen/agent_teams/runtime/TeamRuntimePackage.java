/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

import java.util.List;

/**
 * Package facade for agent-team runtime exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.runtime} package in
 * {@code openjiuwen/agent_teams/runtime/__init__.py}.</p>
 */
public final class TeamRuntimePackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/runtime/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "ActiveTeam",
            "ActiveTeamInfo",
            "RunAction",
            "RunActionKind",
            "RuntimeState",
            "TeamRuntimeActivation",
            "TeamRuntimeManager",
            "TeamRuntimePool",
            "TeamSessionReleaseInfo"
    );

    private TeamRuntimePackage() {
    }

    public static boolean exports(String symbol) {
        return EXPORTED_SYMBOLS.contains(symbol);
    }
}
