/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reasoning_bank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.summary.task.reasoning_bank} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reasoning_bank/__init__.py}.
 */
class ReasoningBankSummaryTaskPackageTest {

    @Test
    void exposesPythonModulePath() {
        assertEquals(
                "openjiuwen/extensions/context_evolver/summary/task/reasoning_bank/__init__.py",
                ReasoningBankSummaryTaskPackage.PYTHON_MODULE
        );
    }

    @Test
    void exposesUpdateOperationExportsInPythonOrder() {
        assertSame(SummarizeMemoryOp.class, ReasoningBankSummaryTaskPackage.SUMMARIZE_MEMORY_OP);
        assertSame(SummarizeMemoryParallelOp.class,
                ReasoningBankSummaryTaskPackage.SUMMARIZE_MEMORY_PARALLEL_OP);
        assertSame(UpdateVectorStoreOp.class, ReasoningBankSummaryTaskPackage.UPDATE_VECTOR_STORE_OP);
        assertSame(PersistMemoryOp.class, ReasoningBankSummaryTaskPackage.PERSIST_MEMORY_OP);
        assertEquals(
                List.of("SummarizeMemoryOp", "SummarizeMemoryParallelOp", "UpdateVectorStoreOp", "PersistMemoryOp"),
                ReasoningBankSummaryTaskPackage.EXPORTED_SYMBOLS
        );
    }
}
