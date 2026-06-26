/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.processor.offloader;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's package facade in
 * {@code openjiuwen/core/context_engine/processor/offloader/__init__.py}.
 */
class OffloaderPackageTest {

    @Test
    void exportedSymbolsMatchPythonAllOrder() {
        assertThat(OffloaderPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/core/context_engine/processor/offloader/__init__.py");

        assertThat(OffloaderPackage.all()).containsExactly(
                "MessageOffloader",
                "MessageOffloaderConfig",
                "MessageSummaryOffloader",
                "MessageSummaryOffloaderConfig",
                "ToolResultBudgetProcessor",
                "ToolResultBudgetProcessorConfig"
        );
    }

    @Test
    void keyExportsMapToTranslatedJavaTypes() {
        assertThat(OffloaderPackage.sourceFor("MessageOffloader"))
                .isEqualTo("openjiuwen.core.context_engine.processor.offloader.message_offloader.MessageOffloader");
        assertThat(OffloaderPackage.javaTypeNameFor("MessageOffloader"))
                .isEqualTo("com.openjiuwen.core.context_engine.processor.offloader.MessageOffloader");
        assertThat(OffloaderPackage.sourceFor("ToolResultBudgetProcessorConfig"))
                .isEqualTo(
                        "openjiuwen.core.context_engine.processor.offloader.tool_result_budget_processor."
                                + "ToolResultBudgetProcessorConfig"
                );
        assertThat(OffloaderPackage.javaTypeNameFor("ToolResultBudgetProcessor"))
                .isEqualTo("com.openjiuwen.core.context_engine.processor.offloader.ToolResultBudgetProcessor");
    }

    @Test
    void unknownSymbolIsNotExposed() {
        assertThat(OffloaderPackage.exports("MessageCompression")).isFalse();
        assertThat(OffloaderPackage.sourceFor("missing")).isNull();
        assertThat(OffloaderPackage.javaTypeNameFor("missing")).isNull();
    }

    @Test
    void exportedSymbolsAreImmutable() {
        assertThatThrownBy(() -> OffloaderPackage.all().add("unexpected"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
