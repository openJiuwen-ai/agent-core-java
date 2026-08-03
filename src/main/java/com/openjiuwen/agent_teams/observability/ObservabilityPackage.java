/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import com.openjiuwen.agent_teams.agent.TeamAgent;

import java.util.List;

/**
 * Public facade for the agent-team observability subsystem.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.observability} in
 * {@code openjiuwen/agent_teams/observability/__init__.py}.</p>
 */
public final class ObservabilityPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/observability/__init__.py";
    public static final List<String> ALL = List.of(
            "ObservabilityConfig",
            "ObservabilityRail",
            "attach_to_team_agent",
            "detach_from_team_agent",
            "init_observability",
            "shutdown_observability"
    );

    private ObservabilityPackage() {
    }

    public static void initObservability(ObservabilityConfig config) {
        ObservabilitySetup.initObservability(config);
    }

    public static void shutdownObservability() {
        ObservabilitySetup.shutdownObservability();
    }

    public static void attachToTeamAgent(TeamAgent teamAgent) {
        ObservabilitySetup.attachToTeamAgent(teamAgent);
    }

    public static void detachFromTeamAgent(TeamAgent teamAgent) {
        ObservabilitySetup.detachFromTeamAgent(teamAgent);
    }
}
