/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Package bridge for Pregel exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.graph.pregel} package facade in
 * {@code openjiuwen/core/graph/pregel/__init__.py}.</p>
 */
public final class PregelPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/graph/pregel/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "PregelBuilder",
            "PregelConfig",
            "Pregel",
            "GraphInterrupt",
            "Interrupt",
            "TASK_STATUS_INTERRUPT",
            "MAX_RECURSIVE_LIMIT",
            "START",
            "END"
    );

    public static final Map<String, String> EXPORT_SOURCES = buildExportSources();
    public static final Map<String, String> JAVA_TYPE_NAMES = buildJavaTypeNames();
    public static final Map<String, Object> CONSTANT_VALUES = buildConstantValues();

    private PregelPackage() {
    }

    /**
     * Mirrors Python's {@code __all__}.
     *
     * @return exported symbol names in Python order
     */
    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    public static boolean exports(String symbolName) {
        return EXPORTED_SYMBOLS.contains(symbolName);
    }

    public static String sourceFor(String symbolName) {
        return EXPORT_SOURCES.get(symbolName);
    }

    public static String javaTypeNameFor(String symbolName) {
        return JAVA_TYPE_NAMES.get(symbolName);
    }

    public static Object constantValueFor(String symbolName) {
        return CONSTANT_VALUES.get(symbolName);
    }

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
        sources.put("PregelBuilder", "openjiuwen.core.graph.pregel.builder.PregelBuilder");
        sources.put("PregelConfig", "openjiuwen.core.graph.pregel.config.PregelConfig");
        sources.put("Pregel", "openjiuwen.core.graph.pregel.engine.Pregel");
        sources.put("GraphInterrupt", "openjiuwen.core.graph.pregel.base.GraphInterrupt");
        sources.put("Interrupt", "openjiuwen.core.graph.pregel.base.Interrupt");
        sources.put("TASK_STATUS_INTERRUPT", "openjiuwen.core.graph.pregel.constants.TASK_STATUS_INTERRUPT");
        sources.put("MAX_RECURSIVE_LIMIT", "openjiuwen.core.graph.pregel.constants.MAX_RECURSIVE_LIMIT");
        sources.put("START", "openjiuwen.core.graph.pregel.constants.START");
        sources.put("END", "openjiuwen.core.graph.pregel.constants.END");
        return Collections.unmodifiableMap(sources);
    }

    private static Map<String, String> buildJavaTypeNames() {
        Map<String, String> javaTypeNames = new LinkedHashMap<>();
        javaTypeNames.put("PregelBuilder", "com.openjiuwen.core.graph.pregel.PregelBuilder");
        javaTypeNames.put("PregelConfig", "com.openjiuwen.core.graph.pregel.PregelConfig");
        javaTypeNames.put("Pregel", "com.openjiuwen.core.graph.pregel.Pregel");
        javaTypeNames.put("GraphInterrupt", "com.openjiuwen.core.graph.pregel.GraphInterrupt");
        javaTypeNames.put("Interrupt", "com.openjiuwen.core.graph.pregel.Interrupt");
        return Collections.unmodifiableMap(javaTypeNames);
    }

    private static Map<String, Object> buildConstantValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("TASK_STATUS_INTERRUPT", PregelConstants.TASK_STATUS_INTERRUPT);
        values.put("MAX_RECURSIVE_LIMIT", PregelConstants.MAX_RECURSIVE_LIMIT);
        values.put("START", PregelConstants.START);
        values.put("END", PregelConstants.END);
        return Collections.unmodifiableMap(values);
    }
}
