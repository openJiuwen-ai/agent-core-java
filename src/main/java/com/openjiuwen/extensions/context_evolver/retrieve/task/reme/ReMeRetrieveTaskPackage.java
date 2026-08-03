/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reme;

import java.util.List;

/**
 * Package bridge for ReMe retrieve task exports.
 * <p>
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.retrieve.task.reme} in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/reme/__init__.py}.
 * </p>
 */
public final class ReMeRetrieveTaskPackage {

    public static final String PYTHON_MODULE =
            "openjiuwen/extensions/context_evolver/retrieve/task/reme/__init__.py";
    public static final Class<RecallMemoryOp> RECALL_MEMORY_OP = RecallMemoryOp.class;
    public static final Class<RerankMemoryOp> RERANK_MEMORY_OP = RerankMemoryOp.class;
    public static final Class<RewriteMemoryOp> REWRITE_MEMORY_OP = RewriteMemoryOp.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "RecallMemoryOp",
            "RerankMemoryOp",
            "RewriteMemoryOp"
    );

    private ReMeRetrieveTaskPackage() {
    }
}
