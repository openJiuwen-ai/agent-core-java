/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_msgbus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public facade for the hierarchical message-bus team package.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.multi_agent.teams.hierarchical_msgbus} in
 * {@code openjiuwen/core/multi_agent/teams/hierarchical_msgbus/__init__.py}.</p>
 */
public final class HierarchicalMsgbusPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/multi_agent/teams/hierarchical_msgbus/__init__.py";
    public static final List<String> ALL = List.of(
            "HierarchicalTeam",
            "HierarchicalTeamConfig",
            "SupervisorAgent",
            "P2PAbilityManager"
    );
    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private HierarchicalMsgbusPackage() {
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
        putIfPresent(exports, "HierarchicalTeam",
                "com.openjiuwen.core.multiagent.teams.hierarchical_msgbus.HierarchicalTeam");
        exports.put("HierarchicalTeamConfig", HierarchicalTeamConfig.class);
        exports.put("SupervisorAgent", SupervisorAgent.class);
        exports.put("P2PAbilityManager", P2PAbilityManager.class);
        return Collections.unmodifiableMap(exports);
    }

    private static void putIfPresent(Map<String, Class<?>> exports, String exportedName, String className) {
        try {
            exports.put(exportedName, Class.forName(className));
        } catch (ClassNotFoundException ignored) {
            // HierarchicalTeam is translated in the paired task from the same cycle group.
        }
    }
}
