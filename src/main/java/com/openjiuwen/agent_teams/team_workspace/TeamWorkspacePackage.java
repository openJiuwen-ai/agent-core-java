/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.team_workspace;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package facade for team shared workspace exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.team_workspace} in
 * {@code openjiuwen/agent_teams/team_workspace/__init__.py}.</p>
 */
public final class TeamWorkspacePackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/team_workspace/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "ConflictStrategy",
            "TeamWorkspaceConfig",
            "WorkspaceFileLock",
            "WorkspaceMode",
            "TeamWorkspaceManager",
            "WorkspaceMetaTool",
            "TeamWorkspaceRail"
    );

    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private TeamWorkspacePackage() {
    }

    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    public static boolean exports(String symbolName) {
        return EXPORTED_SYMBOLS.contains(symbolName);
    }

    public static Class<?> typeFor(String exportedName) {
        return EXPORTED_TYPES.get(exportedName);
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("ConflictStrategy", ConflictStrategy.class);
        exports.put("TeamWorkspaceConfig", TeamWorkspaceConfig.class);
        exports.put("WorkspaceFileLock", WorkspaceFileLock.class);
        exports.put("WorkspaceMode", WorkspaceMode.class);
        exports.put("TeamWorkspaceManager", TeamWorkspaceManager.class);
        exports.put("WorkspaceMetaTool", WorkspaceMetaTool.class);
        exports.put("TeamWorkspaceRail", TeamWorkspaceRail.class);
        return Collections.unmodifiableMap(exports);
    }
}
