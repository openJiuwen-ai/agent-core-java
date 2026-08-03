/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Package facade metadata for agent-team agent implementations.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.agent} package facade in
 * {@code openjiuwen/agent_teams/agent/__init__.py}.</p>
 */
public final class AgentPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/agent/__init__.py";

    private static final String AGENT_CONFIGURATOR_SOURCE = "openjiuwen.agent_teams.agent.agent_configurator.";
    private static final String COORDINATION_SOURCE = "openjiuwen.agent_teams.agent.coordination.";
    private static final String RECOVERY_MANAGER_SOURCE = "openjiuwen.agent_teams.agent.recovery_manager.";
    private static final String SESSION_MANAGER_SOURCE = "openjiuwen.agent_teams.agent.session_manager.";
    private static final String SPAWN_MANAGER_SOURCE = "openjiuwen.agent_teams.agent.spawn_manager.";
    private static final String STREAM_CONTROLLER_SOURCE = "openjiuwen.agent_teams.agent.stream_controller.";
    private static final String TEAM_AGENT_SOURCE = "openjiuwen.agent_teams.agent.team_agent.";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "AgentConfigurator",
            "CoordinationKernel",
            "EventBus",
            "RecoveryManager",
            "SessionManager",
            "SpawnManager",
            "StreamController",
            "TeamAgent"
    );

    public static final Map<String, String> EXPORT_SOURCES = Map.of(
            "AgentConfigurator", AGENT_CONFIGURATOR_SOURCE + "AgentConfigurator",
            "CoordinationKernel", COORDINATION_SOURCE + "CoordinationKernel",
            "EventBus", COORDINATION_SOURCE + "EventBus",
            "RecoveryManager", RECOVERY_MANAGER_SOURCE + "RecoveryManager",
            "SessionManager", SESSION_MANAGER_SOURCE + "SessionManager",
            "SpawnManager", SPAWN_MANAGER_SOURCE + "SpawnManager",
            "StreamController", STREAM_CONTROLLER_SOURCE + "StreamController",
            "TeamAgent", TEAM_AGENT_SOURCE + "TeamAgent"
    );

    public static final Map<String, String> JAVA_SYMBOL_NAMES = Map.of(
            "AgentConfigurator", "com.openjiuwen.agent_teams.agent.AgentConfigurator",
            "CoordinationKernel", "com.openjiuwen.agent_teams.agent.coordination.CoordinationKernel",
            "EventBus", "com.openjiuwen.agent_teams.agent.coordination.EventBus",
            "RecoveryManager", "com.openjiuwen.agent_teams.agent.RecoveryManager",
            "SessionManager", "com.openjiuwen.agent_teams.agent.SessionManager",
            "SpawnManager", "com.openjiuwen.agent_teams.agent.SpawnManager",
            "StreamController", "com.openjiuwen.agent_teams.agent.StreamController",
            "TeamAgent", "com.openjiuwen.agent_teams.agent.TeamAgent"
    );

    private AgentPackage() {
    }

    /**
     * Mirrors Python's ordered {@code __all__} list.
     *
     * @return exported agent symbol names
     */
    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    /**
     * Checks whether Python's agent package exports a symbol.
     *
     * @param name attribute name
     * @return true when the attribute is in {@code __all__}
     */
    public static boolean exports(String name) {
        return EXPORTED_SYMBOLS.contains(name);
    }

    /**
     * Returns the dotted Python object imported by {@code agent.__init__}.
     *
     * @param name attribute name
     * @return dotted Python source object, or null when absent
     */
    public static String sourceFor(String name) {
        return EXPORT_SOURCES.get(name);
    }

    /**
     * Returns the translated Java symbol name.
     *
     * @param name attribute name
     * @return Java class symbol name, or null when absent
     */
    public static String javaSymbolNameFor(String name) {
        return JAVA_SYMBOL_NAMES.get(name);
    }

    /**
     * Checks whether a Python export has a translated Java symbol.
     *
     * @param name attribute name
     * @return true when translated in the current Java tree
     */
    public static boolean translated(String name) {
        return JAVA_SYMBOL_NAMES.containsKey(name);
    }

    /**
     * Resolves translated class exports lazily.
     *
     * @param name attribute name
     * @return Java class for class-like exports, or empty for missing names
     */
    public static Optional<Class<?>> resolveType(String name) {
        String javaType = JAVA_SYMBOL_NAMES.get(name);
        if (javaType == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Class.forName(javaType));
        } catch (ClassNotFoundException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Resolves a translated Java symbol or raises a Python-like missing attribute error.
     *
     * @param name attribute name
     * @return Java symbol name
     */
    public static String getAttr(String name) {
        if (!exports(name)) {
            throw new NoSuchElementException(
                    "module 'openjiuwen.agent_teams.agent' has no attribute '" + name + "'");
        }
        return JAVA_SYMBOL_NAMES.get(name);
    }
}
