/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.ui;

import java.util.List;
import java.util.Map;

/**
 * Package bridge for CLI UI exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.cli.ui} package facade in
 * {@code openjiuwen/harness/cli/ui/__init__.py}.</p>
 */
public final class CliUiPackage {
    public static final String PYTHON_MODULE = "openjiuwen/harness/cli/ui/__init__.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "run_repl",
            "render_stream",
            "run_once"
    );
    public static final Map<String, Class<?>> EXPORTED_TYPES = Map.of(
            "run_repl", CliRepl.class,
            "render_stream", CliRenderer.class,
            "run_once", CliRunner.class
    );
    public static final Class<CliRepl> RUN_REPL_OWNER = CliRepl.class;
    public static final Class<CliRenderer> RENDER_STREAM_OWNER = CliRenderer.class;
    public static final Class<CliRunner> RUN_ONCE_OWNER = CliRunner.class;

    private CliUiPackage() {
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
     * Resolves a Python package export to its translated Java owner type.
     *
     * @param symbolName symbol name
     * @return Java owner type, or null when absent
     */
    public static Class<?> typeFor(String symbolName) {
        return EXPORTED_TYPES.get(symbolName);
    }
}
