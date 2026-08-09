/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.team_runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package facade for multi-agent team runtime exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.multi_agent.team_runtime} in
 * {@code openjiuwen/core/multi_agent/team_runtime/__init__.py}.</p>
 */
public final class TeamRuntimePackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/multi_agent/team_runtime/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "MessageEnvelope",
            "MessageBus",
            "MessageBusConfig",
            "TeamRuntime",
            "RuntimeConfig",
            "CommunicableAgent"
    );

    public static final Map<String, String> EXPORT_SOURCES = buildExportSources();
    public static final Map<String, Class<?>> JAVA_TYPES = buildJavaTypes();

    private TeamRuntimePackage() {
    }

    /**
     * Mirrors Python's {@code __all__}.
     *
     * @return exported symbol names in Python order
     */
    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    /**
     * Checks whether the Python package facade exports a symbol.
     *
     * @param symbolName symbol name
     * @return {@code true} when Python {@code __getattr__} handles the symbol
     */
    public static boolean exports(String symbolName) {
        return EXPORTED_SYMBOLS.contains(symbolName);
    }

    /**
     * Returns the Python source object imported lazily by the package facade.
     *
     * @param symbolName symbol name
     * @return dotted Python source object, or {@code null} when absent
     */
    public static String sourceFor(String symbolName) {
        return EXPORT_SOURCES.get(symbolName);
    }

    /**
     * Resolves the translated Java type for a Python lazy export.
     *
     * @param symbolName symbol name
     * @return translated Java type
     */
    public static Class<?> resolveType(String symbolName) {
        Class<?> type = JAVA_TYPES.get(symbolName);
        if (type == null) {
            throw new IllegalArgumentException(
                    "module 'openjiuwen.core.multi_agent.team_runtime' has no attribute '" + symbolName + "'");
        }
        return type;
    }

    private static Map<String, String> buildExportSources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("MessageEnvelope", "openjiuwen.core.multi_agent.team_runtime.envelope.MessageEnvelope");
        sources.put("MessageBus", "openjiuwen.core.multi_agent.team_runtime.message_bus.MessageBus");
        sources.put("MessageBusConfig", "openjiuwen.core.multi_agent.team_runtime.message_bus.MessageBusConfig");
        sources.put("TeamRuntime", "openjiuwen.core.multi_agent.team_runtime.team_runtime.TeamRuntime");
        sources.put("RuntimeConfig", "openjiuwen.core.multi_agent.team_runtime.team_runtime.RuntimeConfig");
        sources.put("CommunicableAgent", "openjiuwen.core.multi_agent.team_runtime.communicable_agent.CommunicableAgent");
        return Collections.unmodifiableMap(sources);
    }

    private static Map<String, Class<?>> buildJavaTypes() {
        Map<String, Class<?>> types = new LinkedHashMap<>();
        types.put("MessageEnvelope", MessageEnvelope.class);
        types.put("MessageBus", MessageBus.class);
        types.put("MessageBusConfig", MessageBusConfig.class);
        types.put("TeamRuntime", TeamRuntime.class);
        types.put("RuntimeConfig", RuntimeConfig.class);
        types.put("CommunicableAgent", CommunicableAgent.class);
        return Collections.unmodifiableMap(types);
    }
}
