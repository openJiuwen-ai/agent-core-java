/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.rails;

import java.util.List;
import java.util.Map;

/**
 * Package bridge for CLI rail exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.cli.rails} package facade in
 * {@code openjiuwen/harness/cli/rails/__init__.py}.</p>
 */
public final class HarnessCliRailsPackage {

    public static final String PYTHON_MODULE = "openjiuwen/harness/cli/rails/__init__.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "TokenTrackingRail",
            "ToolTrackingRail"
    );
    public static final Map<String, Class<?>> EXPORTED_TYPES = Map.of(
            "TokenTrackingRail", TokenTrackingRail.class,
            "ToolTrackingRail", ToolTrackingRail.class
    );
    public static final Class<TokenTrackingRail> TOKEN_TRACKING_RAIL = TokenTrackingRail.class;
    public static final Class<ToolTrackingRail> TOOL_TRACKING_RAIL = ToolTrackingRail.class;

    private HarnessCliRailsPackage() {
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
     * Checks whether a symbol is re-exported by Python {@code __all__}.
     *
     * @param symbolName symbol name
     * @return true when the symbol is part of Python {@code __all__}
     */
    public static boolean exports(String symbolName) {
        return EXPORTED_SYMBOLS.contains(symbolName);
    }

    /**
     * Resolves a Python package export to its translated Java type.
     *
     * @param symbolName symbol name
     * @return Java type, or null when absent
     */
    public static Class<?> typeFor(String symbolName) {
        return EXPORTED_TYPES.get(symbolName);
    }
}
