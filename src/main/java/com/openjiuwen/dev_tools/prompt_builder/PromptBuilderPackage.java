/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.prompt_builder;

import com.openjiuwen.dev_tools.prompt_builder.builder.BadCasePromptBuilder;
import com.openjiuwen.dev_tools.prompt_builder.builder.FeedbackPromptBuilder;
import com.openjiuwen.dev_tools.prompt_builder.builder.MetaTemplateBuilder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package facade preserving Python prompt-builder re-export names.
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.prompt_builder} module in
 * {@code openjiuwen/dev_tools/prompt_builder/__init__.py}.</p>
 */
public final class PromptBuilderPackage {
    public static final String PYTHON_MODULE = "openjiuwen/dev_tools/prompt_builder/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "MetaTemplateBuilder",
            "FeedbackPromptBuilder",
            "BadCasePromptBuilder"
    );

    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private PromptBuilderPackage() {
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
        exports.put("MetaTemplateBuilder", MetaTemplateBuilder.class);
        exports.put("FeedbackPromptBuilder", FeedbackPromptBuilder.class);
        exports.put("BadCasePromptBuilder", BadCasePromptBuilder.class);
        return Collections.unmodifiableMap(exports);
    }
}
