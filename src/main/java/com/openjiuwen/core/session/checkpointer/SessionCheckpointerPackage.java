/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Package bridge for checkpointer exports.
 *
 * <p>Mirrors Python's {@code __all__} in
 * {@code openjiuwen/core/session/checkpointer/__init__.py}.</p>
 */
public final class SessionCheckpointerPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/session/checkpointer/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "CheckpointerFactory",
            "CheckpointerProvider",
            "Checkpointer",
            "Storage",
            "build_key",
            "build_key_with_namespace",
            "SESSION_NAMESPACE_AGENT",
            "SESSION_NAMESPACE_AGENT_TEAM",
            "SESSION_NAMESPACE_WORKFLOW",
            "WORKFLOW_NAMESPACE_GRAPH"
    );

    public static final Map<String, String> EXPORT_SOURCES = buildExportSources();
    public static final Map<String, String> JAVA_TYPE_NAMES = buildJavaTypeNames();
    public static final Map<String, Object> CONSTANT_VALUES = buildConstantValues();

    private SessionCheckpointerPackage() {
    }

    /**
     * Mirrors Python's ordered {@code __all__}.
     *
     * @return exported symbol names in Python order
     */
    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    /**
     * Checks whether the package facade exports a symbol.
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
     * @return dotted Python source object, or null when absent
     */
    public static String sourceFor(String symbolName) {
        return EXPORT_SOURCES.get(symbolName);
    }

    /**
     * Returns the translated Java type name for class-like exports.
     *
     * @param symbolName symbol name
     * @return Java type name, or null for function/constant exports
     */
    public static String javaTypeNameFor(String symbolName) {
        return JAVA_TYPE_NAMES.get(symbolName);
    }

    /**
     * Returns a translated constant value.
     *
     * @param symbolName symbol name
     * @return constant value, or null for non-constant exports
     */
    public static Object constantValueFor(String symbolName) {
        return CONSTANT_VALUES.get(symbolName);
    }

    /**
     * Resolves a translated Java type for class-like exports.
     *
     * @param symbolName symbol name
     * @return resolved type, or empty for functions, constants, or missing types
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
        sources.put("CheckpointerFactory", "openjiuwen.core.session.checkpointer.checkpointer.CheckpointerFactory");
        sources.put("CheckpointerProvider", "openjiuwen.core.session.checkpointer.checkpointer.CheckpointerProvider");
        sources.put("Checkpointer", "openjiuwen.core.session.checkpointer.base.Checkpointer");
        sources.put("Storage", "openjiuwen.core.session.checkpointer.base.Storage");
        sources.put("build_key", "openjiuwen.core.session.checkpointer.base.build_key");
        sources.put("build_key_with_namespace", "openjiuwen.core.session.checkpointer.base.build_key_with_namespace");
        sources.put("SESSION_NAMESPACE_AGENT", "openjiuwen.core.session.checkpointer.base.SESSION_NAMESPACE_AGENT");
        sources.put("SESSION_NAMESPACE_AGENT_TEAM",
                "openjiuwen.core.session.checkpointer.base.SESSION_NAMESPACE_AGENT_TEAM");
        sources.put("SESSION_NAMESPACE_WORKFLOW",
                "openjiuwen.core.session.checkpointer.base.SESSION_NAMESPACE_WORKFLOW");
        sources.put("WORKFLOW_NAMESPACE_GRAPH", "openjiuwen.core.session.checkpointer.base.WORKFLOW_NAMESPACE_GRAPH");
        return Collections.unmodifiableMap(sources);
    }

    private static Map<String, String> buildJavaTypeNames() {
        Map<String, String> javaTypeNames = new LinkedHashMap<>();
        javaTypeNames.put("CheckpointerFactory", "com.openjiuwen.core.session.checkpointer.CheckpointerFactory");
        javaTypeNames.put("CheckpointerProvider", "com.openjiuwen.core.session.checkpointer.CheckpointerProvider");
        javaTypeNames.put("Checkpointer", "com.openjiuwen.core.session.checkpointer.Checkpointer");
        javaTypeNames.put("Storage", "com.openjiuwen.core.session.checkpointer.Storage");
        return Collections.unmodifiableMap(javaTypeNames);
    }

    private static Map<String, Object> buildConstantValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("SESSION_NAMESPACE_AGENT", Checkpointer.SESSION_NAMESPACE_AGENT);
        values.put("SESSION_NAMESPACE_AGENT_TEAM", Checkpointer.SESSION_NAMESPACE_AGENT_TEAM);
        values.put("SESSION_NAMESPACE_WORKFLOW", Checkpointer.SESSION_NAMESPACE_WORKFLOW);
        values.put("WORKFLOW_NAMESPACE_GRAPH", Checkpointer.WORKFLOW_NAMESPACE_GRAPH);
        return Collections.unmodifiableMap(values);
    }
}
