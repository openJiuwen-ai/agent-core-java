/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.openjiuwen.core.context.processor.compressor.DialogueCompressorConfig;

import org.junit.jupiter.api.Test;

/**
 * Verifies a mismatched config type is rejected at construction with an explicit error.
 */
class ToolResultBudgetProcessorConfigMismatchTest {

    @Test
    void constructorRejectsForeignConfigTypeWithExplicitError() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ToolResultBudgetProcessor(new DialogueCompressorConfig()))
                .withMessage("ToolResultBudgetProcessor requires ToolResultBudgetProcessorConfig");
    }
}
