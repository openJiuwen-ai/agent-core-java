/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.llm.react;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package facade preserving Python ReAct component re-export names.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.workflow.components.llm.react} module in
 * {@code openjiuwen/core/workflow/components/llm/react/__init__.py}.</p>
 */
public final class ReactPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/workflow/components/llm/react/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "ReActAgentComp",
            "ReActAgentCompConfig",
            "ReActAgentCompExecutable"
    );

    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private ReactPackage() {
    }

    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    public static boolean exports(String symbolName) {
        return EXPORTED_SYMBOLS.contains(symbolName);
    }

    public static Class<?> typeFor(String exportedName) {
        return EXPORTED_TYPES.get(exportedName);
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("ReActAgentComp", ReActAgentComp.class);
        exports.put("ReActAgentCompConfig", ReActAgentCompConfig.class);
        exports.put("ReActAgentCompExecutable", ReActAgentCompExecutable.class);
        return Collections.unmodifiableMap(exports);
    }
}
