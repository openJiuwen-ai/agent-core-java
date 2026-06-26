/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.ace;

import java.util.List;

/**
 * Package bridge for ACE summary task exports.
 * <p>
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.summary.task.ace} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/ace/__init__.py}.
 * </p>
 */
public final class AceSummaryTaskPackage {

    public static final String PYTHON_MODULE =
            "openjiuwen/extensions/context_evolver/summary/task/ace/__init__.py";
    public static final Class<LoadPlaybookOp> LOAD_PLAYBOOK_OP = LoadPlaybookOp.class;
    public static final Class<ReflectOp> REFLECT_OP = ReflectOp.class;
    public static final Class<ParallelReflectOp> PARALLEL_REFLECT_OP = ParallelReflectOp.class;
    public static final Class<CurateOp> CURATE_OP = CurateOp.class;
    public static final Class<ParallelCurateOp> PARALLEL_CURATE_OP = ParallelCurateOp.class;
    public static final Class<ApplyDeltaOp> APPLY_DELTA_OP = ApplyDeltaOp.class;
    public static final Class<PersistMemoryOp> PERSIST_MEMORY_OP = PersistMemoryOp.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "LoadPlaybookOp",
            "ReflectOp",
            "ParallelReflectOp",
            "CurateOp",
            "ParallelCurateOp",
            "ApplyDeltaOp",
            "PersistMemoryOp"
    );

    private AceSummaryTaskPackage() {
    }
}
