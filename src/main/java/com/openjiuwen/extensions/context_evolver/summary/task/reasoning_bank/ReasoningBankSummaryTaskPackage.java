/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reasoning_bank;

import java.util.List;

/**
 * Package bridge for ReasoningBank summary task exports.
 * <p>
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.summary.task.reasoning_bank} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reasoning_bank/__init__.py}.
 * </p>
 */
public final class ReasoningBankSummaryTaskPackage {

    public static final String PYTHON_MODULE =
            "openjiuwen/extensions/context_evolver/summary/task/reasoning_bank/__init__.py";
    public static final Class<SummarizeMemoryOp> SUMMARIZE_MEMORY_OP = SummarizeMemoryOp.class;
    public static final Class<SummarizeMemoryParallelOp> SUMMARIZE_MEMORY_PARALLEL_OP =
            SummarizeMemoryParallelOp.class;
    public static final Class<UpdateVectorStoreOp> UPDATE_VECTOR_STORE_OP = UpdateVectorStoreOp.class;
    public static final Class<PersistMemoryOp> PERSIST_MEMORY_OP = PersistMemoryOp.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "SummarizeMemoryOp",
            "SummarizeMemoryParallelOp",
            "UpdateVectorStoreOp",
            "PersistMemoryOp"
    );

    private ReasoningBankSummaryTaskPackage() {
    }
}
