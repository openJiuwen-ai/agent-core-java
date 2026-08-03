/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.summary.task.reme} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reme/__init__.py}.
 */
class ReMeSummaryTaskPackageTest {

    @Test
    void exposesPythonModulePath() {
        assertEquals(
                "openjiuwen/extensions/context_evolver/summary/task/reme/__init__.py",
                ReMeSummaryTaskPackage.PYTHON_MODULE
        );
    }

    @Test
    void exposesUpdateOperationExportsInPythonOrder() {
        assertSame(TrajectoryPreprocessOp.class, ReMeSummaryTaskPackage.TRAJECTORY_PREPROCESS_OP);
        assertSame(SuccessExtractionOp.class, ReMeSummaryTaskPackage.SUCCESS_EXTRACTION_OP);
        assertSame(FailureExtractionOp.class, ReMeSummaryTaskPackage.FAILURE_EXTRACTION_OP);
        assertSame(ComparativeExtractionOp.class, ReMeSummaryTaskPackage.COMPARATIVE_EXTRACTION_OP);
        assertSame(ComparativeAllExtractionOp.class, ReMeSummaryTaskPackage.COMPARATIVE_ALL_EXTRACTION_OP);
        assertSame(MemoryValidationOp.class, ReMeSummaryTaskPackage.MEMORY_VALIDATION_OP);
        assertSame(MemoryDeduplicationOp.class, ReMeSummaryTaskPackage.MEMORY_DEDUPLICATION_OP);
        assertSame(UpdateVectorStoreOp.class, ReMeSummaryTaskPackage.UPDATE_VECTOR_STORE_OP);
        assertSame(PersistMemoryOp.class, ReMeSummaryTaskPackage.PERSIST_MEMORY_OP);
        assertEquals(
                List.of(
                        "TrajectoryPreprocessOp",
                        "SuccessExtractionOp",
                        "FailureExtractionOp",
                        "ComparativeExtractionOp",
                        "ComparativeAllExtractionOp",
                        "MemoryValidationOp",
                        "MemoryDeduplicationOp",
                        "UpdateVectorStoreOp",
                        "PersistMemoryOp"
                ),
                ReMeSummaryTaskPackage.EXPORTED_SYMBOLS
        );
    }
}
