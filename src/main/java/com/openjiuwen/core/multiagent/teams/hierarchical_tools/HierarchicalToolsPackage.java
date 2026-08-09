/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_tools;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public facade for the hierarchical tools team package.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.multi_agent.teams.hierarchical_tools} in
 * {@code openjiuwen/core/multi_agent/teams/hierarchical_tools/__init__.py}.</p>
 */
public final class HierarchicalToolsPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/multi_agent/teams/hierarchical_tools/__init__.py";
    public static final List<String> ALL = List.of("HierarchicalTeamConfig", "HierarchicalTeam");
    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private HierarchicalToolsPackage() {
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
        exports.put("HierarchicalTeamConfig", HierarchicalTeamConfig.class);
        exports.put("HierarchicalTeam", HierarchicalTeam.class);
        return Collections.unmodifiableMap(exports);
    }
}
