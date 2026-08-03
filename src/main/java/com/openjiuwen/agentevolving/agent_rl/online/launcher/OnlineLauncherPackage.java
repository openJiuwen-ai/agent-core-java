/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.launcher;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package facade for online RL launcher helpers.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.agent_rl.online.launcher} package in
 * {@code openjiuwen/agent_evolving/agent_rl/online/launcher/__init__.py}.</p>
 */
public final class OnlineLauncherPackage {

    public static final String PYTHON_MODULE =
            "openjiuwen/agent_evolving/agent_rl/online/launcher/__init__.py";
    public static final String DESCRIPTION = "Launcher helpers for the online RL loop.";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "LauncherPaths",
            "run_online_rl_loop"
    );
    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private OnlineLauncherPackage() {
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

    public static Class<?> getAttribute(String name) {
        Class<?> exportedType = typeFor(name);
        if (exportedType != null) {
            return exportedType;
        }
        throw new IllegalArgumentException(
                "module 'openjiuwen.agent_evolving.agent_rl.online.launcher' has no attribute '" + name + "'"
        );
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("LauncherPaths", LauncherRunner.LauncherPaths.class);
        exports.put("run_online_rl_loop", LauncherRunner.class);
        return Collections.unmodifiableMap(exports);
    }
}
