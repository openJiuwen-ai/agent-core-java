/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components;

import com.openjiuwen.core.session.NodeSessionApi;

import java.util.List;
import java.util.Map;

/**
 * Package bridge for workflow component facade exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.workflow.components} package facade in
 * {@code openjiuwen/core/workflow/components/__init__.py}.</p>
 */
public final class WorkflowComponentsPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/workflow/components/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of("Session");

    public static final Map<String, String> EXPORT_SOURCES = Map.of(
            "Session", "openjiuwen.core.session.node.Session"
    );

    public static final Map<String, Class<?>> JAVA_TYPES = Map.of(
            "Session", NodeSessionApi.class
    );

    private WorkflowComponentsPackage() {
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
     * Returns the Python source object imported by this package facade.
     *
     * @param symbolName symbol name
     * @return dotted Python source object, or {@code null} when absent
     */
    public static String sourceFor(String symbolName) {
        return EXPORT_SOURCES.get(symbolName);
    }

    /**
     * Returns the Java type that mirrors the exported Python object.
     *
     * @param symbolName symbol name
     * @return Java type, or {@code null} when absent
     */
    public static Class<?> javaTypeFor(String symbolName) {
        return JAVA_TYPES.get(symbolName);
    }
}
