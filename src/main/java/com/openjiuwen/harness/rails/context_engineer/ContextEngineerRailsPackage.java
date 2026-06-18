/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.context_engineer;

import java.util.List;
import java.util.Map;

/**
 * Module facade for context engineer rails.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.rails.context_engineer} package facade in
 * {@code openjiuwen/harness/rails/context_engineer/__init__.py}.</p>
 */
public final class ContextEngineerRailsPackage {

    public static final String PYTHON_MODULE = "openjiuwen/harness/rails/context_engineer/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "ContextProcessorRail",
            "ContextAssembleRail"
    );

    public static final Map<String, Class<?>> TYPE_EXPORTS = Map.of(
            "ContextProcessorRail", ContextProcessorRail.class,
            "ContextAssembleRail", ContextAssembleRail.class
    );

    private ContextEngineerRailsPackage() {
    }

    /**
     * Mirrors Python's {@code __all__}.
     *
     * @return exported symbol names in Python order
     */
    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    public static List<Class<?>> exports() {
        return List.of(
                ContextProcessorRail.class,
                ContextAssembleRail.class
        );
    }

    public static boolean exports(String symbolName) {
        return EXPORTED_SYMBOLS.contains(symbolName);
    }

    public static Class<?> typeFor(String symbolName) {
        return TYPE_EXPORTS.get(symbolName);
    }
}
