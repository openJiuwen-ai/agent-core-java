/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.processor.offloader;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package bridge for context-engine offloader exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.context_engine.processor.offloader} package facade in
 * {@code openjiuwen/core/context_engine/processor/offloader/__init__.py}.</p>
 */
public final class OffloaderPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/context_engine/processor/offloader/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "MessageOffloader",
            "MessageOffloaderConfig",
            "MessageSummaryOffloader",
            "MessageSummaryOffloaderConfig",
            "ToolResultBudgetProcessor",
            "ToolResultBudgetProcessorConfig"
    );

    public static final Map<String, String> EXPORT_SOURCES = buildExportSources();
    public static final Map<String, String> JAVA_TYPE_NAMES = buildJavaTypeNames();

    private OffloaderPackage() {
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
                "MessageOffloader",
                "openjiuwen.core.context_engine.processor.offloader.message_offloader.MessageOffloader"
        );
        sources.put(
                "MessageOffloaderConfig",
                "openjiuwen.core.context_engine.processor.offloader.message_offloader.MessageOffloaderConfig"
        );
        sources.put(
                "MessageSummaryOffloader",
                "openjiuwen.core.context_engine.processor.offloader.message_summary_offloader.MessageSummaryOffloader"
        );
        sources.put(
                "MessageSummaryOffloaderConfig",
                "openjiuwen.core.context_engine.processor.offloader.message_summary_offloader.MessageSummaryOffloaderConfig"
        );
        sources.put(
                "ToolResultBudgetProcessor",
                "openjiuwen.core.context_engine.processor.offloader.tool_result_budget_processor."
                        + "ToolResultBudgetProcessor"
        );
        sources.put(
                "ToolResultBudgetProcessorConfig",
                "openjiuwen.core.context_engine.processor.offloader.tool_result_budget_processor."
                        + "ToolResultBudgetProcessorConfig"
        );
        return Collections.unmodifiableMap(sources);
    }

    private static Map<String, String> buildJavaTypeNames() {
        Map<String, String> javaTypeNames = new LinkedHashMap<>();
        javaTypeNames.put(
                "MessageOffloader",
                "com.openjiuwen.core.context_engine.processor.offloader.MessageOffloader"
        );
        javaTypeNames.put(
                "MessageOffloaderConfig",
                "com.openjiuwen.core.context_engine.processor.offloader.MessageOffloaderConfig"
        );
        javaTypeNames.put(
                "MessageSummaryOffloader",
                "com.openjiuwen.core.context_engine.processor.offloader.MessageSummaryOffloader"
        );
        javaTypeNames.put(
                "MessageSummaryOffloaderConfig",
                "com.openjiuwen.core.context_engine.processor.offloader.MessageSummaryOffloaderConfig"
        );
        javaTypeNames.put(
                "ToolResultBudgetProcessor",
                "com.openjiuwen.core.context_engine.processor.offloader.ToolResultBudgetProcessor"
        );
        javaTypeNames.put(
                "ToolResultBudgetProcessorConfig",
                "com.openjiuwen.core.context_engine.processor.offloader.ToolResultBudgetProcessorConfig"
        );
        return Collections.unmodifiableMap(javaTypeNames);
    }
}
