/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.ace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.summary.task.ace} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/ace/__init__.py}.
 */
class AceSummaryTaskPackageTest {

    @Test
    void exposesPythonModulePath() {
        assertEquals(
                "openjiuwen/extensions/context_evolver/summary/task/ace/__init__.py",
                AceSummaryTaskPackage.PYTHON_MODULE
        );
    }

    @Test
    void exposesUpdateOperationExportsInPythonOrder() {
        assertSame(LoadPlaybookOp.class, AceSummaryTaskPackage.LOAD_PLAYBOOK_OP);
        assertSame(ReflectOp.class, AceSummaryTaskPackage.REFLECT_OP);
        assertSame(ParallelReflectOp.class, AceSummaryTaskPackage.PARALLEL_REFLECT_OP);
        assertSame(CurateOp.class, AceSummaryTaskPackage.CURATE_OP);
        assertSame(ParallelCurateOp.class, AceSummaryTaskPackage.PARALLEL_CURATE_OP);
        assertSame(ApplyDeltaOp.class, AceSummaryTaskPackage.APPLY_DELTA_OP);
        assertSame(PersistMemoryOp.class, AceSummaryTaskPackage.PERSIST_MEMORY_OP);
        assertEquals(
                List.of(
                        "LoadPlaybookOp",
                        "ReflectOp",
                        "ParallelReflectOp",
                        "CurateOp",
                        "ParallelCurateOp",
                        "ApplyDeltaOp",
                        "PersistMemoryOp"
                ),
                AceSummaryTaskPackage.EXPORTED_SYMBOLS
        );
    }
}
