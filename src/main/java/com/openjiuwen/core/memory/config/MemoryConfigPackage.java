/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Package bridge for memory config exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.memory.config} package facade in
 * {@code openjiuwen/core/memory/config/__init__.py}.</p>
 */
public final class MemoryConfigPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/memory/config/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "MemoryEngineConfig",
            "MemoryScopeConfig",
            "AgentMemoryConfig"
    );

    public static final Map<String, String> EXPORT_SOURCES = buildExportSources();
    public static final Map<String, String> JAVA_TYPE_NAMES = buildJavaTypeNames();

    private MemoryConfigPackage() {
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
     * Checks whether a symbol is re-exported by the Python package facade.
     *
     * @param symbolName symbol name
     * @return {@code true} when the symbol is part of Python {@code __all__}
     */
    public static boolean exports(String symbolName) {
        return EXPORTED_SYMBOLS.contains(symbolName);
    }

    /**
     * Returns the Python source object imported by the package facade.
     *
     * @param symbolName symbol name
     * @return dotted Python source object, or {@code null} when absent
     */
    public static String sourceFor(String symbolName) {
        return EXPORT_SOURCES.get(symbolName);
    }

    /**
     * Returns the Java type name expected to mirror the exported Python object.
     *
     * @param symbolName symbol name
     * @return fully qualified Java type name, or {@code null} when absent
     */
    public static String javaTypeNameFor(String symbolName) {
        return JAVA_TYPE_NAMES.get(symbolName);
    }

    /**
     * Resolves the Java type for an exported symbol when the translated dependency is present.
     *
     * @param symbolName symbol name
     * @return resolved type, or empty when the symbol is unknown or the type has not been merged yet
     */
    public static Optional<Class<?>> resolveType(String symbolName) {
        String javaTypeName = JAVA_TYPE_NAMES.get(symbolName);
        if (javaTypeName == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Class.forName(javaTypeName));
        } catch (ClassNotFoundException ignored) {
            return Optional.empty();
        }
    }

    private static Map<String, String> buildExportSources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("MemoryEngineConfig", "openjiuwen.core.memory.config.config.MemoryEngineConfig");
        sources.put("MemoryScopeConfig", "openjiuwen.core.memory.config.config.MemoryScopeConfig");
        sources.put("AgentMemoryConfig", "openjiuwen.core.memory.config.config.AgentMemoryConfig");
        return Collections.unmodifiableMap(sources);
    }

    private static Map<String, String> buildJavaTypeNames() {
        Map<String, String> javaTypeNames = new LinkedHashMap<>();
        javaTypeNames.put("MemoryEngineConfig", "com.openjiuwen.core.memory.config.MemoryEngineConfig");
        javaTypeNames.put("MemoryScopeConfig", "com.openjiuwen.core.memory.config.MemoryScopeConfig");
        javaTypeNames.put("AgentMemoryConfig", "com.openjiuwen.core.memory.config.AgentMemoryConfig");
        return Collections.unmodifiableMap(javaTypeNames);
    }
}
