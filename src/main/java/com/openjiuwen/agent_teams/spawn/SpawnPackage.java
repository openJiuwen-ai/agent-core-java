/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.spawn;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Package facade metadata for agent-team spawn helpers.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.spawn} package facade in
 * {@code openjiuwen/agent_teams/spawn/__init__.py}.</p>
 */
public final class SpawnPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/spawn/__init__.py";

    private static final String INPROCESS_HANDLE_SOURCE = "openjiuwen.agent_teams.spawn.inprocess_handle.";
    private static final String INPROCESS_SPAWN_SOURCE = "openjiuwen.agent_teams.spawn.inprocess_spawn.";
    private static final String SHARED_RESOURCES_SOURCE = "openjiuwen.agent_teams.spawn.shared_resources.";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "InProcessSpawnHandle",
            "inprocess_spawn",
            "get_shared_db",
            "get_shared_runtime",
            "cleanup_shared_resources"
    );

    public static final Map<String, String> EXPORT_SOURCES = Map.of(
            "InProcessSpawnHandle", INPROCESS_HANDLE_SOURCE + "InProcessSpawnHandle",
            "inprocess_spawn", INPROCESS_SPAWN_SOURCE + "inprocess_spawn",
            "get_shared_db", SHARED_RESOURCES_SOURCE + "get_shared_db",
            "get_shared_runtime", SHARED_RESOURCES_SOURCE + "get_shared_runtime",
            "cleanup_shared_resources", SHARED_RESOURCES_SOURCE + "cleanup_shared_resources"
    );

    public static final Map<String, String> JAVA_SYMBOL_NAMES = Map.of(
            "InProcessSpawnHandle", "com.openjiuwen.agent_teams.spawn.InProcessSpawnHandle",
            "inprocess_spawn", "com.openjiuwen.agent_teams.spawn.InProcessSpawn#inprocessSpawn",
            "get_shared_db", "com.openjiuwen.agent_teams.spawn.SharedResources#getSharedDb",
            "get_shared_runtime", "com.openjiuwen.agent_teams.spawn.SharedResources#getSharedRuntime",
            "cleanup_shared_resources", "com.openjiuwen.agent_teams.spawn.SharedResources#cleanupSharedResources"
    );

    private SpawnPackage() {
    }

    /**
     * Mirrors Python's ordered {@code __all__} list.
     *
     * @return exported spawn helper symbol names
     */
    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    /**
     * Checks whether Python's spawn package exports a symbol.
     *
     * @param name attribute name
     * @return true when the attribute is in {@code __all__}
     */
    public static boolean exports(String name) {
        return EXPORTED_SYMBOLS.contains(name);
    }

    /**
     * Returns the dotted Python object imported by {@code spawn.__init__}.
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
     * @return Java class or method symbol name, or null when absent
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
     * @return Java class for class-like exports, or empty for methods and missing names
     */
    public static Optional<Class<?>> resolveType(String name) {
        String javaType = JAVA_SYMBOL_NAMES.get(name);
        if (javaType == null || javaType.contains("#")) {
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
                    "module 'openjiuwen.agent_teams.spawn' has no attribute '" + name + "'");
        }
        return JAVA_SYMBOL_NAMES.get(name);
    }
}
