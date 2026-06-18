/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Package bridge for memory exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.memory} package facade in
 * {@code openjiuwen/core/memory/__init__.py}.</p>
 */
public final class MemoryPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/memory/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "MemoryEngineConfig",
            "MemoryScopeConfig",
            "AgentMemoryConfig",
            "LongTermMemory"
    );

    public static final Map<String, String> EXPORT_SOURCES = buildExportSources();
    public static final Map<String, String> JAVA_TYPE_NAMES = buildJavaTypeNames();

    private MemoryPackage() {
    }

    /**
     * Mirrors Python's {@code __all__} in
     * {@code openjiuwen/core/memory/__init__.py}.
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
     * @return true when the symbol is part of Python {@code __all__}
     */
    public static boolean exports(String symbolName) {
        return EXPORTED_SYMBOLS.contains(symbolName);
    }

    /**
     * Returns the Python source object imported by the package facade.
     *
     * @param symbolName symbol name
     * @return dotted Python source object, or null when absent
     */
    public static String sourceFor(String symbolName) {
        return EXPORT_SOURCES.get(symbolName);
    }

    /**
     * Returns the expected Java type name for an exported symbol.
     *
     * @param symbolName symbol name
     * @return fully qualified Java type name, or null when absent
     */
    public static String javaTypeNameFor(String symbolName) {
        return JAVA_TYPE_NAMES.get(symbolName);
    }

    /**
     * Resolves the Java type for an exported symbol when the translated
     * dependency is present.
     *
     * @param symbolName symbol name
     * @return resolved type, or empty when unknown or not merged yet
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
        sources.put("MemoryEngineConfig", "openjiuwen.core.memory.config.MemoryEngineConfig");
        sources.put("MemoryScopeConfig", "openjiuwen.core.memory.config.MemoryScopeConfig");
        sources.put("AgentMemoryConfig", "openjiuwen.core.memory.config.AgentMemoryConfig");
        sources.put("LongTermMemory", "openjiuwen.core.memory.long_term_memory.LongTermMemory");
        return Collections.unmodifiableMap(sources);
    }

    private static Map<String, String> buildJavaTypeNames() {
        Map<String, String> javaTypeNames = new LinkedHashMap<>();
        javaTypeNames.put("MemoryEngineConfig", "com.openjiuwen.core.memory.config.MemoryEngineConfig");
        javaTypeNames.put("MemoryScopeConfig", "com.openjiuwen.core.memory.config.MemoryScopeConfig");
        javaTypeNames.put("AgentMemoryConfig", "com.openjiuwen.core.memory.config.AgentMemoryConfig");
        javaTypeNames.put("LongTermMemory", "com.openjiuwen.core.memory.LongTermMemory");
        return Collections.unmodifiableMap(javaTypeNames);
    }
}
