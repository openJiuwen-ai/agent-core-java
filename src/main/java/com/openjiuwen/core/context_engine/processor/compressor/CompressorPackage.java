/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.processor.compressor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package bridge for context-engine compressor exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.context_engine.processor.compressor} package facade in
 * {@code openjiuwen/core/context_engine/processor/compressor/__init__.py}.</p>
 */
public final class CompressorPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/context_engine/processor/compressor/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "CurrentRoundCompressor",
            "CurrentRoundCompressorConfig",
            "DialogueCompressor",
            "DialogueCompressorConfig",
            "FullCompactProcessor",
            "FullCompactProcessorConfig",
            "MicroCompactProcessor",
            "MicroCompactProcessorConfig",
            "RoundLevelCompressor",
            "RoundLevelCompressorConfig"
    );

    public static final Map<String, String> EXPORT_SOURCES = buildExportSources();
    public static final Map<String, String> JAVA_TYPE_NAMES = buildJavaTypeNames();

    private CompressorPackage() {
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
                "CurrentRoundCompressor",
                "openjiuwen.core.context_engine.processor.compressor.current_round_compressor.CurrentRoundCompressor"
        );
        sources.put(
                "CurrentRoundCompressorConfig",
                "openjiuwen.core.context_engine.processor.compressor.current_round_compressor.CurrentRoundCompressorConfig"
        );
        sources.put(
                "DialogueCompressor",
                "openjiuwen.core.context_engine.processor.compressor.dialogue_compressor.DialogueCompressor"
        );
        sources.put(
                "DialogueCompressorConfig",
                "openjiuwen.core.context_engine.processor.compressor.dialogue_compressor.DialogueCompressorConfig"
        );
        sources.put(
                "FullCompactProcessor",
                "openjiuwen.core.context_engine.processor.compressor.full_compact_processor.FullCompactProcessor"
        );
        sources.put(
                "FullCompactProcessorConfig",
                "openjiuwen.core.context_engine.processor.compressor.full_compact_processor.FullCompactProcessorConfig"
        );
        sources.put(
                "MicroCompactProcessor",
                "openjiuwen.core.context_engine.processor.compressor.micro_compact_processor.MicroCompactProcessor"
        );
        sources.put(
                "MicroCompactProcessorConfig",
                "openjiuwen.core.context_engine.processor.compressor.micro_compact_processor.MicroCompactProcessorConfig"
        );
        sources.put(
                "RoundLevelCompressor",
                "openjiuwen.core.context_engine.processor.compressor.round_level_compressor.RoundLevelCompressor"
        );
        sources.put(
                "RoundLevelCompressorConfig",
                "openjiuwen.core.context_engine.processor.compressor.round_level_compressor.RoundLevelCompressorConfig"
        );
        return Collections.unmodifiableMap(sources);
    }

    private static Map<String, String> buildJavaTypeNames() {
        Map<String, String> javaTypeNames = new LinkedHashMap<>();
        javaTypeNames.put(
                "CurrentRoundCompressor",
                "com.openjiuwen.core.context_engine.processor.compressor.CurrentRoundCompressor"
        );
        javaTypeNames.put(
                "CurrentRoundCompressorConfig",
                "com.openjiuwen.core.context_engine.processor.compressor.CurrentRoundCompressorConfig"
        );
        javaTypeNames.put(
                "DialogueCompressor",
                "com.openjiuwen.core.context_engine.processor.compressor.DialogueCompressor"
        );
        javaTypeNames.put(
                "DialogueCompressorConfig",
                "com.openjiuwen.core.context_engine.processor.compressor.DialogueCompressorConfig"
        );
        javaTypeNames.put(
                "FullCompactProcessor",
                "com.openjiuwen.core.context_engine.processor.compressor.FullCompactProcessor"
        );
        javaTypeNames.put(
                "FullCompactProcessorConfig",
                "com.openjiuwen.core.context_engine.processor.compressor.FullCompactProcessorConfig"
        );
        javaTypeNames.put(
                "MicroCompactProcessor",
                "com.openjiuwen.core.context_engine.processor.compressor.MicroCompactProcessor"
        );
        javaTypeNames.put(
                "MicroCompactProcessorConfig",
                "com.openjiuwen.core.context_engine.processor.compressor.MicroCompactProcessorConfig"
        );
        javaTypeNames.put(
                "RoundLevelCompressor",
                "com.openjiuwen.core.context_engine.processor.compressor.RoundLevelCompressor"
        );
        javaTypeNames.put(
                "RoundLevelCompressorConfig",
                "com.openjiuwen.core.context_engine.processor.compressor.RoundLevelCompressorConfig"
        );
        return Collections.unmodifiableMap(javaTypeNames);
    }
}
