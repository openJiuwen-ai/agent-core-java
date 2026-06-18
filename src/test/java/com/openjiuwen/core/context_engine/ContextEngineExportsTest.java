/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for {@link ContextEngineExports}.
 *
 * <p>Mirrors Python's module-level {@code __all__} in
 * {@code openjiuwen/core/context_engine/__init__.py}.</p>
 */
class ContextEngineExportsTest {

    @Test
    void coreClassesMatchPythonOrder() {
        assertThat(ContextEngineExports.CORE_CLASSES).containsExactly(
                "ContextEngineConfig",
                "ContextWindow",
                "ModelContext",
                "ContextStats",
                "ContextEngine"
        );
    }

    @Test
    void tokenCountersMatchPythonOrder() {
        assertThat(ContextEngineExports.TOKEN_COUNTERS).containsExactly(
                "TokenCounter",
                "TiktokenCounter"
        );
    }

    @Test
    void processorClassesMatchPythonOrder() {
        assertThat(ContextEngineExports.PROCESSOR_CLASSES).containsExactly(
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
    }

    @Test
    void allConcatenatesGroupsExactlyLikePythonAll() {
        List<String> expected = java.util.stream.Stream.of(
                        ContextEngineExports.CORE_CLASSES,
                        ContextEngineExports.TOKEN_COUNTERS,
                        ContextEngineExports.PROCESSOR_CLASSES)
                .flatMap(List::stream)
                .toList();

        assertThat(ContextEngineExports.ALL).containsExactlyElementsOf(expected);
        assertThat(ContextEngineExports.ALL).hasSize(24);
    }
}
