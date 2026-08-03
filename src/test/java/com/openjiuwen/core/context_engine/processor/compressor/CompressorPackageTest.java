/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.processor.compressor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's package facade in
 * {@code openjiuwen/core/context_engine/processor/compressor/__init__.py}.
 */
class CompressorPackageTest {

    @Test
    void exportedSymbolsMatchPythonAllOrder() {
        assertThat(CompressorPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/core/context_engine/processor/compressor/__init__.py");

        assertThat(CompressorPackage.all()).containsExactly(
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
    }

    @Test
    void keyExportsMapToTranslatedJavaTypes() {
        assertThat(CompressorPackage.sourceFor("CurrentRoundCompressor"))
                .isEqualTo(
                        "openjiuwen.core.context_engine.processor.compressor.current_round_compressor."
                                + "CurrentRoundCompressor"
                );
        assertThat(CompressorPackage.javaTypeNameFor("CurrentRoundCompressor"))
                .isEqualTo("com.openjiuwen.core.context_engine.processor.compressor.CurrentRoundCompressor");
        assertThat(CompressorPackage.sourceFor("FullCompactProcessorConfig"))
                .isEqualTo(
                        "openjiuwen.core.context_engine.processor.compressor.full_compact_processor."
                                + "FullCompactProcessorConfig"
                );
        assertThat(CompressorPackage.javaTypeNameFor("RoundLevelCompressorConfig"))
                .isEqualTo("com.openjiuwen.core.context_engine.processor.compressor.RoundLevelCompressorConfig");
    }

    @Test
    void unknownSymbolIsNotExposed() {
        assertThat(CompressorPackage.exports("reset_task_group")).isFalse();
        assertThat(CompressorPackage.sourceFor("missing")).isNull();
        assertThat(CompressorPackage.javaTypeNameFor("missing")).isNull();
    }

    @Test
    void exportedSymbolsAreImmutable() {
        assertThatThrownBy(() -> CompressorPackage.all().add("unexpected"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
