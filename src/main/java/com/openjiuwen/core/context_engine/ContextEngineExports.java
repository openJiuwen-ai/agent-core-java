/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine;

import java.util.List;

/**
 * Public export names for the context-engine package facade.
 *
 * <p>Mirrors Python's module-level {@code __all__} in
 * {@code openjiuwen/core/context_engine/__init__.py}.</p>
 */
public final class ContextEngineExports {

    public static final List<String> CORE_CLASSES = List.of(
            "ContextEngineConfig",
            "ContextWindow",
            "ModelContext",
            "ContextStats",
            "ContextEngine"
    );

    public static final List<String> TOKEN_COUNTERS = List.of(
            "TokenCounter",
            "TiktokenCounter"
    );

    public static final List<String> PROCESSOR_CLASSES = List.of(
            "ContextProcessor",
            "ToolResultBudgetProcessor",
            "ToolResultBudgetProcessorConfig",
            "MessageOffloader",
            "MessageOffloaderConfig",
            "MessageSummaryOffloader",
            "MessageSummaryOffloaderConfig",
            "MicroCompactProcessor",
            "MicroCompactProcessorConfig",
            "DialogueCompressor",
            "DialogueCompressorConfig",
            "CurrentRoundCompressor",
            "CurrentRoundCompressorConfig",
            "RoundLevelCompressor",
            "RoundLevelCompressorConfig",
            "FullCompactProcessor",
            "FullCompactProcessorConfig"
    );

    public static final List<String> ALL = concat(CORE_CLASSES, TOKEN_COUNTERS, PROCESSOR_CLASSES);

    private ContextEngineExports() {
    }

    private static List<String> concat(List<String> coreClasses, List<String> tokenCounters,
                                       List<String> processorClasses) {
        return java.util.stream.Stream.of(coreClasses, tokenCounters, processorClasses)
                .flatMap(List::stream)
                .toList();
    }
}
