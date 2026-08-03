/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.tool;

import com.openjiuwen.core.foundation.tool.function.LocalFunction;

import java.util.List;
import java.util.Map;

/**
 * Package bridge for context-evolver tool exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.context_evolver.tool} package facade in
 * {@code openjiuwen/extensions/context_evolver/tool/__init__.py}.</p>
 */
public final class ContextEvolverToolPackage {

    public static final String PYTHON_MODULE = "openjiuwen/extensions/context_evolver/tool/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of("wikipedia_tool");

    public static final Map<String, String> EXPORT_SOURCES = Map.of(
            "wikipedia_tool", "openjiuwen.extensions.context_evolver.tool.wikipedia_tool.wikipedia_tool"
    );

    public static final Map<String, LocalFunction> JAVA_EXPORTS = Map.of(
            "wikipedia_tool", WikipediaTool.WIKIPEDIA_TOOL
    );

    private ContextEvolverToolPackage() {
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
     * Returns the Java tool instance that mirrors the exported Python object.
     *
     * @param symbolName symbol name
     * @return Java local function, or {@code null} when absent
     */
    public static LocalFunction localFunctionFor(String symbolName) {
        return JAVA_EXPORTS.get(symbolName);
    }
}
