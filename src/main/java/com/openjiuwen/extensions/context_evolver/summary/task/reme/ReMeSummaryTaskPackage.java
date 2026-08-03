/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reme;

import java.util.List;

/**
 * Package bridge for ReMe summary task exports.
 * <p>
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.summary.task.reme} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reme/__init__.py}.
 * </p>
 */
public final class ReMeSummaryTaskPackage {

    public static final String PYTHON_MODULE =
            "openjiuwen/extensions/context_evolver/summary/task/reme/__init__.py";
    public static final Class<TrajectoryPreprocessOp> TRAJECTORY_PREPROCESS_OP = TrajectoryPreprocessOp.class;
    public static final Class<SuccessExtractionOp> SUCCESS_EXTRACTION_OP = SuccessExtractionOp.class;
    public static final Class<FailureExtractionOp> FAILURE_EXTRACTION_OP = FailureExtractionOp.class;
    public static final Class<ComparativeExtractionOp> COMPARATIVE_EXTRACTION_OP = ComparativeExtractionOp.class;
    public static final Class<ComparativeAllExtractionOp> COMPARATIVE_ALL_EXTRACTION_OP =
            ComparativeAllExtractionOp.class;
    public static final Class<MemoryValidationOp> MEMORY_VALIDATION_OP = MemoryValidationOp.class;
    public static final Class<MemoryDeduplicationOp> MEMORY_DEDUPLICATION_OP = MemoryDeduplicationOp.class;
    public static final Class<UpdateVectorStoreOp> UPDATE_VECTOR_STORE_OP = UpdateVectorStoreOp.class;
    public static final Class<PersistMemoryOp> PERSIST_MEMORY_OP = PersistMemoryOp.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "TrajectoryPreprocessOp",
            "SuccessExtractionOp",
            "FailureExtractionOp",
            "ComparativeExtractionOp",
            "ComparativeAllExtractionOp",
            "MemoryValidationOp",
            "MemoryDeduplicationOp",
            "UpdateVectorStoreOp",
            "PersistMemoryOp"
    );

    private ReMeSummaryTaskPackage() {
    }
}
