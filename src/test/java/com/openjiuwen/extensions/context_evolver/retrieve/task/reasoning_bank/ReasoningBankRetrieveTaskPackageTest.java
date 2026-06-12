/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reasoning_bank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.retrieve.task.reasoning_bank} in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/reasoning_bank/__init__.py}.
 */
class ReasoningBankRetrieveTaskPackageTest {

    @Test
    void exposesPythonModulePath() {
        assertEquals(
                "openjiuwen/extensions/context_evolver/retrieve/task/reasoning_bank/__init__.py",
                ReasoningBankRetrieveTaskPackage.PYTHON_MODULE
        );
    }

    @Test
    void exposesRecallMemoryOpExport() {
        assertSame(RecallMemoryOp.class, ReasoningBankRetrieveTaskPackage.RECALL_MEMORY_OP);
        assertEquals(List.of("RecallMemoryOp"), ReasoningBankRetrieveTaskPackage.EXPORTED_SYMBOLS);
    }
}
