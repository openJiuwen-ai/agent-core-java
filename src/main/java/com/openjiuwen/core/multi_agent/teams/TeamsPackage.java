/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams;

import com.openjiuwen.core.multi_agent.teams.handoff.HandoffConfig;
import com.openjiuwen.core.multi_agent.teams.handoff.HandoffOrchestrator;
import com.openjiuwen.core.multi_agent.teams.handoff.HandoffRoute;
import com.openjiuwen.core.multi_agent.teams.handoff.HandoffSignal;
import com.openjiuwen.core.multi_agent.teams.handoff.HandoffTeam;
import com.openjiuwen.core.multi_agent.teams.handoff.HandoffTeamConfig;
import com.openjiuwen.core.multi_agent.teams.handoff.TeamInterruptSignal;
import com.openjiuwen.core.multi_agent.teams.hierarchical_msgbus.SupervisorAgent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public facade for the multi-agent teams package.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.multi_agent.teams} in
 * {@code openjiuwen/core/multi_agent/teams/__init__.py}.</p>
 */
public final class TeamsPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/multi_agent/teams/__init__.py";
    public static final List<String> ALL = List.of(
            "make_team_session",
            "standalone_invoke_context",
            "standalone_stream_context",
            "HandoffTeam",
            "HandoffTeamConfig",
            "HandoffConfig",
            "HandoffRoute",
            "HandoffSignal",
            "HandoffOrchestrator",
            "TeamInterruptSignal",
            "HierarchicalToolsTeam",
            "HierarchicalMsgbusTeam",
            "HierarchicalTeamConfig",
            "SupervisorAgent"
    );
    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private TeamsPackage() {
    }

    public static List<String> all() {
        return ALL;
    }

    public static boolean exports(String symbolName) {
        return ALL.contains(symbolName);
    }

    public static Class<?> typeFor(String exportedName) {
        return EXPORTED_TYPES.get(exportedName);
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("HandoffTeam", HandoffTeam.class);
        exports.put("HandoffTeamConfig", HandoffTeamConfig.class);
        exports.put("HandoffConfig", HandoffConfig.class);
        exports.put("HandoffRoute", HandoffRoute.class);
        exports.put("HandoffSignal", HandoffSignal.class);
        exports.put("HandoffOrchestrator", HandoffOrchestrator.class);
        exports.put("TeamInterruptSignal", TeamInterruptSignal.class);
        exports.put("HierarchicalToolsTeam",
                com.openjiuwen.core.multi_agent.teams.hierarchical_tools.HierarchicalTeam.class);
        exports.put("HierarchicalMsgbusTeam",
                com.openjiuwen.core.multi_agent.teams.hierarchical_msgbus.HierarchicalTeam.class);
        exports.put("HierarchicalTeamConfig",
                com.openjiuwen.core.multi_agent.teams.hierarchical_msgbus.HierarchicalTeamConfig.class);
        exports.put("SupervisorAgent", SupervisorAgent.class);
        return Collections.unmodifiableMap(exports);
    }
}
