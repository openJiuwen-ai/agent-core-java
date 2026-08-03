/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.retrieve.task.reme} in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/reme/__init__.py}.
 */
class ReMeRetrieveTaskPackageTest {

    @Test
    void exposesPythonModulePath() {
        assertEquals(
                "openjiuwen/extensions/context_evolver/retrieve/task/reme/__init__.py",
                ReMeRetrieveTaskPackage.PYTHON_MODULE
        );
    }

    @Test
    void exposesRunOperationExportsInPythonOrder() {
        assertSame(RecallMemoryOp.class, ReMeRetrieveTaskPackage.RECALL_MEMORY_OP);
        assertSame(RerankMemoryOp.class, ReMeRetrieveTaskPackage.RERANK_MEMORY_OP);
        assertSame(RewriteMemoryOp.class, ReMeRetrieveTaskPackage.REWRITE_MEMORY_OP);
        assertEquals(
                List.of("RecallMemoryOp", "RerankMemoryOp", "RewriteMemoryOp"),
                ReMeRetrieveTaskPackage.EXPORTED_SYMBOLS
        );
    }
}
