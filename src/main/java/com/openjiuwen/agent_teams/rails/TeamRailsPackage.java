/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.rails;

import java.util.List;

/**
 * Package facade for agent-team rail exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.rails} in
 * {@code openjiuwen/agent_teams/rails/__init__.py}.</p>
 */
public final class TeamRailsPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/rails/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "FirstIterationGate",
            "TeamPolicyRail",
            "TeamPlanModeRail",
            "TeamToolApprovalRail",
            "TeamToolRail",
            "qualify_team_tool_ids"
    );

    private TeamRailsPackage() {
    }

    public static boolean exports(String symbol) {
        return EXPORTED_SYMBOLS.contains(symbol);
    }
}
