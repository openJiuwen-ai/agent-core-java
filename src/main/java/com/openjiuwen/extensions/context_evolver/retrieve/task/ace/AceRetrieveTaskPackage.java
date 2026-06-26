/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.ace;

import java.util.List;

/**
 * Package bridge for ACE retrieve task exports.
 * <p>
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.retrieve.task.ace} in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/ace/__init__.py}.
 * </p>
 */
public final class AceRetrieveTaskPackage {

    public static final String PYTHON_MODULE =
            "openjiuwen/extensions/context_evolver/retrieve/task/ace/__init__.py";
    public static final Class<RecallMemoryOp> RECALL_MEMORY_OP = RecallMemoryOp.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of("RecallMemoryOp");

    private AceRetrieveTaskPackage() {
    }
}
