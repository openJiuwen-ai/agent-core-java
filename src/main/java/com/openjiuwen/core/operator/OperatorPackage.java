/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Package bridge for core operator exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.operator} package facade in
 * {@code openjiuwen/core/operator/__init__.py}.</p>
 */
public final class OperatorPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/operator/__init__.py";

    public static final String DESCRIPTION =
            "Operator abstraction for atomic execution and optimization.";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "Operator",
            "PreviewableOperator",
            "TunableSpec",
            "LLMCallOperator",
            "LLMCall",
            "ToolCallOperator",
            "MemoryCallOperator",
            "SkillExperienceOperator",
            "SkillCallOperator"
    );

    public static final Map<String, String> EXPORT_SOURCES = buildExportSources();
    public static final Map<String, String> JAVA_TYPE_NAMES = buildJavaTypeNames();

    private OperatorPackage() {
    }

    /**
     * Mirrors Python's {@code __all__} in
     * {@code openjiuwen/core/operator/__init__.py}.
     *
     * @return exported symbol names in Python order
     */
    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    /**
     * Checks whether a symbol is re-exported by the Python package facade.
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
     * Returns the expected Java type name for an exported symbol.
     *
     * @param symbolName symbol name
     * @return fully qualified Java type name, or {@code null} when absent
     */
    public static String javaTypeNameFor(String symbolName) {
        return JAVA_TYPE_NAMES.get(symbolName);
    }

    /**
     * Resolves the Java type for an exported symbol when the translated dependency is present.
     *
     * @param symbolName symbol name
     * @return resolved type, or empty when unknown or not merged yet
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
        sources.put("Operator", "openjiuwen.core.operator.base.Operator");
        sources.put("PreviewableOperator", "openjiuwen.core.operator.base.PreviewableOperator");
        sources.put("TunableSpec", "openjiuwen.core.operator.base.TunableSpec");
        sources.put("LLMCallOperator", "openjiuwen.core.operator.llm_call.LLMCallOperator");
        sources.put("LLMCall", "openjiuwen.core.operator.llm_call.LLMCall");
        sources.put("ToolCallOperator", "openjiuwen.core.operator.tool_call.ToolCallOperator");
        sources.put("MemoryCallOperator", "openjiuwen.core.operator.memory_call.MemoryCallOperator");
        sources.put("SkillExperienceOperator", "openjiuwen.core.operator.skill_call.SkillExperienceOperator");
        sources.put("SkillCallOperator", "openjiuwen.core.operator.skill_call.SkillCallOperator");
        return Collections.unmodifiableMap(sources);
    }

    private static Map<String, String> buildJavaTypeNames() {
        Map<String, String> javaTypeNames = new LinkedHashMap<>();
        javaTypeNames.put("Operator", "com.openjiuwen.core.operator.Operator");
        javaTypeNames.put("PreviewableOperator", "com.openjiuwen.core.operator.PreviewableOperator");
        javaTypeNames.put("TunableSpec", "com.openjiuwen.core.operator.TunableSpec");
        javaTypeNames.put("LLMCallOperator", "com.openjiuwen.core.operator.llm_call.LLMCallOperator");
        javaTypeNames.put("LLMCall", "com.openjiuwen.core.operator.llm_call.LLMCall");
        javaTypeNames.put("ToolCallOperator", "com.openjiuwen.core.operator.tool_call.ToolCallOperator");
        javaTypeNames.put("MemoryCallOperator", "com.openjiuwen.core.operator.memory_call.MemoryCallOperator");
        javaTypeNames.put("SkillExperienceOperator", "com.openjiuwen.core.operator.skill_call.SkillExperienceOperator");
        javaTypeNames.put("SkillCallOperator", "com.openjiuwen.core.operator.skill_call.SkillCallOperator");
        return Collections.unmodifiableMap(javaTypeNames);
    }
}
