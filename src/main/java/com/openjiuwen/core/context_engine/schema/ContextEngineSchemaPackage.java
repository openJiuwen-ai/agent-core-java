/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.schema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package bridge for context-engine schema exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.context_engine.schema} package facade in
 * {@code openjiuwen/core/context_engine/schema/__init__.py}.</p>
 */
public final class ContextEngineSchemaPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/context_engine/schema/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "CONTEXT_COMPRESSION_STATE_TYPE",
            "ContextCompressionMetric",
            "ContextCompressionSaved",
            "ContextCompressionState",
            "ContextCompressionUsage"
    );

    public static final Map<String, String> EXPORT_SOURCES = buildExportSources();
    public static final Map<String, String> JAVA_TYPE_NAMES = buildJavaTypeNames();

    private ContextEngineSchemaPackage() {
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
     * Checks whether a symbol is re-exported by Python {@code __all__}.
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
     * Returns the Java type name expected to mirror the Python object.
     *
     * @param symbolName symbol name
     * @return fully qualified Java type name, or {@code null} when absent
     */
    public static String javaTypeNameFor(String symbolName) {
        return JAVA_TYPE_NAMES.get(symbolName);
    }

    private static Map<String, String> buildExportSources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put(
                "CONTEXT_COMPRESSION_STATE_TYPE",
                "openjiuwen.core.context_engine.schema.context_state.CONTEXT_COMPRESSION_STATE_TYPE"
        );
        sources.put(
                "ContextCompressionMetric",
                "openjiuwen.core.context_engine.schema.context_state.ContextCompressionMetric"
        );
        sources.put(
                "ContextCompressionSaved",
                "openjiuwen.core.context_engine.schema.context_state.ContextCompressionSaved"
        );
        sources.put(
                "ContextCompressionState",
                "openjiuwen.core.context_engine.schema.context_state.ContextCompressionState"
        );
        sources.put(
                "ContextCompressionUsage",
                "openjiuwen.core.context_engine.schema.context_state.ContextCompressionUsage"
        );
        return Collections.unmodifiableMap(sources);
    }

    private static Map<String, String> buildJavaTypeNames() {
        Map<String, String> javaTypeNames = new LinkedHashMap<>();
        javaTypeNames.put(
                "CONTEXT_COMPRESSION_STATE_TYPE",
                "com.openjiuwen.core.context_engine.schema.ContextCompressionState#CONTEXT_COMPRESSION_STATE_TYPE"
        );
        javaTypeNames.put(
                "ContextCompressionMetric",
                "com.openjiuwen.core.context_engine.schema.ContextCompressionMetric"
        );
        javaTypeNames.put(
                "ContextCompressionSaved",
                "com.openjiuwen.core.context_engine.schema.ContextCompressionSaved"
        );
        javaTypeNames.put(
                "ContextCompressionState",
                "com.openjiuwen.core.context_engine.schema.ContextCompressionState"
        );
        javaTypeNames.put(
                "ContextCompressionUsage",
                "com.openjiuwen.core.context_engine.schema.ContextCompressionUsage"
        );
        return Collections.unmodifiableMap(javaTypeNames);
    }
}
